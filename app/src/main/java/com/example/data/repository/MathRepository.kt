package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.model.*
import com.example.data.sync.SupabaseSyncRepository
import com.example.data.sync.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.io.File

class MathRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(context)
    private val questionDao = db.questionDao()
    private val wrongQuestionDao = db.wrongQuestionDao()
    private val masteredQuestionDao = db.masteredQuestionDao()
    private val diaryDao = db.diaryDao()
    private val monsterStatsDao = db.monsterStatsDao()
    private val userSettingsDao = db.userSettingsDao()
    private val syncRepository = SupabaseSyncRepository(context)

    val allQuestions: Flow<List<Question>> = questionDao.getAllQuestions()
    val wrongQuestions: Flow<List<WrongQuestion>> = wrongQuestionDao.getAllWrongQuestions()
    val masteredQuestions: Flow<List<MasteredQuestion>> = masteredQuestionDao.getAllMasteredQuestions()
    val diaryEntries: Flow<List<DiaryEntry>> = diaryDao.getAllDiaryEntries()
    val monsterStats: Flow<List<MonsterStats>> = monsterStatsDao.getAllMonsterStats()
    val userSettings: Flow<UserSettings?> = userSettingsDao.getUserSettingsFlow()

    suspend fun initializeDefaultDataIfNeeded() = withContext(Dispatchers.IO) {
        // v15 升级迁移：检测到旧种子题（id 以 q0 开头）就清掉，换成 V15Data 全量题库
        val currentQuestions = questionDao.getAllQuestions().firstOrNull() ?: emptyList()
        val hasV15Seed = currentQuestions.any { it.id.startsWith("v15q") }
        if (hasV15Seed) {
            // 已有 v15 题库，只补漏
            val v15Ids = Constants.DEFAULT_QUESTION_BANK.map { it.id }.toSet()
            val missing = Constants.DEFAULT_QUESTION_BANK.filter { it.id !in currentQuestions.map { q -> q.id } }
            if (missing.isNotEmpty()) questionDao.insertQuestions(missing)
        } else {
            // 删除旧种子（非自定义、id 短），写入 v15 全量题库
            val legacyIds = currentQuestions.filter { !it.isCustom && it.id.length <= 5 }
            legacyIds.forEach { questionDao.deleteQuestionById(it.id) }
            questionDao.insertQuestions(Constants.DEFAULT_QUESTION_BANK)
        }

        // Seed Monster Stats
        val currentStats = monsterStatsDao.getAllMonsterStats().firstOrNull() ?: emptyList()
        if (currentStats.isEmpty()) {
            val defaultStats = Constants.MONSTERS.keys.map { monsterId ->
                MonsterStats(monsterId = monsterId, seenCount = 0, defeatedCount = 0)
            }
            defaultStats.forEach { monsterStatsDao.insertOrUpdate(it) }
        }

        // Seed / Update Settings & Streak
        val settings = userSettingsDao.getUserSettings() ?: UserSettings()
        val today = getTodayStr()
        val updatedSettings = updateLoginStreak(settings, today)
        userSettingsDao.updateSettings(updatedSettings)
    }

    fun isSupabaseConfigured(): Boolean = syncRepository.isConfigured

    suspend fun getLastSyncAt(): String = syncRepository.getLastSyncAt()

    suspend fun syncWithSupabase(): SyncResult = withContext(Dispatchers.IO) {
        val mastered = masteredQuestionDao.getAllMasteredQuestions().firstOrNull() ?: emptyList()
        val wrong = wrongQuestionDao.getAllWrongQuestions().firstOrNull() ?: emptyList()
        syncRepository.fullSync(mastered, wrong)
    }

    suspend fun getMatchingBundle(questionId: String) = syncRepository.getMatchingBundle(questionId)

    suspend fun recordSyncedAttempt(question: Question, userAnswerJson: String, isCorrect: Boolean) {
        if (question.id.startsWith("sync_")) {
            syncRepository.recordLocalAttempt(question, userAnswerJson, isCorrect)
        }
    }

    private fun getTodayStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun addDays(dateStr: String, days: Int): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: Date()
            val cal = Calendar.getInstance()
            cal.time = date
            cal.add(Calendar.DAY_OF_YEAR, days)
            sdf.format(cal.time)
        } catch (e: Exception) {
            getTodayStr()
        }
    }

    private fun updateLoginStreak(settings: UserSettings, today: String): UserSettings {
        val lastLogin = settings.lastLoginDate
        val newStreak = when {
            lastLogin == today -> settings.streakDays
            lastLogin.isBlank() -> 1
            addDays(lastLogin, 1) == today -> settings.streakDays + 1
            else -> 1
        }
        val resetDailyTasks = if (settings.dailyTasksDate != today) {
            settings.copy(
                dailyTasksDate = today,
                dailyCorrectCount = 0,
                dailyDefeatedCount = 0,
                dailyMethodNotesCount = 0,
                dailyRewardClaimed = false
            )
        } else {
            settings
        }
        return resetDailyTasks.copy(
            lastLoginDate = today,
            streakDays = newStreak
        )
    }

    suspend fun getUserSettingsSync(): UserSettings {
        return withContext(Dispatchers.IO) {
            userSettingsDao.getUserSettings() ?: UserSettings()
        }
    }

    suspend fun updateSettings(update: (UserSettings) -> UserSettings) = withContext(Dispatchers.IO) {
        val current = userSettingsDao.getUserSettings() ?: UserSettings()
        val newSettings = update(current)
        userSettingsDao.updateSettings(newSettings)
    }

    suspend fun addExp(amount: Int): Boolean = withContext(Dispatchers.IO) {
        val current = userSettingsDao.getUserSettings() ?: UserSettings()
        val newExp = current.exp + amount
        val newLevel = (newExp / 50) + 1
        val leveledUp = newLevel > current.level
        userSettingsDao.updateSettings(
            current.copy(exp = newExp, level = newLevel)
        )
        leveledUp
    }

    suspend fun recordMonsterSeen(monsterId: String) = withContext(Dispatchers.IO) {
        val stats = monsterStatsDao.getMonsterStats(monsterId) ?: MonsterStats(monsterId)
        monsterStatsDao.insertOrUpdate(stats.copy(seenCount = stats.seenCount + 1))
    }

    suspend fun recordMonsterDefeated(monsterId: String) = withContext(Dispatchers.IO) {
        val stats = monsterStatsDao.getMonsterStats(monsterId) ?: MonsterStats(monsterId)
        monsterStatsDao.insertOrUpdate(stats.copy(defeatedCount = stats.defeatedCount + 1))
    }

    suspend fun markMastered(questionId: String): String? = withContext(Dispatchers.IO) {
        masteredQuestionDao.insert(MasteredQuestion(questionId))
        val wrong = wrongQuestionDao.getWrongQuestion(questionId)
        val defeatedMonsterId = wrong?.monsterId

        if (wrong != null) {
            wrongQuestionDao.deleteWrongQuestion(questionId)
        }
        if (defeatedMonsterId != null) {
            recordMonsterDefeated(defeatedMonsterId)
        }
        defeatedMonsterId
    }

    suspend fun addWrongQuestion(questionId: String, monsterId: String) = withContext(Dispatchers.IO) {
        val existing = wrongQuestionDao.getWrongQuestion(questionId)
        val nextDate = addDays(getTodayStr(), 3)
        val updated = if (existing != null) {
            existing.copy(
                failCount = existing.failCount + 1,
                monsterId = monsterId,
                nextReviewDate = nextDate
            )
        } else {
            WrongQuestion(
                questionId = questionId,
                monsterId = monsterId,
                failCount = 1,
                nextReviewDate = nextDate
            )
        }
        wrongQuestionDao.insertOrUpdate(updated)
    }

    suspend fun recordDailyCorrect() = withContext(Dispatchers.IO) {
        updateSettings { it.copy(dailyCorrectCount = it.dailyCorrectCount + 1) }
    }

    suspend fun recordDailyMonsterDefeated() = withContext(Dispatchers.IO) {
        updateSettings { it.copy(dailyDefeatedCount = it.dailyDefeatedCount + 1) }
    }

    suspend fun recordDailyMethodNote() = withContext(Dispatchers.IO) {
        updateSettings { it.copy(dailyMethodNotesCount = it.dailyMethodNotesCount + 1) }
    }

    suspend fun claimDailyRewardIfReady(): Boolean = withContext(Dispatchers.IO) {
        val current = userSettingsDao.getUserSettings() ?: UserSettings()
        val allDone = current.dailyCorrectCount >= 3 &&
                current.dailyDefeatedCount >= 1 &&
                current.dailyMethodNotesCount >= 1

        if (allDone && !current.dailyRewardClaimed) {
            updateSettings { it.copy(dailyRewardClaimed = true) }
            addExp(Constants.DAILY_TASK_REWARD_EXP)
            true
        } else {
            false
        }
    }

    suspend fun addDiaryEntry(topicTitle: String, result: String, note: String, score: Int? = null) = withContext(Dispatchers.IO) {
        diaryDao.insertEntry(
            DiaryEntry(
                date = getTodayStr(),
                topicTitle = topicTitle,
                result = result,
                note = note,
                score = score
            )
        )
    }

    suspend fun addCustomQuestion(question: Question) = withContext(Dispatchers.IO) {
        questionDao.insertQuestion(question)
    }

    suspend fun addCustomQuestions(questions: List<Question>) = withContext(Dispatchers.IO) {
        questionDao.insertQuestions(questions)
    }

    suspend fun deleteQuestion(questionId: String) = withContext(Dispatchers.IO) {
        val question = questionDao.getQuestionById(questionId)
        questionDao.deleteQuestionById(questionId)
        wrongQuestionDao.deleteWrongQuestion(questionId)
        question?.image?.takeIf { it.isNotBlank() }?.let { path ->
            val imageFile = File(path)
            if (imageFile.isFile && imageFile.absolutePath.startsWith(appContext.filesDir.absolutePath)) {
                imageFile.delete()
            }
        }
    }

    fun hashPin(rawPin: String): String {
        val salt = "math_adventure_island_parent_lock_v10"
        val bytes = (salt + rawPin.trim()).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun verifyPin(settings: UserSettings, rawPin: String): Boolean {
        if (!settings.parentLockEnabled || settings.parentPinHash.isBlank()) return true
        return hashPin(rawPin) == settings.parentPinHash
    }
}
