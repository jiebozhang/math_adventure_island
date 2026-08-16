package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Constants
import com.example.data.model.Question
import com.example.data.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
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

object GeminiHelper {
    private const val TAG = "GeminiHelper"

    private fun getApiKey(settings: UserSettings): String {
        return settings.llmApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
    }

    private suspend fun callLlmApi(
        settings: UserSettings,
        systemPrompt: String,
        userMessage: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(settings)
        if (apiKey.isBlank()) {
            throw IllegalStateException("未配置 Gemini API Key，请在家长控制台配置或通过 Secrets 设置。")
        }

        val provider = settings.llmProvider
        val apiBase = settings.llmApiBase.trim().removeSuffix("/")
        val model = settings.llmModel.ifBlank { "gemini-3.5-flash" }

        if (provider == "anthropic") {
            return@withContext callAnthropicApi(apiBase, apiKey, model, systemPrompt, userMessage, settings.llmTimeoutSeconds)
        } else if (apiBase.contains("generativelanguage.googleapis.com")) {
            return@withContext callGeminiNativeApi(apiBase, apiKey, model, systemPrompt, userMessage, settings.llmTimeoutSeconds)
        } else {
            return@withContext callOpenAiCompatibleApi(apiBase, apiKey, model, systemPrompt, userMessage, settings.llmTimeoutSeconds)
        }
    }

    private fun callGeminiNativeApi(
        apiBase: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String,
        timeoutSec: Int
    ): String {
        val urlString = "$apiBase/v1beta/models/$model:generateContent?key=$apiKey"
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutSec * 1000
            readTimeout = timeoutSec * 1000
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

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
            throw IllegalStateException("API HTTP $code: $errStr")
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
        timeoutSec: Int
    ): String {
        val endpoint = if (apiBase.endsWith("/chat/completions")) apiBase else "$apiBase/chat/completions"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutSec * 1000
            readTimeout = timeoutSec * 1000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
        }

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
            put("temperature", 0.3)
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }

        val code = conn.responseCode
        if (code != 200) {
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("API HTTP $code: $errStr")
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
        timeoutSec: Int
    ): String {
        val endpoint = if (apiBase.endsWith("/v1/messages")) apiBase else "$apiBase/v1/messages"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutSec * 1000
            readTimeout = timeoutSec * 1000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            doOutput = true
        }

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
            throw IllegalStateException("Anthropic API HTTP $code: $errStr")
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

    fun checkLocalAnswer(userVal: String, correctVal: String): Boolean {
        val userTrim = userVal.trim()
        val correctTrim = correctVal.trim()
        if (userTrim == correctTrim) return true

        val userNum = userTrim.toDoubleOrNull()
        val correctNum = correctTrim.toDoubleOrNull()
        if (userNum != null && correctNum != null) {
            return Math.abs(userNum - correctNum) < 1e-6
        }

        // Extract numbers from user string
        val numbers = Regex("-?\\d+(?:\\.\\d+)?").findAll(userTrim).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (correctNum != null && numbers.isNotEmpty()) {
            if (Math.abs(numbers.last() - correctNum) < 1e-6) return true
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
