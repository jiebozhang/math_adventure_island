package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Constants
import com.example.data.model.Question
import com.example.data.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.net.URL
import kotlin.random.Random

data class UnderstoodResult(
    val understood: Boolean,
    val feedback: String,
    val followUp: String
)

data class JudgeResult(
    val correct: Boolean,
    val reason: String
)

/**
 * AI 批改结果（供七步法状态机消费）
 * @param correct 是否通过
 * @param feedback 通过时的鼓励语
 * @param hint 未通过时的引导性提示（不直接给答案）
 * @param suggestion 未通过时的学习建议
 */
data class StepGradeResult(
    val correct: Boolean,
    val feedback: String = "",
    val hint: String = "",
    val suggestion: String = ""
) {
    /** 直接展示给孩子的合并文案 */
    val hintAndSuggestion: String
        get() = when {
            hint.isBlank() && suggestion.isBlank() -> "再检查一下吧！"
            suggestion.isBlank() -> hint
            hint.isBlank() -> "小建议：$suggestion"
            else -> "$hint\n小建议：$suggestion"
        }
}

object GeminiHelper {
    private const val TAG = "GeminiHelper"

    private fun getApiKey(settings: UserSettings): String {
        return settings.llmApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
    }

    /**
     * 调用 LLM。用 suspendCancellableCoroutine + 后台线程 + AtomicReference 持有 conn，
     * 协程取消（withTimeout 触发）时立即 disconnect，保证超时真正生效。
     *
     * 2026-09-05 修复：conn 必须在三个 callXxxApi 里创建后立即 set 进 connRef，
     * 否则 invokeOnCancellation 里的 disconnect() 是空操作（之前 connRef 永远是 null）。
     */
    private suspend fun callLlmApi(
        settings: UserSettings,
        systemPrompt: String,
        userMessage: String,
        temperature: Double = 0.3
    ): String = suspendCancellableCoroutine { cont ->
        val apiKey = getApiKey(settings)
        if (apiKey.isBlank()) {
            cont.resumeWithException(IllegalStateException("未配置 Gemini API Key，请在家长控制台配置或通过 Secrets 设置。"))
            return@suspendCancellableCoroutine
        }

        val provider = settings.llmProvider
        val apiBase = settings.llmApiBase.trim().removeSuffix("/")
        val model = settings.llmModel.ifBlank { "gemini-3.5-flash" }

        // 用用户配置的超时，不再硬截断到 8s；
        // socket readTimeout 比 withTimeout 多 5s 缓冲 → 协程超时先触发，
        // 走"AI 批改超时了"文案分支（而不是 socket 超时被 catch(Exception) 误报成"AI 暂时不可用"）
        val effectiveTimeoutSec = settings.llmTimeoutSeconds

        val connRef = AtomicReference<HttpURLConnection?>(null)

        val workThread = Thread {
            try {
                val result: String = if (provider == "anthropic") {
                    callAnthropicApi(apiBase, apiKey, model, systemPrompt, userMessage, effectiveTimeoutSec, temperature, connRef)
                } else if (apiBase.contains("generativelanguage.googleapis.com")) {
                    callGeminiNativeApi(apiBase, apiKey, model, systemPrompt, userMessage, effectiveTimeoutSec, temperature, connRef)
                } else {
                    callOpenAiCompatibleApi(apiBase, apiKey, model, systemPrompt, userMessage, effectiveTimeoutSec, temperature, connRef)
                }
                if (cont.isActive) cont.resume(result)
            } catch (e: Throwable) {
                // 2026-09-05：真实失败原因必须打进日志，UI 层也会透出摘要
                Log.e(TAG, "callLlmApi failed (${e.javaClass.simpleName}): ${e.message?.take(200)}")
                if (cont.isActive) cont.resumeWithException(e)
            } finally {
                try { connRef.get()?.disconnect() } catch (_: Exception) {}
            }
        }.apply { isDaemon = true; name = "llm-call" }

        cont.invokeOnCancellation {
            connRef.get()?.disconnect()
            workThread.interrupt()
        }
        workThread.start()
    }

    private fun callGeminiNativeApi(
        apiBase: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String,
        timeoutSec: Int,
        temperature: Double = 0.3,
        connRef: AtomicReference<HttpURLConnection?>? = null
    ): String {
        val urlString = "$apiBase/v1beta/models/$model:generateContent?key=$apiKey"
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = minOf(timeoutSec, 15) * 1000
            readTimeout = (timeoutSec + 5) * 1000
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        connRef?.set(conn)

        val payload = JSONObject().apply {
            if (systemPrompt.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
            }
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
            }))
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }

        val code = conn.responseCode
        if (code != 200) {
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("API HTTP $code: ${errStr.take(150)}")
        }

        val respStr = conn.inputStream.bufferedReader().use { it.readText() }
        val jsonResp = JSONObject(respStr)
        val candidates = jsonResp.optJSONArray("candidates")
        val content = candidates?.optJSONObject(0)?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        return parts?.optJSONObject(0)?.optString("text", "") ?: ""
    }

    private fun callOpenAiCompatibleApi(
        apiBase: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String,
        timeoutSec: Int,
        temperature: Double = 0.3,
        connRef: AtomicReference<HttpURLConnection?>? = null
    ): String {
        val endpoint = if (apiBase.endsWith("/chat/completions")) apiBase else "$apiBase/chat/completions"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = minOf(timeoutSec, 15) * 1000
            readTimeout = (timeoutSec + 5) * 1000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
        }
        connRef?.set(conn)

        val messages = JSONArray().apply {
            if (systemPrompt.isNotBlank()) {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }

        val code = conn.responseCode
        if (code != 200) {
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("API HTTP $code: ${errStr.take(150)}")
        }

        val respStr = conn.inputStream.bufferedReader().use { it.readText() }
        val jsonResp = JSONObject(respStr)
        val choices = jsonResp.optJSONArray("choices")
        val messageObj = choices?.optJSONObject(0)?.optJSONObject("message")
        return messageObj?.optString("content", "") ?: ""
    }

    private fun callAnthropicApi(
        apiBase: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String,
        timeoutSec: Int,
        temperature: Double = 0.3,
        connRef: AtomicReference<HttpURLConnection?>? = null
    ): String {
        val endpoint = if (apiBase.endsWith("/v1/messages")) apiBase else "$apiBase/v1/messages"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = minOf(timeoutSec, 15) * 1000
            readTimeout = (timeoutSec + 5) * 1000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            doOutput = true
        }
        connRef?.set(conn)

        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            if (systemPrompt.isNotBlank()) {
                put("system", systemPrompt)
            }
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            }))
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }

        val code = conn.responseCode
        if (code != 200) {
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("Anthropic API HTTP $code: ${errStr.take(150)}")
        }

        val respStr = conn.inputStream.bufferedReader().use { it.readText() }
        val jsonResp = JSONObject(respStr)
        val contentArr = jsonResp.optJSONArray("content")
        for (i in 0 until (contentArr?.length() ?: 0)) {
            val item = contentArr?.optJSONObject(i)
            if (item?.optString("type") == "text") {
                return item.optString("text", "")
            }
        }
        return ""
    }

    private fun extractJsonObject(text: String): JSONObject {
        var trimmed = text.trim()
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replace(Regex("^```[a-zA-Z]*\\s*"), "").replace(Regex("```\\s*$"), "").trim()
        }
        val start = trimmed.indexOf("{")
        val end = trimmed.lastIndexOf("}")
        if (start != -1 && end != -1 && end > start) {
            val jsonSub = trimmed.substring(start, end + 1)
            return JSONObject(jsonSub)
        }
        return JSONObject(trimmed)
    }

    suspend fun checkUnderstanding(
        settings: UserSettings,
        questionText: String,
        imageDesc: String,
        priorPairs: List<Pair<String?, String>>,
        latestAnswer: String,
        turnCount: Int,
        maxTurns: Int
    ): UnderstoodResult {
        val systemPrompt = """
            你是一位温柔耐心的小学数学老师，正在和一个1-3年级的孩子对话。
            孩子正在用自己的话复述一道数学题的意思。评判标准要宽松，只要抓住"已知了什么、要求什么"的大意就算理解，不要吹毛求疵。
            如果理解了：understood设为true，feedback用一句话简短表扬（不超过20字），follow_up留空字符串。
            如果有明显遗漏：understood设为false，feedback留空字符串，follow_up提出一个具体的追问，引导孩子注意到遗漏的部分，绝不能直接告诉孩子漏掉的具体数字或条件内容，follow_up不超过25字，用问句结尾。
            这是第${turnCount}轮对话（最多${maxTurns}轮）。
            只输出JSON：{"understood": true或false, "feedback": "...", "follow_up": "..."}，不要输出其他文字。
        """.trimIndent()

        val historyArr = JSONArray()
        for ((aiQ, stA) in priorPairs) {
            historyArr.put(JSONObject().apply {
                put("AI之前问的", aiQ ?: "（开场：用自己的话说说这道题在讲什么）")
                put("孩子当时的回答", stA)
            })
        }

        val userMessage = JSONObject().apply {
            put("题目", questionText)
            put("图片内容描述", imageDesc.ifBlank { "无图片" })
            put("此前的追问与孩子的回答", historyArr)
            put("孩子本轮最新的回答", latestAnswer)
        }.toString()

        return try {
            val reply = callLlmApi(settings, systemPrompt, userMessage)
            val json = extractJsonObject(reply)
            UnderstoodResult(
                understood = json.optBoolean("understood", false),
                feedback = json.optString("feedback", ""),
                followUp = json.optString("follow_up", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "checkUnderstanding failed", e)
            UnderstoodResult(understood = true, feedback = "理解得很好！", followUp = "")
        }
    }

    suspend fun checkMethodReasoning(
        settings: UserSettings,
        questionText: String,
        coreConcept: String,
        priorPairs: List<Pair<String?, String>>,
        latestAnswer: String,
        turnCount: Int,
        maxTurns: Int
    ): UnderstoodResult {
        val systemPrompt = """
            你是一位温柔耐心的小学数学老师。孩子正在说他打算用什么方法解决这道题（加法/减法/乘法/除法等）。
            判断孩子有没有说出"为什么"——哪怕只是很朴素的理由，比如"因为要把两个数合起来"，也算通过。
            如果孩子只说了方法名（比如"加法"）但没说理由：understood设为false，feedback留空，follow_up用一句话追问"为什么呢？"类型的问题。
            如果孩子说了方法+理由：understood设为true，feedback用一句话表扬，follow_up留空。
            只输出JSON：{"understood": true或false, "feedback": "...", "follow_up": "..."}。
        """.trimIndent()

        val historyArr = JSONArray()
        for ((aiQ, stA) in priorPairs) {
            historyArr.put(JSONObject().apply {
                put("AI之前问的", aiQ ?: "（开场：你打算用哪种方法解决这道题？）")
                put("孩子当时的回答", stA)
            })
        }

        val userMessage = JSONObject().apply {
            put("题目", questionText)
            put("核心概念", coreConcept.ifBlank { "无" })
            put("历史对话", historyArr)
            put("孩子本轮最新的回答", latestAnswer)
        }.toString()

        return try {
            val reply = callLlmApi(settings, systemPrompt, userMessage)
            val json = extractJsonObject(reply)
            UnderstoodResult(
                understood = json.optBoolean("understood", false),
                feedback = json.optString("feedback", ""),
                followUp = json.optString("follow_up", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "checkMethodReasoning failed", e)
            UnderstoodResult(understood = true, feedback = "想法很清晰！", followUp = "")
        }
    }

    suspend fun judgeAnswer(
        settings: UserSettings,
        question: Question,
        studentAnswer: String
    ): JudgeResult {
        val systemPrompt = """
            你是小学数学判题助手。判断学生答案是否等价正确，允许学生写过程、单位、中文表达。
            如果答案正确返回 JSON：{"correct": true, "reason": "..."}；
            否则返回 JSON：{"correct": false, "reason": "..."}。不要输出其他文字。
        """.trimIndent()

        val userMessage = JSONObject().apply {
            put("题目", question.text)
            put("图片内容描述", question.imageDesc.ifBlank { "无图片" })
            put("标准答案", question.answer)
            put("学生作答", studentAnswer)
        }.toString()

        return try {
            val reply = callLlmApi(settings, systemPrompt, userMessage)
            val json = extractJsonObject(reply)
            JudgeResult(
                correct = json.optBoolean("correct", false),
                reason = json.optString("reason", "AI 判题")
            )
        } catch (e: Exception) {
            Log.e(TAG, "judgeAnswer failed", e)
            // Local fallback check
            val isLocalCorrect = checkLocalAnswer(studentAnswer, question.answer)
            JudgeResult(correct = isLocalCorrect, reason = "AI 异常，回退本地判题: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────
/**
 * 七步 AI 批改：统一 JSON 契约 + 5 套 Prompt 模板 + 本地规则
 * 依据：《数学冒险岛_Android七步AI校验完整方案.md》
 *
 * 硬性约束（写进每条 system prompt）：
 * - 只输出一个 JSON 对象，无前后缀文字、无 markdown 代码块
 * - hint 绝不能直接说出正确答案或缺失内容
 * - 每项不超过 20 字
 */
private val GRADE_JSON_SPEC = """
    只输出JSON：{"correct": true或false, "feedback": "...", "hint": "...", "suggestion": "..."}，
    不要输出其他文字。feedback/hint/suggestion 每项不超过20字。
""".trimIndent()

/** 判题必须用低温度，避免同一答案多次判定不一致 */
private const val GRADE_TEMPERATURE = 0.0

// ── Step1：读题（费曼复述，多轮，宽松判定）──
suspend fun gradeReadStep(
    settings: UserSettings,
    questionText: String,
    imageDesc: String,
    priorPairs: List<Pair<String?, String>>,
    latestAnswer: String,
    turnCount: Int,
    maxTurns: Int
): StepGradeResult {
    val systemPrompt = """
        你是一位温柔耐心的小学数学老师，正在和一个1-3年级的孩子对话。
        孩子正在用自己的话复述一道数学题的意思。评判标准要宽松，
        只要抓住"已知了什么、要求什么"的大意就算理解，不要吹毛求疵。

        如果孩子的复述体现了正确的大意（哪怕不完整但没有明显错误）：
        correct设为true，feedback给一句简短表扬（不超过15字）。

        如果有明显遗漏（比如完全没提到某个已知条件，或说反了要求什么）：
        correct设为false，feedback留空，
        hint提出一个具体的追问方向，引导孩子注意到遗漏的部分
        （例如"你提到了第一排密码，那第二排呢？"），
        绝不能直接告诉孩子漏掉的具体数字或条件内容，
        suggestion给一句鼓励性的方法建议（比如"可以把题目再读一遍，圈出数字"）。

        如果已经是最后一轮（第${maxTurns}轮），hint可以稍微给多一点提示方向，
        但依然不能直接说出条件或答案。

        $GRADE_JSON_SPEC
    """.trimIndent()

    val historyArr = JSONArray()
    for ((aiQ, stA) in priorPairs) {
        historyArr.put(JSONObject().apply {
            put("AI之前问的", aiQ ?: "（开场：用自己的话说说这道题在讲什么）")
            put("孩子当时的回答", stA)
        })
    }
    val userMessage = JSONObject().apply {
        put("题目", questionText)
        put("图片内容描述", imageDesc.ifBlank { "无图片" })
        put("此前已经问过、孩子没答对的追问记录", historyArr)
        put("孩子本轮最新的回答", latestAnswer)
        put("当前轮次", "第${turnCount}轮，最多${maxTurns}轮")
    }.toString()

    return callGrading(settings, systemPrompt, userMessage)
}

// ── Step2：找条件（对照标准条件，客观校验）──
suspend fun gradeConditionsStep(
    settings: UserSettings,
    questionText: String,
    imageDesc: String,
    referenceConditions: String,
    userInput: String,
    attemptCount: Int = 1
): StepGradeResult {
    val systemPrompt = """
        你是一位耐心的小学数学老师，正在批改孩子"找已知条件"这一步。

        请判断孩子是否找全、找对了题目里的已知条件（允许表达方式不同，
        比如"358"和"第一排密码是358"视为等价；顺序不重要）。
        不要求逐字一致，但不能漏掉关键数字或条件，也不能凭空编造题目里没有的条件。

        如果找全找对了：correct设为true，feedback给一句简短鼓励（不超过15字）。

        如果遗漏或找错了：correct设为false，feedback留空，
        hint指出"大概漏了哪一类信息"（比如"题目里是不是还提到了另一个数？"），
        绝不能直接说出遗漏的具体数字，
        suggestion给一句方法建议（比如"把题目里出现的每个数字都圈出来看看"）。

        这是孩子第${attemptCount}次尝试，${if (attemptCount >= 2) "可以给稍多一点的提示方向，但仍不能直接说出具体数字。" else "提示要含蓄，只指方向。"}

        $GRADE_JSON_SPEC
    """.trimIndent()

    val userMessage = JSONObject().apply {
        put("题目", questionText)
        put("图片内容描述", imageDesc.ifBlank { "无图片" })
        put("标准的已知条件", referenceConditions.ifBlank { "（题库未提供，请按题目内容自行判断）" })
        put("孩子写下的已知条件", userInput)
    }.toString()

    return callGrading(settings, systemPrompt, userMessage)
}

// ── Step3：找问题（对照标准问题，客观校验）──
suspend fun gradeQuestionStep(
    settings: UserSettings,
    questionText: String,
    referenceQuestion: String,
    userInput: String,
    attemptCount: Int = 1
): StepGradeResult {
    val systemPrompt = """
        你是一位耐心的小学数学老师，正在批改孩子"找出题目要求什么"这一步。

        请判断孩子是否准确抓住了题目最终要求什么（允许表达方式不同，
        比如"一共多少"和"两个加起来是几"视为等价）。

        如果抓对了：correct设为true，feedback给一句简短鼓励（不超过15字）。

        如果理解错了或说的是别的东西：correct设为false，feedback留空，
        hint引导孩子重新关注题目最后一句话（比如"再读一下题目最后一句，问的是谁的数量？"），
        绝不能直接说出正确的问题是什么，
        suggestion给一句方法建议（比如"通常问句会带'一共''还剩''多少'这些词，找找看"）。

        这是孩子第${attemptCount}次尝试。

        $GRADE_JSON_SPEC
    """.trimIndent()

    val userMessage = JSONObject().apply {
        put("题目", questionText)
        put("标准的问题（题目要求求什么）", referenceQuestion.ifBlank { "（题库未提供，请按题目最后一句判断）" })
        put("孩子写下的理解", userInput)
    }.toString()

    return callGrading(settings, systemPrompt, userMessage)
}

// ── Step4：选方法（多轮，只校验"有没有说理由"，不校验方法本身）──
suspend fun gradeMethodStep(
    settings: UserSettings,
    questionText: String,
    coreConcept: String,
    priorPairs: List<Pair<String?, String>>,
    latestAnswer: String,
    turnCount: Int,
    maxTurns: Int
): StepGradeResult {
    val systemPrompt = """
        你是一位温柔耐心的小学数学老师。孩子正在说他打算用什么方法解决这道题
        （比如加法、减法、乘法、除法，或者更具体的思路）。

        你的任务不是判断"方法对不对"（后面列算式那一步会验证结果是否正确），
        而是判断孩子有没有说出"为什么"——哪怕只是很朴素的理由，
        比如"因为要把两个数合起来"，也算通过。只要不是完全说不出理由，
        或者理由和题目明显不相关，就应该判定通过。

        如果说出了理由（哪怕朴素）：correct设为true，feedback给一句简短鼓励（不超过15字）。

        如果完全说不出理由或者只说了方法名没说为什么：correct设为false，feedback留空，
        hint用一个具体的问题引导孩子说出理由（比如"为什么你觉得要用加法呢？"），
        suggestion给一句提示（比如"想想这个知识点：$coreConcept"）。

        这是第${turnCount}轮，最多${maxTurns}轮。

        $GRADE_JSON_SPEC
    """.trimIndent()

    val historyArr = JSONArray()
    for ((aiQ, stA) in priorPairs) {
        historyArr.put(JSONObject().apply {
            put("AI之前问的", aiQ ?: "（开场：你打算用哪种方法？为什么？）")
            put("孩子当时的回答", stA)
        })
    }
    val userMessage = JSONObject().apply {
        put("题目", questionText)
        put("这个知识点的核心概念", coreConcept.ifBlank { "无" })
        put("此前已经问过、孩子没答对的追问记录", historyArr)
        put("孩子本轮最新的回答", latestAnswer)
    }.toString()

    return callGrading(settings, systemPrompt, userMessage)
}

// ── Step5：列算式（本地规则判不出来才调，判"对"完全不需要 AI）──
suspend fun gradeFormulaStep(
    settings: UserSettings,
    questionText: String,
    imageDesc: String,
    correctAnswer: String,
    userAnswer: String,
    attemptCount: Int = 1
): StepGradeResult {
    val systemPrompt = """
        你是一位耐心的小学数学老师，正在批改孩子的列式作答。

        允许孩子写过程、单位、不同的表达形式（比如"12"和"十二"视为等价），
        但最终数值结果必须与标准答案完全一致，不能因为"孩子写了过程"或"态度认真"
        就放宽正确性标准。如果图片内容描述不是"无图片"，说明这是看图题，请结合它判断。

        如果正确：correct设为true，feedback给一句简短鼓励（不超过15字），
        hint和suggestion都留空字符串。

        如果不正确：correct设为false，feedback留空字符串，
        hint是一句引导性提示，帮孩子发现自己哪里可能算错了
        （绝不能直接说出正确答案或最终数值），
        suggestion是一句具体可操作的学习建议
        （比如"试着列竖式算一遍""用画图法数一数""检查一下有没有进位"）。

        这是孩子第${attemptCount}次尝试，${if (attemptCount >= 3) "已经有些受挫，提示可以给到接近答案，语气更温和，但仍不要直接说出最终数值。" else ""}

        $GRADE_JSON_SPEC
    """.trimIndent()

    val userMessage = JSONObject().apply {
        put("题目", questionText)
        put("图片内容描述", imageDesc.ifBlank { "无图片" })
        put("标准答案", correctAnswer)
        put("孩子的作答", userAnswer)
    }.toString()

    return callGrading(settings, systemPrompt, userMessage)
}

// ── Step7：总结（可选，轻量鼓励语，纯文本不判对错）──
suspend fun generateSummaryPraise(
    settings: UserSettings,
    questionText: String,
    method: String
): String {
    val systemPrompt = """
        你是一位温柔耐心的小学数学老师。孩子刚刚独立做对了一道题。
        请给他一句简短、具体、有针对性的鼓励语（不超过20字），
        不要泛泛而谈"你真棒"，尽量提到他这道题具体做得好的地方。
        只输出这句话本身，不要输出其他文字，不要输出JSON。
    """.trimIndent()
    val userMessage = JSONObject().apply {
        put("题目", questionText)
        put("用的方法", method.ifBlank { "认真思考" })
    }.toString()
    return try {
        callLlmApi(settings, systemPrompt, userMessage, 0.7).trim()
    } catch (e: Exception) {
        Log.e(TAG, "generateSummaryPraise failed", e)
        ""
    }
}

/** 统一的批改调用 + JSON 容错解析（解析失败默认判"错"，绝不误判为对）。
 *  2026-09-05：对快速失败类错误（429 限流 / 5xx / 连接重置）自动重试一次；
 *  超时类不重试（重试只会再耗一遍超时预算）。 */
private suspend fun callGrading(
    settings: UserSettings,
    systemPrompt: String,
    userMessage: String
): StepGradeResult {
    val raw = try {
        callLlmApi(settings, systemPrompt, userMessage, GRADE_TEMPERATURE)
    } catch (e: Exception) {
        Log.w(TAG, "grading call failed (${e.javaClass.simpleName}): ${e.message?.take(120)}")
        if (isRetryableLlmError(e)) {
            delay(1500) // 限流一般 1~2 秒后恢复
            Log.i(TAG, "retrying grading call once...")
            callLlmApi(settings, systemPrompt, userMessage, GRADE_TEMPERATURE)
        } else throw e
    }
    // 2026-09-05 关键修复：改用 indexOf 提取最外层 {...}，不再用正则！
    // 某些 Android ROM 的正则引擎对 \{ 转义抛 PatternSyntaxException
    // （"Syntax error in regexp pattern"）——这正是七步法上线以来
    // 所有"AI 暂时不可用"的真正根因（此前异常被吞，无法定位）。
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) {
        Log.w(TAG, "grading reply has no JSON object: ${raw.take(200)}")
        return StepGradeResult(correct = false, hint = "AI返回格式异常，请重新提交")
    }
    val jsonText = raw.substring(start, end + 1)
    return try {
        val obj = JSONObject(jsonText)
        StepGradeResult(
            correct = obj.optBoolean("correct", false),
            feedback = obj.optString("feedback", ""),
            hint = obj.optString("hint", ""),
            suggestion = obj.optString("suggestion", "")
        )
    } catch (e: Exception) {
        Log.e(TAG, "parseGradeJson failed: $raw", e)
        StepGradeResult(correct = false, hint = "AI返回格式异常，请重新提交")
    }
}

/** 可自动重试：连接重置等快速失败 IOException、HTTP 429 限流、HTTP 5xx。超时不可重试。
 *  2026-09-05：去正则实现（设备正则引擎兼容性），直接用字符串包含判断。 */
private fun isRetryableLlmError(e: Throwable): Boolean = when {
    e is java.net.SocketTimeoutException -> false
    e is java.io.IOException -> true
    else -> {
        val m = e.message ?: ""
        m.contains("HTTP 429") || (500..504).any { m.contains("HTTP $it") }
    }
}

/**
 * Step5 本地规则（能不调 AI 就不调）：
 * @return true=确定对，false=确定错，null=无法判定、交给 AI
 */
object LocalAnswerChecker {
    fun check(userInput: String, correctAnswer: String): Boolean? {
        val userTrim = userInput.trim()
        val correctTrim = correctAnswer.trim()

        val userNum = userTrim.toDoubleOrNull()
        val correctNum = correctTrim.toDoubleOrNull()
        if (userNum != null && correctNum != null) {
            return kotlin.math.abs(userNum - correctNum) < 1e-6
        }
        if (userTrim == correctTrim) return true

        // 口语化归一后再比（"十二"/"答案是12"/"12个" 等价）
        val userNorm = normalizeNumbers(userTrim)
        val correctNorm = normalizeNumbers(correctTrim)
        if (userNorm.isNotBlank() && userNorm == correctNorm) return true
        val un = userNorm.toDoubleOrNull()
        val cn = correctNum ?: correctNorm.toDoubleOrNull()
        if (un != null && cn != null && kotlin.math.abs(un - cn) < 1e-6) return true

        // 允许"3×4=12"这种带过程写法：抓最后一个数字对比
        if (correctNum != null) {
            val last = Regex("-?\\d+(?:\\.\\d+)?").findAll(userTrim)
                .mapNotNull { it.value.toDoubleOrNull() }.toList().lastOrNull()
            if (last != null && kotlin.math.abs(last - correctNum) < 1e-6) return true
            val lastNorm = Regex("-?\\d+(?:\\.\\d+)?").findAll(userNorm)
                .mapNotNull { it.value.toDoubleOrNull() }.toList().lastOrNull()
            if (lastNorm != null && kotlin.math.abs(lastNorm - correctNum) < 1e-6) return true
        }
        return null // 判不出来，交给 AI
    }
}

    suspend fun explainSolution(
        settings: UserSettings,
        question: Question
    ): String {
        val local = question.methodHint.trim()
        if (local.isNotBlank()) return local

        val systemPrompt = """
            你是一位小学数学老师。孩子这道题已经错了3次，请用2-3句话把解题思路讲清楚，
            语气温和鼓励，要写出关键算式，最后给出正确答案。不超过80字，纯文本不要markdown。
        """.trimIndent()
        val userMessage = JSONObject().apply {
            put("题目", if (question.story.isNotBlank()) "${question.story}\n${question.text}" else question.text)
            put("图片内容描述", question.imageDesc.ifBlank { "无图片" })
            put("标准答案", question.answer)
        }.toString()

        return try {
            callLlmApi(settings, systemPrompt, userMessage).trim().ifBlank {
                "正确答案是：${question.answer}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "explainSolution failed", e)
            "正确答案是：${question.answer}"
        }
    }

    private val CN_DIGITS = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '两' to 2, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )
    private val CN_DIGIT_CLASS = "[零〇一两二三四五六七八九]"

    /** "十" / "十二" / "三十" / "三十五" → 10 / 12 / 30 / 35（支持 0-99，够小学低年级） */
    private fun convertChineseTens(s: String): String =
        Regex("($CN_DIGIT_CLASS)?十($CN_DIGIT_CLASS)?").replace(s) { m ->
            val tens = m.groupValues[1].firstOrNull()?.let { CN_DIGITS[it] } ?: 1
            val ones = m.groupValues[2].firstOrNull()?.let { CN_DIGITS[it] } ?: 0
            (tens * 10 + ones).toString()
        }

    /** 剩余单个中文数字 → 阿拉伯数字 */
    private fun convertChineseDigits(s: String): String =
        Regex(CN_DIGIT_CLASS).replace(s) { m -> CN_DIGITS[m.value.first()]!!.toString() }

    /**
     * 把孩子输入的口语化数字统一成阿拉伯数字串，便于本地等价匹配：
     * "十二" / "十二个" / "答案是 12" / "12" 互相等价；"1打2" 之类保持原样不匹配。
     */
    private fun normalizeNumbers(raw: String): String {
        var s = raw.trim()
        if (s.isBlank()) return s
        s = convertChineseTens(s)
        s = convertChineseDigits(s)
        // 去掉修饰词与单位，只留数字、运算符、小数点
        s = s.replace(Regex("[答案是等于一共共有还有剩个只支本块元角分米的了是，,。、？?\\s]"), "")
        return s
    }

    fun checkLocalAnswer(userVal: String, correctVal: String): Boolean {
        val userTrim = userVal.trim()
        val correctTrim = correctVal.trim()
        if (userTrim == correctTrim) return true

        val userNum = userTrim.toDoubleOrNull()
        val correctNum = correctTrim.toDoubleOrNull()
        if (userNum != null && correctNum != null) {
            return Math.abs(userNum - correctNum) < 1e-6
        }

        // 口语化归一后再比一次："十二个" / "答案是 12" / "12" 都等价
        val userNorm = normalizeNumbers(userTrim)
        val correctNorm = normalizeNumbers(correctTrim)
        if (userNorm == correctNorm && userNorm.isNotBlank()) return true
        val userNormNum = userNorm.toDoubleOrNull()
        val correctNormNum = correctNorm.toDoubleOrNull()
        if (userNormNum != null && correctNormNum != null) {
            return Math.abs(userNormNum - correctNormNum) < 1e-6
        }

        // Extract numbers from user string
        val numbers = Regex("-?\\d+(?:\\.\\d+)?").findAll(userTrim).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (correctNum != null && numbers.isNotEmpty()) {
            if (Math.abs(numbers.last() - correctNum) < 1e-6) return true
        }
        val normNumbers = Regex("-?\\d+(?:\\.\\d+)?").findAll(userNorm).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (correctNum != null && normNumbers.isNotEmpty()) {
            if (Math.abs(normNumbers.last() - correctNum) < 1e-6) return true
        }

        return false
    }

    suspend fun generateVariantQuestion(
        settings: UserSettings,
        originalQuestion: Question
    ): Question = withContext(Dispatchers.IO) {
        val topic = Constants.KNOWLEDGE_MAP.firstOrNull { it.id == originalQuestion.topicId }
        val systemPrompt = """
            你是小学1-3年级数学题库设计助手，负责为"间隔复习"出一道变体题。
            变体题必须考察和原题完全相同的知识点、相同的解题方法与难度，但数字、人物、情境务必换掉。
            只返回 JSON：{"story": "新的故事引入", "text": "完整题目文本", "answer": "正确答案数字或简短文本", "hidden_traps": ["易错点提示"]}
        """.trimIndent()

        val userMessage = JSONObject().apply {
            put("知识点", topic?.title ?: "")
            put("核心概念", topic?.coreConcept ?: "")
            put("原题故事", originalQuestion.story)
            put("原题题目", originalQuestion.text)
            put("原题答案", originalQuestion.answer)
        }.toString()

        return@withContext try {
            val reply = callLlmApi(settings, systemPrompt, userMessage)
            val json = extractJsonObject(reply)
            val newText = json.optString("text", "").ifBlank { originalQuestion.text }
            val newAnswer = json.optString("answer", originalQuestion.answer)
            val newStory = json.optString("story", originalQuestion.story)
            val trapsArr = json.optJSONArray("hidden_traps")
            val trapsList = mutableListOf<String>()
            if (trapsArr != null) {
                for (i in 0 until trapsArr.length()) {
                    trapsList.add(trapsArr.optString(i))
                }
            }

            originalQuestion.copy(
                story = newStory,
                text = newText,
                answer = newAnswer,
                hiddenTrapsJson = if (trapsList.isNotEmpty()) JSONArray(trapsList).toString() else originalQuestion.hiddenTrapsJson,
                image = "",
                imageDesc = "",
                isAiVariant = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "generateVariantQuestion failed, returning original", e)
            originalQuestion
        }
    }

    suspend fun generateBatchQuestions(
        settings: UserSettings,
        topicId: String,
        requestText: String,
        count: Int
    ): List<Question> = withContext(Dispatchers.IO) {
        val topic = Constants.KNOWLEDGE_MAP.firstOrNull { it.id == topicId }
        val systemPrompt = """
            你是小学1-3年级数学题库设计助手。请只返回 JSON，格式为 {"questions": [...]}，
            每题必须包含：text, answer, story, method_hint, hidden_traps, reference (conditions, question)。
        """.trimIndent()

        val userMessage = JSONObject().apply {
            put("topic_id", topicId)
            put("topic_title", topic?.title ?: "")
            put("core_concept", topic?.coreConcept ?: "")
            put("common_mistake", topic?.commonMistake ?: "")
            put("count", count)
            put("request", requestText)
        }.toString()

        return@withContext try {
            val reply = callLlmApi(settings, systemPrompt, userMessage)
            val json = extractJsonObject(reply)
            val qArr = json.optJSONArray("questions") ?: JSONArray()
            val list = mutableListOf<Question>()
            for (i in 0 until qArr.length()) {
                val item = qArr.getJSONObject(i)
                val text = item.optString("text", "").trim()
                val answer = item.optString("answer", "").trim()
                if (text.isNotBlank() && answer.isNotBlank()) {
                    val traps = item.optJSONArray("hidden_traps")
                    val trapsList = mutableListOf<String>()
                    if (traps != null) {
                        for (j in 0 until traps.length()) trapsList.add(traps.optString(j))
                    }
                    val ref = item.optJSONObject("reference")
                    list.add(
                        Question(
                            id = "ai_${Random.nextInt(100000, 999999)}",
                            topicId = topicId,
                            story = item.optString("story", "AI 生成的练习题"),
                            text = text,
                            answer = answer,
                            methodHint = item.optString("method_hint", ""),
                            hiddenTrapsJson = JSONArray(trapsList).toString(),
                            conditionsRef = ref?.optString("conditions", "") ?: "",
                            questionRef = ref?.optString("question", "") ?: "",
                            isCustom = true
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "generateBatchQuestions failed", e)
            emptyList()
        }
    }

    suspend fun testConnection(settings: UserSettings): String {
        return callLlmApi(settings, "", "请只回复：测试成功！")
    }
}
