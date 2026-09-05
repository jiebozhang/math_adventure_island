# -*- coding: utf-8 -*-
import io, sys

p = 'app/src/main/java/com/example/ui/screens/QuestScreen.kt'
s = io.open(p, encoding='utf-8').read()

# ===== 1) 替换 4 个辅助函数（用 UTF-8 字面量写）=====
old = u'''/**
 * 未通过的提示卡：引导性提示 + 学习建议 + 尝试次数，
 * 达到上限后给「看解题演示」，展示后直接显示演示内容。
 */
@Composable
private fun GradingFeedbackCard(
    gradingState: GradingState,
    solutionText: String,
    onRetry: () -> Unit,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (gradingState) {
        is GradingState.Incorrect -> {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF3CD), modifier = modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\uD83D\uDCA1", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("再想想", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        Spacer(modifier = Modifier.weight(1f))
                        if (gradingState.attemptCount > 0) {
                            Text("第 ${gradingState.attemptCount} 次尝试", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                        }
                    }
                    Text(gradingState.hint, fontSize = 13.sp, color = Color(0xFF374151))
                    if (gradingState.suggestion.isNotBlank()) {
                        Text("\uD83D\uDCA1 建议：${gradingState.suggestion}", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                    if (solutionText.isNotBlank()) {
                        HorizontalDivider(color = Color(0xFFF0D9A0), thickness = 1.dp)
                        Text("解题演示", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        Text(solutionText, fontSize = 13.sp, color = Color(0xFF1D4ED8))
                        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("我懂了，再试一次", fontSize = 12.sp)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("再试一次", fontSize = 12.sp)
                            }
                            if (gradingState.canRevealSolution) {
                                TextButton(onClick = onReveal, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text("看解题演示", fontSize = 12.sp, color = Color(0xFFB45309))
                                }
                            }
                        }
                    }
                }
            }
        }
        is GradingState.Error -> {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFE4E6), modifier = modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(gradingState.message, fontSize = 13.sp, color = Color(0xFFB91C1C))
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("重试", fontSize = 12.sp)
                    }
                }
            }
        }
        else -> Unit
    }
}

/** 「下一步」是否可点：批改中禁用（防重复点击）；阻断步骤答错且未看演示时禁用 */
private fun canGoNext(stepIndex: Int, questState: ActiveQuestState): Boolean {
    return when (questState.gradingState) {
        is GradingState.Grading -> false
        is GradingState.Incorrect -> !(isBlockingStep(stepIndex) && !questState.solutionRevealed)
        else -> true
    }
}

/** 主按钮文案：软反馈步骤判错后变成"跳过这步"，看过演示后变成"看过啦，继续" */
private fun mainButtonLabel(stepIndex: Int, isLastStep: Boolean, questState: ActiveQuestState): String = when {
    isLastStep -> "完成闯关"
    questState.solutionRevealed -> "看过啦，继续"
    questState.gradingState is GradingState.Incorrect && !isBlockingStep(stepIndex) -> "跳过这步"
    else -> "下一步"
}

/** 主按钮点击：最后一步直接交卷；可跳过时直接推进；否则走 AI 批改 */
private fun handleMainAction(
    viewModel: MathViewModel,
    stepIndex: Int,
    isLastStep: Boolean,
    questState: ActiveQuestState,
    answer: String,
    onCleared: () -> Unit
) {
    if (isLastStep) {
        viewModel.completeQuest()
        return
    }
    val alreadyFailed = questState.gradingState is GradingState.Incorrect
    val skippable = (alreadyFailed && !isBlockingStep(stepIndex)) || questState.solutionRevealed
    if (skippable) {
        onCleared()
        viewModel.nextStep()
        return
    }
    viewModel.validateAndAdvance(answer) { passed, _ ->
        if (passed) {
            onCleared()
            viewModel.nextStep()
        }
    }
}'''

new = u'''/**
 * 未通过的提示卡：引导性提示 + 尝试次数，
 * 达到上限后给「看看解析」，展示后直接显示演示内容。
 */
@Composable
private fun GradingFeedbackCard(
    gradingState: StepGradingState,
    solutionText: String,
    onRetry: () -> Unit,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (gradingState) {
        is StepGradingState.Failed -> {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF3CD), modifier = modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\uD83D\uDCA1", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("再想想", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        Spacer(modifier = Modifier.weight(1f))
                        Text("第 ${gradingState.attemptCount} 次尝试", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                    }
                    Text(gradingState.message, fontSize = 13.sp, color = Color(0xFF374151))
                    if (solutionText.isNotBlank()) {
                        HorizontalDivider(color = Color(0xFFF0D9A0), thickness = 1.dp)
                        Text("解题演示", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        Text(solutionText, fontSize = 13.sp, color = Color(0xFF1D4ED8))
                        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("我懂了，再试一次", fontSize = 12.sp)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("再试一次", fontSize = 12.sp)
                            }
                            if (gradingState.attemptCount >= 3) {
                                TextButton(onClick = onReveal, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text("太难了，看看解析", fontSize = 12.sp, color = Color(0xFFB45309))
                                }
                            }
                        }
                    }
                }
            }
        }
        is StepGradingState.Error -> {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFE4E6), modifier = modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(gradingState.message, fontSize = 13.sp, color = Color(0xFFB91C1C))
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("重试", fontSize = 12.sp)
                    }
                }
            }
        }
        else -> Unit
    }
}

/** 「下一步」是否可点：批改中禁用；硬校验步骤必须 Passed；检查/总结由 localOk 决定 */
private fun canGoNext(stepIndex: Int, questState: ActiveQuestState, localOk: Boolean = true): Boolean {
    return canAdvance(stepIndex, questState.gradingState, localOk)
}

/** 主按钮文案 */
private fun mainButtonLabel(stepIndex: Int, isLastStep: Boolean, questState: ActiveQuestState): String = when {
    isLastStep -> "完成闯关"
    questState.solutionRevealed -> "看过啦，继续"
    questState.gradingState is StepGradingState.Failed && !QuestStep.of(stepIndex).needsHardGrading -> "跳过这步"
    else -> "下一步"
}

/** 主按钮点击：最后一步直接交卷；可跳过时直接推进；否则走 AI 批改 */
private fun handleMainAction(
    viewModel: MathViewModel,
    stepIndex: Int,
    isLastStep: Boolean,
    questState: ActiveQuestState,
    answer: String,
    localOk: Boolean,
    onCleared: () -> Unit
) {
    if (isLastStep) {
        viewModel.completeQuest()
        return
    }
    val alreadyFailed = questState.gradingState is StepGradingState.Failed
    val skippable = (alreadyFailed && !QuestStep.of(stepIndex).needsHardGrading) || questState.solutionRevealed
    if (skippable) {
        onCleared()
        viewModel.nextStep()
        return
    }
    viewModel.validateAndAdvance(answer) { passed, _ ->
        if (passed) {
            onCleared()
            viewModel.nextStep()
        }
    }
}'''

if old not in s:
    print('OLD 1 NOT FOUND, fallback to fuzzy line-based replace')
    # 退而求其次：逐函数定位
    funcs = ['GradingFeedbackCard', 'canGoNext(', 'mainButtonLabel(', 'handleMainAction(']
    print('Found:', [f in s for f in funcs])
    sys.exit(1)

s = s.replace(old, new, 1)
print('step1 replaced')

# ===== 2) 替换主按钮调用（手机版）=====
old_phone = '''                // Main button: validate then advance（可用性由 AI 批改状态决定）
                Button(
                    onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText) { answerText = "" } },
                    enabled = canGoNext(stepIndex, questState),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.4f)
                ) {
                    if (questState.gradingState is GradingState.Grading) {
                        GradingIndicator(fontSize = 12.sp)
                    } else {
                        Text(mainButtonLabel(stepIndex, isLastStep, questState), color = GoldText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }'''
new_phone = '''                // Main button: validate then advance（可用性由 AI 批改状态决定）
                Button(
                    onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText, checklistLocalOk) { answerText = "" } },
                    enabled = canGoNext(stepIndex, questState, checklistLocalOk),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.4f)
                ) {
                    if (questState.gradingState is StepGradingState.Grading) {
                        GradingIndicator(fontSize = 12.sp)
                    } else {
                        Text(mainButtonLabel(stepIndex, isLastStep, questState), color = GoldText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }'''
if old_phone in s:
    s = s.replace(old_phone, new_phone, 1)
    print('step2 replaced (phone button)')
else:
    print('OLD 2 (phone button) NOT FOUND')

# ===== 3) 替换主按钮调用（平板版）=====
old_tab = '''                Button(
                    onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText) { answerText = "" } },
                    enabled = canGoNext(stepIndex, questState),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBg, disabledContainerColor = Color(0xFF9CA3AF)),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (questState.gradingState is GradingState.Grading) {
                        GradingIndicator(fontSize = 14.sp)
                    } else {
                        Text(mainButtonLabel(stepIndex, isLastStep, questState), color = GoldText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }'''
new_tab = '''                Button(
                    onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText, checklistLocalOk) { answerText = "" } },
                    enabled = canGoNext(stepIndex, questState, checklistLocalOk),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBg, disabledContainerColor = Color(0xFF9CA3AF)),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (questState.gradingState is StepGradingState.Grading) {
                        GradingIndicator(fontSize = 14.sp)
                    } else {
                        Text(mainButtonLabel(stepIndex, isLastStep, questState), color = GoldText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }'''
if old_tab in s:
    s = s.replace(old_tab, new_tab, 1)
    print('step3 replaced (tablet button)')
else:
    print('OLD 3 (tablet button) NOT FOUND')

# ===== 4) 替换 GradingFeedbackCard 调用（手机版）=====
old_card_phone = '''            // AI 批改未通过：提示 + 建议 + 重试 / 看演示
            GradingFeedbackCard(
                gradingState = questState.gradingState,
                solutionText = questState.solutionText,
                onRetry = { viewModel.resetGrading() },
                onReveal = { viewModel.revealSolution() }
            )
'''
new_card_phone = '''            // AI 批改未通过：提示 + 重试 / 看解析
            GradingFeedbackCard(
                gradingState = questState.gradingState,
                solutionText = questState.solutionText,
                onRetry = { viewModel.resetGrading() },
                onReveal = { viewModel.revealSolutionAndAdvance() }
            )
'''
if old_card_phone in s:
    s = s.replace(old_card_phone, new_card_phone, 1)
    print('step4 replaced (phone card)')
else:
    print('OLD 4 (phone card) NOT FOUND')

# ===== 5) 替换 GradingFeedbackCard 调用（平板版）=====
old_card_tab = '''                // AI 批改未通过：提示 + 建议 + 重试 / 看演示
                GradingFeedbackCard(
                    gradingState = questState.gradingState,
                    solutionText = questState.solutionText,
                    onRetry = { viewModel.resetGrading() },
                    onReveal = { viewModel.revealSolution() }
                )
'''
new_card_tab = '''                // AI 批改未通过：提示 + 重试 / 看解析
                GradingFeedbackCard(
                    gradingState = questState.gradingState,
                    solutionText = questState.solutionText,
                    onRetry = { viewModel.resetGrading() },
                    onReveal = { viewModel.revealSolutionAndAdvance() }
                )
'''
if old_card_tab in s:
    s = s.replace(old_card_tab, new_card_tab, 1)
    print('step5 replaced (tablet card)')
else:
    print('OLD 5 (tablet card) NOT FOUND')

io.open(p, 'w', encoding='utf-8').write(s)
print('done')
