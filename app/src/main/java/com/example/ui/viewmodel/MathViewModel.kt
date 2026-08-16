package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiHelper
import com.example.data.ai.JudgeResult
import com.example.data.ai.UnderstoodResult
import com.example.data.model.*
import com.example.data.repository.MathRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class StepConversationState(
    val messages: List<Pair<String, String>> = emptyList(), // Pair(role: "ai"/"user", text: String)
    val turnCount: Int = 0,
    val passed: Boolean = false
)

data class ActiveQuestState(
    val question: Question? = null,
    val isReview: Boolean = false,
    val currentStep: Int = 0, // 0 to 6
    val userAnswers: MutableMap<String, String> = mutableMapOf(),
    val step1Conv: StepConversationState = StepConversationState(messages = listOf("ai" to "嗨勇者！先别急着算，用你自己的话说说，这道题在讲什么？")),
    val step4Conv: StepConversationState = StepConversationState(messages = listOf("ai" to "你打算用哪种方法解决这道题？先告诉我你的想法～")),
    val isAiThinking: Boolean = false,
    val aiStatusText: String = "",
    val judgeResult: JudgeResult? = null,
    val selectedMonsterId: String? = null,
    val isMonsterCapturedAnimation: Boolean = false,
    val timerSecondsLeft: Int = 1500,
    val isTimerRunning: Boolean = false
)

class MathViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MathRepository(application)

    val userSettings: StateFlow<UserSettings> = repository.userSettings
        .filterNotNull()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    val allQuestions: StateFlow<List<Question>> = repository.allQuestions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Constants.DEFAULT_QUESTION_BANK
        )

    val wrongQuestions: StateFlow<List<WrongQuestion>> = repository.wrongQuestions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val masteredQuestions: StateFlow<List<MasteredQuestion>> = repository.masteredQuestions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val diaryEntries: StateFlow<List<DiaryEntry>> = repository.diaryEntries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val monsterStats: StateFlow<List<MonsterStats>> = repository.monsterStats
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _questState = MutableStateFlow(ActiveQuestState())
    val questState: StateFlow<ActiveQuestState> = _questState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeDefaultDataIfNeeded()
        }
    }

    fun startQuest(question: Question, isReview: Boolean = false) {
        val timerSec = userSettings.value.timerMinutes * 60
        _questState.value = ActiveQuestState(
            question = question,
            isReview = isReview,
            currentStep = 0,
            userAnswers = mutableMapOf(),
            step1Conv = StepConversationState(messages = listOf("ai" to "嗨勇者！先别急着算，用你自己的话说说，这道题在讲什么？")),
            step4Conv = StepConversationState(messages = listOf("ai" to "你打算用哪种方法解决这道题？先告诉我你的想法～")),
            timerSecondsLeft = timerSec,
            isTimerRunning = true
        )
    }

    fun startReviewQuest(question: Question, onReady: () -> Unit) {
        viewModelScope.launch {
            val settings = repository.getUserSettingsSync()
            val canUseAi = settings.llmJudgeEnabled && (settings.llmApiKey.isNotBlank() || com.example.BuildConfig.GEMINI_API_KEY.isNotBlank())
            val targetQuestion = if (canUseAi) {
                _questState.update { it.copy(isAiThinking = true, aiStatusText = "AI 正在生成变体题...") }
                val variant = GeminiHelper.generateVariantQuestion(settings, question)
                _questState.update { it.copy(isAiThinking = false, aiStatusText = "") }
                variant
            } else {
                question
            }
            startQuest(targetQuestion, isReview = true)
            onReady()
        }
    }

    fun nextStep() {
        val current = _questState.value.currentStep
        if (current < 6) {
            _questState.update {
                it.copy(
                    currentStep = current + 1,
                    isTimerRunning = current + 1 < 6
                )
            }
            if (current + 1 == 6) {
                // Summary step: perform answer judging
                performAnswerJudging()
            }
        }
    }

    fun prevStep() {
        val current = _questState.value.currentStep
        if (current > 0) {
            _questState.update { it.copy(currentStep = current - 1) }
        }
    }

    fun quitQuest() {
        _questState.update { it.copy(isTimerRunning = false, question = null) }
    }

    fun setUserAnswer(key: String, value: String) {
        _questState.value.userAnswers[key] = value
    }

    fun updateTimerTick() {
        val current = _questState.value
        if (current.isTimerRunning && current.timerSecondsLeft > 0) {
            _questState.update { it.copy(timerSecondsLeft = it.timerSecondsLeft - 1) }
        }
    }

    fun sendStep1FeynmanUserMessage(text: String, onComplete: () -> Unit) {
        val state = _questState.value
        val question = state.question ?: return
        val currentConv = state.step1Conv

        if (currentConv.passed) {
            state.userAnswers["feynman_text"] = currentConv.messages.filter { it.first == "user" }.joinToString("\n") { it.second }
            nextStep()
            onComplete()
            return
        }

        val updatedMsgs = currentConv.messages + ("user" to text)
        val newTurn = currentConv.turnCount + 1
        _questState.update {
            it.copy(
                step1Conv = currentConv.copy(messages = updatedMsgs, turnCount = newTurn),
                isAiThinking = true,
                aiStatusText = "AI 老师正在阅读你的复述..."
            )
        }

        viewModelScope.launch {
            val settings = repository.getUserSettingsSync()
            val helpless = listOf("不会", "不知道", "不懂", "没听懂").any { text.contains(it) }
            if (helpless) {
                val passed = newTurn >= 3
                val fallback = if (passed) {
                    "没关系，你已经坚持完成三轮了！我们继续下一步吧。"
                } else {
                    "试着填一下：题目告诉我们有 ___，想让我们求 ___？"
                }
                _questState.update {
                    it.copy(
                        step1Conv = it.step1Conv.copy(
                            messages = it.step1Conv.messages + ("ai" to fallback),
                            passed = passed
                        ),
                        isAiThinking = false
                    )
                }
                if (passed) {
                    state.userAnswers["feynman_text"] = updatedMsgs.filter { it.first == "user" }.joinToString("\n") { it.second }
                }
                onComplete()
                return@launch
            }
            if (!settings.llmJudgeEnabled) {
                // Local fallback
                val passed = text.length >= 2
                val aiReply = if (passed) "不错，理解得很好！" else "再试着用一两句话讲讲题目在说什么吧！"
                _questState.update {
                    it.copy(
                        step1Conv = it.step1Conv.copy(
                            messages = it.step1Conv.messages + ("ai" to aiReply),
                            passed = passed
                        ),
                        isAiThinking = false
                    )
                }
                if (passed) {
                    state.userAnswers["feynman_text"] = text
                }
                onComplete()
                return@launch
            }

            val priorPairs = mutableListOf<Pair<String?, String>>()
            var lastAi: String? = null
            for ((role, msg) in currentConv.messages) {
                if (role == "ai") lastAi = msg
                else if (role == "user") {
                    priorPairs.add(lastAi to msg)
                    lastAi = null
                }
            }

            val result = GeminiHelper.checkUnderstanding(
                settings = settings,
                questionText = question.text,
                imageDesc = question.imageDesc,
                priorPairs = priorPairs,
                latestAnswer = text,
                turnCount = newTurn,
                maxTurns = 3
            )

            val aiMessage = if (result.understood) {
                result.feedback.ifBlank { "不错，理解得很好！" }
            } else if (newTurn >= 3) {
                "试着填一下：题目告诉我们 ${question.conditionsRef.ifBlank { "已知条件" }}，想让我们求 ${question.questionRef.ifBlank { "问题" }}（先记下你的想法，我们继续下一步～）"
            } else {
                result.followUp.ifBlank { "再想想，是不是还漏了点什么？" }
            }

            val passed = result.understood || newTurn >= 3

            _questState.update {
                it.copy(
                    step1Conv = it.step1Conv.copy(
                        messages = it.step1Conv.messages + ("ai" to aiMessage),
                        passed = passed
                    ),
                    isAiThinking = false
                )
            }
            if (passed) {
                state.userAnswers["feynman_text"] = updatedMsgs.filter { it.first == "user" }.joinToString("\n") { it.second }
            }
            onComplete()
        }
    }

    fun sendStep4MethodUserMessage(text: String, onComplete: () -> Unit) {
        val state = _questState.value
        val question = state.question ?: return
        val currentConv = state.step4Conv

        if (currentConv.passed) {
            state.userAnswers["method"] = currentConv.messages.filter { it.first == "user" }.joinToString("\n") { it.second }
            nextStep()
            onComplete()
            return
        }

        val updatedMsgs = currentConv.messages + ("user" to text)
        val newTurn = currentConv.turnCount + 1
        _questState.update {
            it.copy(
                step4Conv = currentConv.copy(messages = updatedMsgs, turnCount = newTurn),
                isAiThinking = true,
                aiStatusText = "AI 老师正在分析你的解题思路..."
            )
        }

        viewModelScope.launch {
            val settings = repository.getUserSettingsSync()
            if (!settings.llmJudgeEnabled) {
                val passed = text.isNotBlank()
                val aiReply = if (passed) "想法很清晰！" else "先选一种方法吧！"
                _questState.update {
                    it.copy(
                        step4Conv = it.step4Conv.copy(
                            messages = it.step4Conv.messages + ("ai" to aiReply),
                            passed = passed
                        ),
                        isAiThinking = false
                    )
                }
                if (passed) {
                    state.userAnswers["method"] = text
                }
                onComplete()
                return@launch
            }

            val topic = Constants.KNOWLEDGE_MAP.firstOrNull { it.id == question.topicId }
            val priorPairs = mutableListOf<Pair<String?, String>>()
            var lastAi: String? = null
            for ((role, msg) in currentConv.messages) {
                if (role == "ai") lastAi = msg
                else if (role == "user") {
                    priorPairs.add(lastAi to msg)
                    lastAi = null
                }
            }

            val result = GeminiHelper.checkMethodReasoning(
                settings = settings,
                questionText = question.text,
                coreConcept = topic?.coreConcept ?: "",
                priorPairs = priorPairs,
                latestAnswer = text,
                turnCount = newTurn,
                maxTurns = 3
            )

            val aiMessage = if (result.understood) {
                result.feedback.ifBlank { "不错，想法很清晰！" }
            } else if (newTurn >= 3) {
                "小提示：想想这个知识点 —— ${topic?.coreConcept ?: "根据题目条件列式"}（先记下你的想法，我们继续下一步～）"
            } else {
                result.followUp.ifBlank { "为什么要用这个方法呢？" }
            }

            val passed = result.understood || newTurn >= 3

            _questState.update {
                it.copy(
                    step4Conv = it.step4Conv.copy(
                        messages = it.step4Conv.messages + ("ai" to aiMessage),
                        passed = passed
                    ),
                    isAiThinking = false
                )
            }
            if (passed) {
                state.userAnswers["method"] = updatedMsgs.filter { it.first == "user" }.joinToString("\n") { it.second }
            }
            onComplete()
        }
    }

    private fun performAnswerJudging() {
        val state = _questState.value
        val question = state.question ?: return
        val userVal = state.userAnswers["formula_answer"] ?: ""

        // Local check first
        val isLocal = GeminiHelper.checkLocalAnswer(userVal, question.answer)

        _questState.update { it.copy(isAiThinking = true, aiStatusText = "AI 正在批改答案...") }

        viewModelScope.launch {
            val settings = repository.getUserSettingsSync()
            val judgeResult = if (isLocal) {
                JudgeResult(correct = true, reason = "本地规则判题：最终数字正确")
            } else if (settings.llmJudgeEnabled) {
                GeminiHelper.judgeAnswer(settings, question, userVal)
            } else {
                JudgeResult(correct = isLocal, reason = "本地规则判题")
            }

            _questState.update { it.copy(isAiThinking = false, judgeResult = judgeResult) }

            val topic = Constants.KNOWLEDGE_MAP.firstOrNull { it.id == question.topicId }
            val topicTitle = topic?.title ?: ""

            if (judgeResult.correct) {
                val defeatedMonster = repository.markMastered(question.id)
                repository.addExp(10)
                repository.recordDailyCorrect()
                if (defeatedMonster != null) {
                    repository.recordDailyMonsterDefeated()
                }
            }
        }
    }

    fun submitCorrectQuestSummary(note: String, score: Int, onComplete: (Boolean) -> Unit) {
        val state = _questState.value
        val question = state.question ?: return
        val topic = Constants.KNOWLEDGE_MAP.firstOrNull { it.id == question.topicId }
        val topicTitle = topic?.title ?: ""

        viewModelScope.launch {
            repository.addDiaryEntry(topicTitle, "success", note, score)
            if (note.isNotBlank()) {
                repository.recordDailyMethodNote()
            }
            val rewardClaimedNow = repository.claimDailyRewardIfReady()
            onComplete(rewardClaimedNow)
        }
    }

    fun submitWrongQuestSummary(monsterId: String, onComplete: () -> Unit) {
        val state = _questState.value
        val question = state.question ?: return
        val topic = Constants.KNOWLEDGE_MAP.firstOrNull { it.id == question.topicId }
        val topicTitle = topic?.title ?: ""
        val monsterName = Constants.MONSTERS[monsterId]?.name ?: "怪兽"

        viewModelScope.launch {
            repository.recordMonsterSeen(monsterId)
            repository.addWrongQuestion(question.id, monsterId)
            repository.addDiaryEntry(topicTitle, "fail", "被${monsterName}捣乱了，3天后复仇！", null)
            onComplete()
        }
    }

    fun updateUserSettings(update: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            repository.updateSettings(update)
        }
    }

    fun addCustomQuestion(question: Question, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.addCustomQuestion(question)
            onComplete()
        }
    }

    fun importQuestionsFromJson(rawJson: String, onComplete: (Int, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val trimmed = rawJson.trim()
                val array = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONArray().apply { put(JSONObject(trimmed)) }
                val questions = buildList {
                    for (index in 0 until array.length()) {
                        val obj = array.getJSONObject(index)
                        val traps = obj.optJSONArray("hiddenTraps") ?: JSONArray(obj.optString("hiddenTraps", "[]"))
                        add(
                            Question(
                                id = obj.optString("id").ifBlank { "custom_${System.currentTimeMillis()}_$index" },
                                topicId = obj.optString("topicId", Constants.KNOWLEDGE_MAP.first().id),
                                story = obj.optString("story"),
                                text = obj.optString("text"),
                                answer = obj.optString("answer"),
                                methodHint = obj.optString("methodHint"),
                                hiddenTrapsJson = traps.toString(),
                                conditionsRef = obj.optString("conditionsRef"),
                                questionRef = obj.optString("questionRef"),
                                image = obj.optString("image"),
                                isCustom = true
                            )
                        )
                    }
                }.filter { it.text.isNotBlank() && it.answer.isNotBlank() }
                repository.addCustomQuestions(questions)
                onComplete(questions.size, null)
            } catch (error: Exception) {
                onComplete(0, "JSON 格式不正确，请检查题目字段")
            }
        }
    }

    fun deleteQuestion(questionId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteQuestion(questionId)
            onComplete()
        }
    }

    fun generateAiBatchQuestions(topicId: String, requestText: String, count: Int, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val settings = repository.getUserSettingsSync()
            val questions = GeminiHelper.generateBatchQuestions(settings, topicId, requestText, count)
            if (questions.isNotEmpty()) {
                questions.forEach { repository.addCustomQuestion(it) }
            }
            onComplete(questions.size)
        }
    }

    fun testLlmConnection(settingsOverride: UserSettings? = null, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val settings = settingsOverride ?: repository.getUserSettingsSync()
                val res = GeminiHelper.testConnection(settings)
                onResult(true, res)
            } catch (e: Exception) {
                onResult(false, e.message ?: "连接失败")
            }
        }
    }
}
