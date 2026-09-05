import io

P = r'D:/Math_Adventure_Island_android/app/src/main/java/com/example/ui/viewmodel/MathViewModel.kt'
src = io.open(P, encoding='utf-8').read()

start = src.find('    /** 非阻断步骤的软反馈提示语（不调 LLM，零延迟零配额） */')
if start == -1:
    start = src.find('    /** 非阻断步骤的软反馈提示语')
end = src.find('    fun completeQuest() {')
assert start != -1 and end != -1 and end > start, "markers i=%s j=%s" % (start, end)

NEW = '''    /**
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

            // AI 批改未开启：列算式已确定错就按错，其余步骤诚实放行并提示
            if (!settings.llmJudgeEnabled) {
                if (localVerdict == false) {
                    failStep(step, "答案好像不太对，再检查一下？", onResult = onResult)
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
                val result = runGrading(questStep, question, answer, settings, step, turn)
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
            } catch (e: Exception) {
                Log.e("MathViewModel", "grade step $step failed", e)
                // 文档 4.5：AI 挂了不能卡住孩子，但本地已确定是错的必须仍按错处理
                if (localVerdict == false) {
                    failStep(step, "AI 暂时不可用，再检查一下答案哦", onResult = onResult)
                } else {
                    passStep(step, "网络异常，这次先放行～", onResult)
                }
            }
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
        val fullText = if (question.story.isNotBlank()) "${question.story}\\n${question.text}" else question.text
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

'''

out = src[:start] + NEW + src[end:]
io.open(P, 'w', encoding='utf-8').write(out)
print('old=%d new=%d' % (len(src), len(out)))
