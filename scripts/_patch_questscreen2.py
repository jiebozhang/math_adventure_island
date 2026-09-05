# -*- coding: utf-8 -*-
import io

p = 'app/src/main/java/com/example/ui/screens/QuestScreen.kt'
s = io.open(p, encoding='utf-8').read()

i = s.index('@Composable\nprivate fun GradingFeedbackCard(')
# 找到紧跟其后的下一个 /** 之前的 @Composable canGoNext 起点
# 整个旧块结尾是 "}"; 用下一个 /** 注释做切片终点
end_marker = '/** 「下一步」是否可点'
end_idx = s.index(end_marker, i)
# 切片起点是 @Composable，结尾是 end_marker 起点
old_block = s[i:end_idx]
print('OLD block length:', len(old_block))
print('--- last 200 chars of OLD ---')
print(repr(old_block[-200:]))

new_block = u'''@Composable
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
}

'''

s = s[:i] + new_block + s[end_idx:]
print('NEW file length:', len(s))

# ============ Step 2: 修复主按钮调用 ============
# 手机版主按钮
old_phone = '''                onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText) { answerText = "" } },
                    enabled = canGoNext(stepIndex, questState),'''
new_phone = '''                onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText, checklistLocalOk) { answerText = "" } },
                    enabled = canGoNext(stepIndex, questState, checklistLocalOk),'''
assert old_phone in s, 'phone button old not found'
s = s.replace(old_phone, new_phone, 1)
print('phone button OK')

# 平板版主按钮
old_tab = '''                onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText) { answerText = "" } },
                    enabled = canGoNext(stepIndex, questState),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBg,'''
new_tab = '''                onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText, checklistLocalOk) { answerText = "" } },
                    enabled = canGoNext(stepIndex, questState, checklistLocalOk),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBg,'''
assert old_tab in s, 'tablet button old not found'
s = s.replace(old_tab, new_tab, 1)
print('tablet button OK')

# 批改中指示器
s = s.replace('if (questState.gradingState is GradingState.Grading) {', 'if (questState.gradingState is StepGradingState.Grading) {')
print('grading indicator OK')

# ============ Step 3: 修复 GradingFeedbackCard 调用 ============
old_call = '''            // AI 批改未通过：提示 + 建议 + 重试 / 看演示
            GradingFeedbackCard(
                gradingState = questState.gradingState,
                solutionText = questState.solutionText,
                onRetry = { viewModel.resetGrading() },
                onReveal = { viewModel.revealSolution() }
            )'''
new_call = '''            // AI 批改未通过：提示 + 重试 / 看解析
            GradingFeedbackCard(
                gradingState = questState.gradingState,
                solutionText = questState.solutionText,
                onRetry = { viewModel.resetGrading() },
                onReveal = { viewModel.revealSolutionAndAdvance() }
            )'''
assert old_call in s, 'phone card call not found'
s = s.replace(old_call, new_call, 1)
print('phone card call OK')

old_call_tab = '''                // AI 批改未通过：提示 + 建议 + 重试 / 看演示
                GradingFeedbackCard(
                    gradingState = questState.gradingState,
                    solutionText = questState.solutionText,
                    onRetry = { viewModel.resetGrading() },
                    onReveal = { viewModel.revealSolution() }
                )'''
new_call_tab = '''                // AI 批改未通过：提示 + 重试 / 看解析
                GradingFeedbackCard(
                    gradingState = questState.gradingState,
                    solutionText = questState.solutionText,
                    onRetry = { viewModel.resetGrading() },
                    onReveal = { viewModel.revealSolutionAndAdvance() }
                )'''
assert old_call_tab in s, 'tablet card call not found'
s = s.replace(old_call_tab, new_call_tab, 1)
print('tablet card call OK')

# ============ Step 4: 插入 checklistLocalOk 变量定义（两个 Composable 都要）============
# 检查是否已经有 checklistLocalOk 变量
if 'checklistLocalOk' not in s:
    # 在 QuestScreen 函数体内，stepIndex 定义后插入
    inject = '''    val checklistLocalOk = stepIndex != 5 || questState.checklistChecked.size >= MIN_CHECKLIST
'''
    # 找 stepIndex 定义点
    marker = '    val stepIndex = questState.currentStep\n'
    assert marker in s
    s = s.replace(marker, marker + inject, 1)
    print('injected checklistLocalOk')

io.open(p, 'w', encoding='utf-8').write(s)
print('done')
