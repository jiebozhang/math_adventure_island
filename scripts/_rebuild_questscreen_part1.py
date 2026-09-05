# -*- coding: utf-8 -*-
# -*- 重建 QuestScreen.kt -*- 
# 重建策略：以会话历史里读取到的所有片段为基础，拼接出完整可编译的版本
import io

# 第一部分：imports + 颜色常量 + 7 步元数据 + GradingIndicator（这部分已知）
part1 = '''package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.components.NumberKeyboard
import com.example.ui.viewmodel.CHECKLIST_ITEMS
import com.example.ui.viewmodel.MIN_CHECKLIST
import com.example.ui.viewmodel.QuestStep
import com.example.ui.viewmodel.StepGradingState
import com.example.ui.viewmodel.canAdvance
import com.example.ui.components.PinyinText
import com.example.ui.components.ScratchpadDialog
import com.example.ui.components.StepProgress
import com.example.ui.theme.*
import com.example.ui.viewmodel.ActiveQuestState
import com.example.ui.viewmodel.MathViewModel

// ── 七步元数据（tag + 默认气泡文案）──
private data class StepMeta(val tag: String, val defaultBubble: String)

private val STEP_METAS = listOf(
    StepMeta("读题目 · 把题目读清楚",   "不着急算，先把题目一字一句读明白，看看讲的是一件什么事。"),
    StepMeta("找条件 · 圈出已知信息",   "题目里哪些数字是已经告诉我们的？把它们一个一个找出来。"),
    StepMeta("找问题 · 明确要求什么",   "最后那个问号问的到底是什么？把它单独拎出来看。"),
    StepMeta("讲方法 · 说出你的思路",   "那我们一起想：要知道每人分几块，得先算出一共有多少块饼干，对吗？"),
    StepMeta("列算式 · 写出计算过程",   "把刚才说的方法写成算式，先算什么、再算什么，一步都不能跳。"),
    StepMeta("回头检验 · 检查一遍",     "算完了别急着交——倒着算一遍，看看结果对不对得上。"),
    StepMeta("做总结 · 记住这个套路",   "这道题用的是「先合再分」，下次遇到类似的题，你还会做了吗？"),
)

// ── AI 批改中的文案轮播（避免孩子干等 LLM 响应）──
private val GRADING_TIPS = listOf(
    "AI 老师正在批改...",
    "正在理解你的思路...",
    "马上就好..."
)

/** 批改中：转圈 + 文案轮播（800ms 一换） */
@Composable
private fun GradingIndicator(fontSize: androidx.compose.ui.unit.TextUnit = 12.sp) {
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(800)
            idx = (idx + 1) % GRADING_TIPS.size
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = GoldText)
        Text(GRADING_TIPS[idx], fontSize = fontSize, color = GoldText)
    }
}

/**
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
                        Text("\\u26A1", fontSize = 15.sp)
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

// Design spec colors
private val GoldBorder = Color(0xFFFFC857)
private val DarkBg = Color(0xFF2D2A26)
private val GoldText = Color(0xFFFFC857)
private val BubbleBg = Color(0xFFFAF6F0)
private val PlaceholderColor = Color(0xFFD1D5DB)
private val AnswerColor = Color(0xFF1F2937)

'''

p = 'app/src/main/java/com/example/ui/screens/QuestScreen.kt'
io.open(p, 'w', encoding='utf-8').write(part1)
print('part1 written, bytes:', len(part1.encode('utf-8')))
