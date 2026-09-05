package com.example.data.sync

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.model.Constants
import com.example.data.model.MasteredQuestion
import com.example.data.model.Question
import com.example.data.model.SyncProgressEntity
import com.example.data.model.SyncQuestionAssetEntity
import com.example.data.model.SyncQuestionBundle
import com.example.data.model.SyncQuestionEntity
import com.example.data.model.SyncQuestionOptionEntity
import com.example.data.model.SyncStateEntity
import com.example.data.model.SyncTopicEntity
import com.example.data.model.WrongQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class SyncResult(
    val questionsPulled: Int,
    val playableQuestions: Int,
    val assetsPulled: Int,
    val optionsPulled: Int,
    val progressUploaded: Int,
    val progressPulled: Int,
    val lastSyncAt: String
) {
    fun toSummary(): String {
        return "题库 $questionsPulled 题（可练 $playableQuestions 题），配图 $assetsPulled，选项 $optionsPulled；上传进度 $progressUploaded，拉取进度 $progressPulled"
    }
}

class SupabaseSyncRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val client = SupabaseRestClient(context)

    val isConfigured: Boolean
        get() = client.isConfigured

    suspend fun getLastSyncAt(): String {
        return withContext(Dispatchers.IO) {
            db.syncStateDao().get(LAST_SYNC_AT)?.value.orEmpty()
        }
    }

    suspend fun getMatchingBundle(localQuestionId: String): SyncQuestionBundle? = withContext(Dispatchers.IO) {
        val remoteId = localQuestionId.removePrefix(SYNC_PREFIX)
        val question = db.syncQuestionDao().getMatchingQuestions().firstOrNull { it.id == remoteId } ?: return@withContext null
        SyncQuestionBundle(
            question = question,
            assets = db.syncQuestionAssetDao().getByQuestionId(remoteId),
            options = db.syncQuestionOptionDao().getByQuestionId(remoteId)
        )
    }

    suspend fun recordLocalAttempt(question: Question, userAnswerJson: String, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val now = nowIso()
        val existing = db.syncProgressDao().getAll().firstOrNull { it.questionId == question.id }
        db.syncProgressDao().upsert(
            SyncProgressEntity(
                questionId = question.id,
                status = if (isCorrect) "mastered" else "learning",
                userAnswerJson = userAnswerJson,
                isCorrect = isCorrect,
                attemptCount = (existing?.attemptCount ?: 0) + 1,
                lastAttemptAt = now,
                nextReviewAt = if (isCorrect) "" else now,
                updatedAt = now,
                pendingUpload = true
            )
        )
    }

    suspend fun fullSync(mastered: List<MasteredQuestion>, wrong: List<WrongQuestion>): SyncResult = withContext(Dispatchers.IO) {
        if (!isConfigured) throw SupabaseSyncException("Supabase 尚未配置")
        val session = client.ensureSession()
        val now = nowIso()

        val topics = client.fetchTable("topics", incrementalParams("last_topics_pull_at"))
        val questions = client.fetchTable("questions", incrementalParams("last_questions_pull_at") + ("review_status" to "eq.approved"))
        val assets = client.fetchTable("question_assets")
        val options = client.fetchTable("question_options")

        db.syncTopicDao().upsertAll(parseTopics(topics, now))
        val parsedQuestions = parseQuestions(questions, now)
        db.syncQuestionDao().upsertAll(parsedQuestions)
        db.syncQuestionAssetDao().upsertAll(parseAssets(assets, now))
        db.syncQuestionOptionDao().upsertAll(parseOptions(options, now))
        db.syncStateDao().upsert(SyncStateEntity("last_topics_pull_at", now))
        db.syncStateDao().upsert(SyncStateEntity("last_questions_pull_at", now))

        val allCachedQuestions = db.syncQuestionDao().getAll()
        val topicById = db.syncTopicDao().getAll().associateBy { it.id }
        val assetsByQuestionId = db.syncQuestionAssetDao().getAll().groupBy { it.questionId }
        val playableQuestions = allCachedQuestions.mapNotNull { question ->
            val assets = assetsByQuestionId[question.id].orEmpty()
            question.toPlayableQuestion(topicById, assets)
        }
        db.questionDao().insertQuestions(playableQuestions)

        queueLegacyProgress(mastered, wrong, session.userId, now)
        val pending = db.syncProgressDao().getPendingUploads()
        client.upsertProgress(progressToJson(pending, session.userId))
        if (pending.isNotEmpty()) db.syncProgressDao().markUploaded(pending.map { it.questionId }, now)

        val remoteProgress = client.fetchTable("progress", mapOf("user_id" to "eq.${session.userId}"))
        mergeRemoteProgress(remoteProgress, now)
        applyRemoteProgressToLegacyTables(remoteProgress)

        db.syncStateDao().upsert(SyncStateEntity(LAST_SYNC_AT, now))
        SyncResult(
            questionsPulled = questions.length(),
            playableQuestions = playableQuestions.size,
            assetsPulled = assets.length(),
            optionsPulled = options.length(),
            progressUploaded = pending.size,
            progressPulled = remoteProgress.length(),
            lastSyncAt = now
        )
    }

    private suspend fun queueLegacyProgress(
        mastered: List<MasteredQuestion>,
        wrong: List<WrongQuestion>,
        userId: String,
        now: String
    ) {
        val rows = mutableListOf<SyncProgressEntity>()
        mastered.forEach {
            rows.add(
                SyncProgressEntity(
                    questionId = it.questionId,
                    userId = userId,
                    status = "mastered",
                    userAnswerJson = "{}",
                    isCorrect = true,
                    attemptCount = 1,
                    lastAttemptAt = now,
                    updatedAt = now,
                    pendingUpload = true
                )
            )
        }
        wrong.forEach {
            rows.add(
                SyncProgressEntity(
                    questionId = it.questionId,
                    userId = userId,
                    status = "learning",
                    userAnswerJson = "{}",
                    isCorrect = false,
                    attemptCount = it.failCount,
                    lastAttemptAt = now,
                    nextReviewAt = dateToIso(it.nextReviewDate),
                    updatedAt = now,
                    pendingUpload = true
                )
            )
        }
        db.syncProgressDao().upsertAll(rows)
    }

    private suspend fun mergeRemoteProgress(rows: JSONArray, syncedAt: String) {
        val progressRows = buildList {
            for (index in 0 until rows.length()) {
                val obj = rows.getJSONObject(index)
                add(
                    SyncProgressEntity(
                        questionId = obj.optString("question_id"),
                        remoteId = obj.optString("id"),
                        userId = obj.optString("user_id"),
                        status = obj.optString("status", "new"),
                        userAnswerJson = obj.optJSONObject("user_answer")?.toString() ?: "{}",
                        isCorrect = if (obj.isNull("is_correct")) null else obj.optBoolean("is_correct"),
                        attemptCount = obj.optInt("attempt_count"),
                        lastAttemptAt = obj.optString("last_attempt_at"),
                        nextReviewAt = obj.optString("next_review_at"),
                        updatedAt = obj.optString("updated_at"),
                        syncedAt = syncedAt,
                        pendingUpload = false
                    )
                )
            }
        }.filter { it.questionId.isNotBlank() }

        val localById = db.syncProgressDao().getAll().associateBy { it.questionId }
        val merged = progressRows.map { remote ->
            val local = localById[remote.questionId]
            if (local != null && local.pendingUpload && local.updatedAt > remote.updatedAt) local else remote
        }
        db.syncProgressDao().upsertAll(merged)
    }

    private suspend fun applyRemoteProgressToLegacyTables(rows: JSONArray) {
        for (index in 0 until rows.length()) {
            val obj = rows.getJSONObject(index)
            val questionId = obj.optString("question_id")
            when (obj.optString("status")) {
                "mastered" -> {
                    db.masteredQuestionDao().insert(MasteredQuestion(questionId))
                    db.wrongQuestionDao().deleteWrongQuestion(questionId)
                }
                "learning" -> {
                    db.masteredQuestionDao().delete(questionId)
                    db.wrongQuestionDao().insertOrUpdate(
                        WrongQuestion(
                            questionId = questionId,
                            monsterId = "careless",
                            failCount = obj.optInt("attempt_count", 1).coerceAtLeast(1),
                            nextReviewDate = obj.optString("next_review_at").take(10).ifBlank { todayPlusThree() }
                        )
                    )
                }
            }
        }
    }

    private fun progressToJson(rows: List<SyncProgressEntity>, userId: String): JSONArray {
        val array = JSONArray()
        rows.forEach { row ->
            array.put(
                JSONObject()
                    .put("user_id", userId)
                    .put("question_id", row.questionId)
                    .put("status", row.status)
                    .put("user_answer", JSONObject(row.userAnswerJson.ifBlank { "{}" }))
                    .put("is_correct", row.isCorrect)
                    .put("attempt_count", row.attemptCount)
                    .put("last_attempt_at", row.lastAttemptAt.ifBlank { JSONObject.NULL })
                    .put("next_review_at", row.nextReviewAt.ifBlank { JSONObject.NULL })
                    .put("updated_at", row.updatedAt)
            )
        }
        return array
    }

    private suspend fun incrementalParams(key: String): Map<String, String> {
        val last = db.syncStateDao().get(key)?.value.orEmpty()
        return if (last.isBlank()) emptyMap() else mapOf("updated_at" to "gt.$last")
    }

    private fun parseTopics(array: JSONArray, syncedAt: String): List<SyncTopicEntity> = buildList {
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            add(
                SyncTopicEntity(
                    id = obj.optString("id"),
                    grade = obj.optInt("grade"),
                    strand = obj.optString("strand"),
                    name = obj.optString("name"),
                    sortOrder = obj.optInt("sort_order"),
                    createdAt = obj.optString("created_at"),
                    updatedAt = obj.optString("updated_at"),
                    syncedAt = syncedAt
                )
            )
        }
    }

    private fun parseQuestions(array: JSONArray, syncedAt: String): List<SyncQuestionEntity> = buildList {
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            add(
                SyncQuestionEntity(
                    id = obj.optString("id"),
                    topicId = obj.optString("topic_id"),
                    grade = obj.optInt("grade"),
                    difficulty = obj.optInt("difficulty"),
                    qtype = obj.optString("qtype"),
                    stemText = obj.optString("stem_text"),
                    layoutJson = obj.optJSONObject("layout")?.toString() ?: "{}",
                    correctAnswerJson = obj.optJSONObject("correct_answer")?.toString() ?: "{}",
                    explanation = obj.optString("explanation"),
                    story = obj.optString("story"),
                    methodHint = obj.optString("method_hint"),
                    hiddenTrapsJson = obj.optJSONArray("hidden_traps")?.toString() ?: "[]",
                    referenceJson = obj.optJSONObject("reference")?.toString() ?: "{}",
                    source = obj.optString("source"),
                    aiGenerated = obj.optBoolean("ai_generated"),
                    reviewStatus = obj.optString("review_status"),
                    createdAt = obj.optString("created_at"),
                    updatedAt = obj.optString("updated_at"),
                    syncedAt = syncedAt
                )
            )
        }
    }

    private fun parseAssets(array: JSONArray, syncedAt: String): List<SyncQuestionAssetEntity> = buildList {
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            add(
                SyncQuestionAssetEntity(
                    id = obj.optString("id"),
                    questionId = obj.optString("question_id"),
                    role = obj.optString("role"),
                    slotKey = obj.optString("slot_key"),
                    label = obj.optString("label"),
                    assetType = obj.optString("asset_type"),
                    svgContent = obj.optString("svg_content"),
                    storagePath = obj.optString("storage_path"),
                    width = obj.optIntOrNull("width"),
                    height = obj.optIntOrNull("height"),
                    sortOrder = obj.optInt("sort_order"),
                    createdAt = obj.optString("created_at"),
                    syncedAt = syncedAt
                )
            )
        }
    }

    private fun parseOptions(array: JSONArray, syncedAt: String): List<SyncQuestionOptionEntity> = buildList {
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            add(
                SyncQuestionOptionEntity(
                    id = obj.optString("id"),
                    questionId = obj.optString("question_id"),
                    optionKey = obj.optString("option_key"),
                    optionLabel = obj.optString("option_label"),
                    optionAssetId = obj.optString("option_asset_id"),
                    sortOrder = obj.optInt("sort_order"),
                    syncedAt = syncedAt
                )
            )
        }
    }

    private fun SyncQuestionEntity.toPlayableQuestion(
        topicById: Map<String, SyncTopicEntity>,
        assets: List<SyncQuestionAssetEntity>
    ): Question? {
        val answerObj = JSONObject(correctAnswerJson.ifBlank { "{}" })
        val answer = when (qtype) {
            "single_choice", "fill_blank", "judge" -> answerObj.optString("value")
            "matching" -> if (answerObj.has("text_answer")) {
                // E.2: text_answer 型 matching 把标准答案提取成纯字符串，
                // 让 judgeAnswer 能直接对比孩子的自由回答，避免喂一整 JSON
                answerObj.optString("text_answer")
            } else {
                correctAnswerJson
            }
            else -> ""
        }
        if (answer.isBlank()) return null
        val reference = JSONObject(referenceJson.ifBlank { "{}" })
        // 从 question_assets 里找主图：stem_main 优先级最高，没有的话用任意 asset_type=raster 的图
        val mainImage = assets
            .filter { it.role == "stem_main" || it.assetType == "raster" }
            .minByOrNull {
                when (it.role) {
                    "stem_main" -> 0
                    "stem_sub" -> 1
                    "option" -> 2
                    "explanation" -> 3
                    else -> 4
                }
            }
        val imageUrl = when (mainImage?.assetType) {
            "svg" -> mainImage.svgContent
            "raster" -> client.storagePublicUrl(mainImage.storagePath)
            else -> fallbackLocalImage(stemText, topicId, qtype)
        }
        return Question(
            id = "$SYNC_PREFIX$id",
            topicId = mapRemoteTopicToLocal(topicById[topicId]),
            story = story.ifBlank { "云端题库送来了一道新挑战！" },
            text = stemText,
            answer = answer,
            qtype = qtype,
            layoutJson = layoutJson,
            correctAnswerJson = correctAnswerJson,
            methodHint = methodHint,
            hiddenTrapsJson = hiddenTrapsJson,
            conditionsRef = reference.optString("conditions"),
            questionRef = reference.optString("question"),
            image = imageUrl,
            isCustom = false
        )
    }

    /**
     * 本地兜底：当云端 question_assets 没有配图时，根据题干关键词从 APK assets 里
     * 找一张相关的观察物体/几何/分数图。这是临时方案，正确做法仍是 PC 端把图片
     * 上传到 Supabase storage 并插入 question_assets 记录。
     */
    private fun fallbackLocalImage(stemText: String, topicId: String, qtype: String): String {
        val text = stemText.lowercase()
        val file = when {
            topicId == "spatial_observe" || "图形山谷" in stemText || "小兔" in stemText -> "u1_observe_q1.png"
            "观察" in stemText || "看到" in stemText || "谁看" in stemText || qtype == "matching" -> "u1_observe_q1.png"
            "剪开" in stemText || "展开图" in stemText || "边" in stemText -> "u1_unfold_q1.png"
            "正方体" in stemText || "对面" in stemText || "木块" in stemText || "1的对面" in stemText -> "u1_cube_flip.png"
            "角" in stemText || "角度" in stemText -> "u5_angle_q1.png"
            "分数" in stemText -> "u6_fraction_q1.png"
            else -> ""
        }
        return if (file.isNotBlank()) "file:///android_asset/star_grade3/$file" else ""
    }

    private fun mapRemoteTopicToLocal(topic: SyncTopicEntity?): String {
        val title = listOfNotNull(topic?.name, topic?.strand).joinToString(" ")
        return Constants.KNOWLEDGE_MAP.firstOrNull { local ->
            title.contains(local.title.substringAfter('·'), ignoreCase = true) ||
                    title.contains(local.coreConcept.take(4), ignoreCase = true)
        }?.id ?: Constants.KNOWLEDGE_MAP.first().id
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        return if (isNull(name)) null else optInt(name)
    }

    private fun dateToIso(date: String): String {
        return if (date.isBlank()) "" else if ("T" in date) date else "${date}T00:00:00Z"
    }

    private fun nowIso(): String = Instant.now().toString()

    private fun todayPlusThree(): String {
        return java.time.LocalDate.now().plusDays(3).toString()
    }

    private companion object {
        const val SYNC_PREFIX = "sync_"
        const val LAST_SYNC_AT = "last_sync_at"
    }
}
