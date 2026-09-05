package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiHelper
import com.example.data.ai.JudgeResult
import com.example.data.ai.StepGradeResult
import com.example.data.ai.UnderstoodResult
import com.example.data.model.*
import com.example.data.sync.SupabaseSyncException
import com.example.data.repository.MathRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    val stepFeedback: String = "",      // AI 对当前步骤作答的实时反馈
    val stepPassed: Boolean = false,     // 当前步骤是否通过校验
    // ── AI 批改状态机（七步硬校验）──
    val gradingState: StepGradingState = StepGradingState.Idle,
    val stepAttempts: Map<Int, Int> = emptyMap(),   // 每步已尝试次数
    val turnCounts: Map<Int, Int> = emptyMap(),     // 多轮追问轮次（Step1/Step4）
    val solutionText: String = "",                  // 解题演示内容
    val solutionRevealed: Boolean = false,          // 是否已看解析（不伪装成判对）
    val checklistChecked: Set<Int> = emptySet(),    // Step6 检查清单勾选项
    val viewedSolution: Boolean = false,            // 供成长档案区分"独立完成"vs"看解析过关"
    val stepPriorPairs: Map<Int, List<Pair<String?, String>>> = emptyMap(), // 多轮追问历史(AI提示, 孩子回答)
    /** Step5 缓存的判题结果，Step7 直接复用，不再重新判题 */
    val formulaJudge: Pair<Boolean, String>? = null,
    val judgeResult: JudgeResult? = null,
    val selectedMonsterId: String? = null,
    val isMonsterCapturedAnimation: Boolean = false,
    val timerSecondsLeft: Int = 1500,
    val isTimerRunning: Boolean = false
)

class MathViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MathRepository(application)
    private val _syncUiState = MutableStateFlow(SyncUiState(available = repository.isSupabaseConfigured()))
    val syncUiState: StateFlow<SyncUiState> = _syncUiState.asStateFlow()

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
            _syncUiState.update { it.copy(lastSyncAt = repository.getLastSyncAt()) }
            syncWithSupabase(silent = true)
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
                    isTimerRunning = true,
                    stepFeedback = "",
                    stepPassed = false,
                    gradingState = StepGradingState.Idle,
                    solutionText = "",
                    solutionRevealed = false,
                    checklistChecked = emptySet()
                )
            }
        }
    }

    /** 「再试一次」：清空批改状态，保留孩子已写的答案方便修改 */
    fun resetGrading() {
        _questState.update {
            it.copy(gradingState = StepGradingState.Idle, stepFeedback = "", isAiThinking = false, aiStatusText = "")
        }
    }

    /**
     * 达到尝试上限后的逃避口："太难了，看看解析"。
     * 诚实标记为"看了解析、未独立完成"，不伪装成判对，不计入掌握度。
     */
    fun revealSolutionAndAdvance() {
        _questState.update {
            it.copy(
                viewedSolution = true,
                solutionRevealed = true,
                formulaJudge = false to "已查看解析，未独立完成"
            )
        }
        val question = _questState.value.question ?: return
        val local = question.methodHint.trim()
        if (local.isNotBlank()) {
            _questState.update { it.copy(solutionText = local) }
            return
        }
        viewModelScope.launch {
            _questState.update { it.copy(isAiThinking = true, aiStatusText = "AI 老师正在准备演示...") }
            val text = GeminiHelper.explainSolution(repository.getUserSettingsSync(), question)
            _questState.update { it.copy(isAiThinking = false, aiStatusText = "", solutionText = text) }
        }
    }

    /**
     * 校验当前步骤并尝试推进（七步硬校验状态机）。
     *
     * 定级依据《数学冒险岛_Android七步AI校验完整方案.md》：
     * - Step1 读题 / Step2 找条件 / Step3 找问题 / Step4 选方法 / Step5 列算式 → 硬校验，必须 Passed 才能下一步
     * - Step6 检查 → 本地勾选清单（≥2 项），不调 AI
     * - Step7 总结 → 读 Step5 缓存结果，不重新判题
     */
    fun validateAndAdvance(answer: String, onResult: (Boolean, String) -> Unit) {
        val step = _questState.value.currentStep
        val question = _questState.value.question ?: return
        val questStep = QuestStep.of(step)

        // Step6 检查：走勾选清单，不看文本
        if (step == 5) {
            validateChecklist(onResult)
            return
        }
        if (answer.isBlank()) {
            failStep(step, "先写下你的想法吧～", countAttempt = false, onResult = onResult)
            return
        }

        // Step7 总结：不重新判题，直接放行（正确性已在 Step5 确定）
        if (!questStep.needsHardGrading) {
            passStep(step, "总结得很好，这道题的套路记住了吗？", onResult)
            return
        }

        // Step5 列算式：本地规则优先，判定为"对"就完全不调 AI
        var localVerdict: Boolean? = null
        if (questStep == QuestStep.FORMULA) {
            localVerdict = GeminiHelper.LocalAnswerChecker.check(answer, question.answer)
            if (localVerdict == true) {
                _questState.update { it.copy(formulaJudge = true to "本地判对") }
                passStep(step, "算对啦，真棒！", onResult)
                return
            }
        }

        val turn = (_questState.value.turnCounts[step] ?: 0) + 1
        viewModelScope.launch {
            val settings = repository.getUserSettingsSync()

            // AI 批改未开启：软性步骤放行，列算式给跳过出口（对齐 PC 端降级策略）
            if (!settings.llmJudgeEnabled) {
                if (localVerdict == false) {
                    failStep(step, "答案好像不太对，再检查一下？", onResult = onResult)
                } else if (questStep == QuestStep.FORMULA) {
                    // 列算式本地判不出 + 没开 AI：给两条出路（重试+跳过），不死等
                    _questState.update {
                        it.copy(gradingState = StepGradingState.Error("未开启 AI 批改，这题判不了。可在家长控制台开启后再试，或先跳过这步～"), isAiThinking = false, aiStatusText = "")
                    }
                    onResult(false, "未开启 AI 批改")
                } else {
                    passStep(step, "未开启 AI 批改，本次直接通过～", onResult)
                }
                return@launch
            }

            _questState.update {
                it.copy(
                    gradingState = StepGradingState.Grading,
                    isAiThinking = true, aiStatusText = "AI 老师正在批改...",
                    turnCounts = it.turnCounts + (step to turn)
                )
            }

            try {
                // 超时 = 家长控制台 AI 配置的"请求超时时间"（秒）乘 1000，没配置时默认 60s
                val timeoutMs = settings.llmTimeoutSeconds.coerceAtLeast(10) * 1000L
                val result = withTimeout(timeoutMs) { runGrading(questStep, question, answer, settings, step, turn) }
                // 防串台：孩子已手动翻页就不再写入旧结果
                if (_questState.value.currentStep != step) {
                    _questState.update { it.copy(isAiThinking = false, aiStatusText = "") }
                    return@launch
                }
                if (result.correct) {
                    if (questStep == QuestStep.FORMULA) {
                        _questState.update { it.copy(formulaJudge = true to result.feedback.ifBlank { "AI 判对" }) }
                    }
                    passStep(step, result.feedback.ifBlank { defaultPraise(step) }, onResult)
                } else {
                    // 记入多轮追问历史，供 Step1/Step4 下一轮使用
                    _questState.update {
                        val prev = it.stepPriorPairs[step] ?: emptyList()
                        it.copy(stepPriorPairs = it.stepPriorPairs + (step to prev + (result.hint to answer)))
                    }
                    failStep(step, result.hintAndSuggestion, onResult = onResult)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w("MathViewModel", "grade step $step timeout")
                degradeOnAiFailure(questStep, step, localVerdict, "网络超时", onResult)
            } catch (e: Exception) {
                Log.e("MathViewModel", "grade step $step failed", e)
                degradeOnAiFailure(questStep, step, localVerdict, shortAiError(e), onResult)
            }
        }
    }

    /**
     * 把 AI 调用异常提炼成孩子/家长能看懂的短原因。
     * 2026-09-05 新增：之前所有异常都笼统显示"AI 暂时不可用"，无法定位真实原因。
     * 注意：全部用字符串包含判断，不用正则（设备正则引擎兼容性坑，见 BUGFIX_LEDGER #15）。
     */
    private fun shortAiError(e: Throwable): String {
        val msg = e.message ?: ""
        val serverCode = (500..504).firstOrNull { msg.contains("HTTP $it") }
        return when {
            e is java.net.SocketTimeoutException && msg.contains("connect", ignoreCase = true) ->
                "网络连接超时，检查一下网络"
            e is java.net.SocketTimeoutException ->
                "AI 响应太慢，可在家长控制台调大请求超时时间"
            e is java.io.IOException ->
                "网络异常(${e.javaClass.simpleName})"
            msg.contains("HTTP 429") ->
                "接口限流(429)，稍等几秒再点重试"
            msg.contains("HTTP 401") || msg.contains("HTTP 403") ->
                "API Key 无效或没有权限"
            serverCode != null ->
                "AI 服务端错误($serverCode)"
            msg.isNotBlank() -> msg.take(30)
            else -> e.javaClass.simpleName
        }
    }

    /** 按步骤分流到对应的 Prompt 模板 */
    private suspend fun runGrading(
        questStep: QuestStep,
        question: Question,
        answer: String,
        settings: UserSettings,
        step: Int,
        turn: Int
    ): StepGradeResult {
        val fullText = if (question.story.isNotBlank()) "${question.story}\n${question.text}" else question.text
        val concept = Constants.KNOWLEDGE_MAP.firstOrNull { it.id == question.topicId }?.title ?: ""
        val attempt = (_questState.value.stepAttempts[step] ?: 0) + 1
        val prior = _questState.value.stepPriorPairs[step] ?: emptyList()

        return when (questStep) {
            QuestStep.READ -> GeminiHelper.gradeReadStep(
                settings = settings, questionText = fullText, imageDesc = question.imageDesc,
                priorPairs = prior, latestAnswer = answer, turnCount = turn, maxTurns = MAX_TURNS
            )
            QuestStep.FIND_CONDITIONS -> GeminiHelper.gradeConditionsStep(
                settings = settings, questionText = fullText, imageDesc = question.imageDesc,
                referenceConditions = question.conditionsRef, userInput = answer, attemptCount = attempt
            )
            QuestStep.FIND_QUESTION -> GeminiHelper.gradeQuestionStep(
                settings = settings, questionText = fullText,
                referenceQuestion = question.questionRef, userInput = answer, attemptCount = attempt
            )
            QuestStep.CHOOSE_METHOD -> GeminiHelper.gradeMethodStep(
                settings = settings, questionText = fullText, coreConcept = concept,
                priorPairs = prior, latestAnswer = answer, turnCount = turn, maxTurns = MAX_TURNS
            )
            QuestStep.FORMULA -> GeminiHelper.gradeFormulaStep(
                settings = settings, questionText = fullText, imageDesc = question.imageDesc,
                correctAnswer = question.answer, userAnswer = answer, attemptCount = attempt
            )
            else -> StepGradeResult(correct = true, feedback = "好的，继续～")
        }
    }

    private fun defaultPraise(step: Int): String = when (step) {
        0 -> "复述得很清楚！"
        1 -> "条件都找出来了，很棒！"
        2 -> "问题抓得准！"
        3 -> "思路说得很清楚！"
        4 -> "算对啦，真棒！"
        else -> "做得很好！"
    }

    /**
     * AI 失败降级策略（对齐 PC 端，见《数学冒险岛_电脑端AI失败降级策略_workbuddy执行Prompt.md》）：
     * 核心原则——技术故障永远不能变成孩子过不去的墙。
     * - 软性步骤（读题/找条件/找问题/选方法）：本来就不是判断客观对错，AI 挂了没法验证，
     *   默认相信孩子直接放行，不弹"重试"死胡同（孩子已输入的内容已实时存进 userAnswers）。
     * - 列算式（最终对错）：本地判错直接 Failed（不受 AI 影响）；本地判不出 + AI 挂了 →
     *   给两条出路：改完重新提交（主按钮"重试"）/ 先跳过这步（反馈卡跳过按钮），绝不死等。
     */
    private fun degradeOnAiFailure(
        questStep: QuestStep,
        step: Int,
        localVerdict: Boolean?,
        reason: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (localVerdict == false) {
            failStep(step, "答案好像不太对，再检查一下？", onResult = onResult)
            return
        }
        if (questStep != QuestStep.FORMULA) {
            passStep(step, "网络不太好，这步先过～", onResult)
        } else {
            _questState.update {
                it.copy(
                    gradingState = StepGradingState.Error("AI 判不了（$reason）。可以检查一下重新提交，或先跳过这步～"),
                    isAiThinking = false, aiStatusText = ""
                )
            }
            onResult(false, "AI 判不了：$reason")
        }
    }

    /**
     * AI 失败降级的"跳过"出口：跳过不是"看解析"，不记 viewedSolution，只是继续往下走。
     * 技术故障不能拦住孩子的进度。
     */
    fun skipStep() {
        _questState.update { it.copy(gradingState = StepGradingState.Idle, isAiThinking = false, aiStatusText = "") }
        nextStep()
    }

    /** Step6 检查：勾选清单至少 2 项 */
    private fun validateChecklist(onResult: (Boolean, String) -> Unit) {
        if (_questState.value.checklistChecked.size >= MIN_CHECKLIST) {
            passStep(5, "检查得很仔细！", onResult)
        } else {
            failStep(5, "至少勾选 ${MIN_CHECKLIST} 项检查内容哦～", countAttempt = false, onResult = onResult)
        }
    }

    fun toggleChecklistItem(index: Int) {
        _questState.update {
            val next = if (index in it.checklistChecked) it.checklistChecked - index else it.checklistChecked + index
            it.copy(checklistChecked = next, gradingState = StepGradingState.Idle, stepFeedback = "")
        }
    }

    /** 通过：置 Passed 并放行 */
    private fun passStep(step: Int, feedback: String, onResult: (Boolean, String) -> Unit) {
        _questState.update {
            it.copy(
                gradingState = StepGradingState.Passed(feedback), stepFeedback = feedback,
                stepPassed = true, isAiThinking = false, aiStatusText = ""
            )
        }
        onResult(true, feedback)
    }

    /** 未通过：置 Failed 并拦截（阻断由 canAdvance 判定） */
    private fun failStep(
        step: Int, message: String, countAttempt: Boolean = true, onResult: (Boolean, String) -> Unit
    ) {
        _questState.update {
            val attempt = if (countAttempt) (it.stepAttempts[step] ?: 0) + 1 else (it.stepAttempts[step] ?: 0)
            it.copy(
                gradingState = StepGradingState.Failed(message, attempt), stepFeedback = message,
                stepPassed = false, isAiThinking = false, aiStatusText = "",
                stepAttempts = it.stepAttempts + (step to attempt)
            )
        }
        onResult(false, message)
    }

    fun completeQuest() {
        _questState.update { it.copy(isTimerRunning = false) }
        // 用 step_4（列算式步骤）的答案做最终判题
        val state = _questState.value
        val allAnswers = state.userAnswers
        // 如果 step_4 有答案就用它做 formula_answer，否则拼接所有步骤答案
        val formulaAnswer = allAnswers["step_4"] ?: allAnswers.values.filter { it.isNotBlank() }.joinToString(" ")
        if (formulaAnswer.isNotBlank()) {
            allAnswers["formula_answer"] = formulaAnswer
        }
        performAnswerJudging()
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
        if (question.qtype == "matching") {
            performMatchingJudging(state, question)
            return
        }
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
                repository.recordSyncedAttempt(question, JSONObject().put("value", userVal).toString(), true)
                val defeatedMonster = repository.markMastered(question.id)
                repository.addExp(10)
                repository.recordDailyCorrect()
                if (defeatedMonster != null) {
                    repository.recordDailyMonsterDefeated()
                }
            }
        }
    }

    private fun performMatchingJudging(state: ActiveQuestState, question: Question) {
        // E.2: text_answer 型 matching 走 AI 判题，slot 型走多空比对
        if (question.isTextAnswerMatching()) {
            performTextAnswerMatching(state, question)
            return
        }
        val correct = JSONObject(question.correctAnswerJson.ifBlank { question.answer })
        val detail = JSONObject()
        var allCorrect = true
        val slots = JSONObject(question.layoutJson.ifBlank { "{}" }).optJSONArray("slots") ?: JSONArray()
        for (index in 0 until slots.length()) {
            val slot = slots.optString(index)
            val userValue = state.userAnswers["match_$slot"].orEmpty()
            val expected = correct.optString(slot)
            val slotCorrect = userValue == expected
            allCorrect = allCorrect && slotCorrect
            detail.put(slot, JSONObject().put("answer", userValue).put("correct", slotCorrect))
        }
        _questState.update {
            it.copy(
                isAiThinking = false,
                judgeResult = JudgeResult(
                    correct = allCorrect,
                    reason = if (allCorrect) "多空匹配全部正确" else "多空匹配有空格还需要调整"
                )
            )
        }
        viewModelScope.launch {
            repository.recordSyncedAttempt(question, detail.toString(), allCorrect)
            if (allCorrect) {
                val defeatedMonster = repository.markMastered(question.id)
                repository.addExp(10)
                repository.recordDailyCorrect()
                if (defeatedMonster != null) repository.recordDailyMonsterDefeated()
            }
        }
    }

    private fun performTextAnswerMatching(state: ActiveQuestState, question: Question) {
        val studentText = state.userAnswers["text_answer"].orEmpty()
        // 兜底：text_answer 没填，给个空串让 AI 判错
        val effectiveAnswer = studentText.ifBlank { "（孩子没有作答）" }
        val isLocal = GeminiHelper.checkLocalAnswer(effectiveAnswer, question.answer)
        _questState.update { it.copy(isAiThinking = true, aiStatusText = "AI 正在批改图文匹配题...") }
        viewModelScope.launch {
            val settings = repository.getUserSettingsSync()
            val judgeResult = if (isLocal) {
                JudgeResult(correct = true, reason = "本地规则判题：文本与参考答案一致")
            } else if (settings.llmJudgeEnabled) {
                GeminiHelper.judgeAnswer(settings, question, effectiveAnswer)
            } else {
                JudgeResult(correct = false, reason = "未启用 AI 判题，仅做了本地规则匹配")
            }
            _questState.update { it.copy(isAiThinking = false, judgeResult = judgeResult) }
            val detail = JSONObject().put("text_answer", effectiveAnswer).put("correct", judgeResult.correct)
            repository.recordSyncedAttempt(question, detail.toString(), judgeResult.correct)
            if (judgeResult.correct) {
                val defeatedMonster = repository.markMastered(question.id)
                repository.addExp(10)
                repository.recordDailyCorrect()
                if (defeatedMonster != null) repository.recordDailyMonsterDefeated()
            }
        }
    }

    fun syncWithSupabase(silent: Boolean = false) {
        if (!repository.isSupabaseConfigured()) {
            if (!silent) {
                _syncUiState.update {
                    it.copy(available = false, lastError = "请先在 local.properties 中配置 Supabase")
                }
            }
            return
        }
        viewModelScope.launch {
            _syncUiState.update { it.copy(isSyncing = true, available = true, lastError = "") }
            try {
                val result = repository.syncWithSupabase()
                _syncUiState.update {
                    it.copy(
                        isSyncing = false,
                        available = true,
                        lastSyncAt = result.lastSyncAt,
                        lastSummary = result.toSummary(),
                        lastError = ""
                    )
                }
            } catch (error: Exception) {
                _syncUiState.update {
                    it.copy(
                        isSyncing = false,
                        available = true,
                        lastError = if (silent) "" else (error.message ?: "同步失败")
                    )
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
            repository.recordSyncedAttempt(question, JSONObject().put("value", state.userAnswers["formula_answer"].orEmpty()).toString(), false)
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
