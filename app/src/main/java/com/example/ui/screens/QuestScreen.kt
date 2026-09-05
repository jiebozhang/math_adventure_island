package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (gradingState) {
        is StepGradingState.Failed -> {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF3CD), modifier = modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\u26A1", fontSize = 15.sp)
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
            // AI 失败降级卡：给两条出路——重试 或 跳过这步，绝不死锁
            // （技术故障不能变成孩子过不去的墙，参考 PC 端降级策略）
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFE4E6), modifier = modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(gradingState.message, fontSize = 13.sp, color = Color(0xFFB91C1C))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("重试", fontSize = 12.sp)
                        }
                        TextButton(onClick = onSkip, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("先跳过这步", fontSize = 12.sp, color = Color(0xFFB45309))
                        }
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
    questState.gradingState is StepGradingState.Failed -> "再试一次"
    questState.gradingState is StepGradingState.Error -> "重试"
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
    // 已看解析 → 直接跳过
    if (questState.solutionRevealed) {
        onCleared()
        viewModel.nextStep()
        return
    }
    // 非硬校验步骤答错 → 允许跳过
    val failedSoft = questState.gradingState is StepGradingState.Failed && !QuestStep.of(stepIndex).needsHardGrading
    if (failedSoft) {
        onCleared()
        viewModel.nextStep()
        return
    }
    // Failed/Error 硬校验步骤 → 重新调 AI 批改
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

/**
 * 长按说话、自动续听拼接的语音输入按钮。
 * 解决"说话中途停顿就被截断"的问题：
 * - 按下：startListening + isHolding=true + 清空 accumulatedText
 * - onResults：把这段识别结果追加到 accumulatedText，如果 isHolding 仍 true → 立刻重新 startListening
 * - onError(SPEECH_TIMEOUT/NO_MATCH)：静音截断，如果 isHolding 仍 true → 重新 startListening
 * - 松开：stopListening + isHolding=false + onResult(accumulatedText) + 清空 accumulatedText
 * - 静音参数调到 4 秒（默认 1 秒太短）
 */
@Composable
private fun VoiceButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    // 用 mutableStateOf 包裹 isHolding 和 accumulatedText，确保在 lambda 闭包里可见最新值
    val isHolding = remember { androidx.compose.runtime.mutableStateOf(false) }
    val accumulatedText = remember { StringBuilder() }
    // 累积 partial 结果，onResults 时取最完整的（finalText 或 partialText 中更长的）
    val partialText = remember { StringBuilder() }

    fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // 返回多个候选结果，取最长的，避免被"第一个"短答案卡住
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        // 静音判定调大（默认 1s 太短，孩子停顿就被截断）
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000)
    }

    fun startListening() {
        try {
            recognizer?.startListening(buildIntent())
            listening = true
            message = ""
        } catch (e: Exception) {
            message = "\u8bed\u97f3\u542f\u52a8\u5931\u8d25"
            listening = false
            isHolding.value = false
        }
    }

    fun stopListening() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
        listening = false
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { message = "\u6309\u4f4f\u8bf4\u8bdd\uff0c\u677e\u5f00\u7ed3\u675f" }
            override fun onBeginningOfSpeech() { message = "\u6b63\u5728\u542c\u4f60\u8bf4\u2026" }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onResults(results: android.os.Bundle?) {
                // 取多个候选中最长的，避免识别器返回最短的"最佳"结果
                val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = candidates?.maxByOrNull { it.length }
                // 如果 final 比 partial 还短（识别器判断用户还要继续，返回了中间结果），用 partial
                val chosen = when {
                    text.isNullOrBlank() -> null
                    partialText.isNotEmpty() && partialText.length > text.length -> partialText.toString()
                    else -> text
                }
                if (!chosen.isNullOrBlank()) {
                    if (accumulatedText.isNotEmpty()) accumulatedText.append(' ')
                    accumulatedText.append(chosen)
                    message = accumulatedText.toString()
                }
                // 按住期间：自动续听，拼下一段
                if (isHolding.value) {
                    partialText.setLength(0)
                    Handler(Looper.getMainLooper()).postDelayed({ if (isHolding.value) startListening() }, 300)
                } else {
                    // 已松开：最终结果写入输入框
                    val finalText = accumulatedText.toString().trim()
                    if (finalText.isNotBlank()) onResult(finalText)
                    accumulatedText.setLength(0)
                    partialText.setLength(0)
                    listening = false
                }
            }
            override fun onError(error: Int) {
                if (isHolding.value && (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)) {
                    // 静音截断，但手指还在按着 → 续听
                    Handler(Looper.getMainLooper()).postDelayed({ if (isHolding.value) startListening() }, 300)
                    return
                }
                message = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "\u6ca1\u6709\u9ea6\u514b\u98ce\u6743\u9650"
                    SpeechRecognizer.ERROR_NO_MATCH -> "\u6ca1\u542c\u6e05\uff0c\u91cd\u65b0\u70b9\u6309\u8bd5"
                    SpeechRecognizer.ERROR_NETWORK -> "\u7f51\u7edc\u4e0d\u597d"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "\u8bf4\u8bdd\u65f6\u95f4\u592a\u77ed"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "\u8bc6\u522b\u5668\u5fd9\uff0c\u7b49\u4e00\u4e0b\u518d\u8bd5"
                    SpeechRecognizer.ERROR_CLIENT -> "\u8bc6\u522b\u5668\u5f02\u5e38\uff0c\u91cd\u65b0\u70b9\u6309"
                    SpeechRecognizer.ERROR_AUDIO -> "\u5f55\u97f3\u5f02\u5e38"
                    else -> "\u8bed\u97f3\u8bc6\u522b\u51fa\u9519\uff08\u9519\u8bef\u7801 $error\uff09"
                }
                // 真正错误：把已累积的文字写入输入框，避免丢失
                if (!isHolding.value) {
                    val finalText = accumulatedText.toString().trim()
                    if (finalText.isNotBlank()) onResult(finalText)
                    accumulatedText.setLength(0)
                }
                listening = false
                isHolding.value = false
            }
            override fun onEndOfSpeech() {
                // 系统 detect 静音结束后触发，这里不主动 stop，等 onResults
                message = if (isHolding.value) "\u6b63\u5728\u6574\u7406\u2026\u7ee7\u7eed\u8bf4" else "\u6b63\u5728\u6574\u7406"
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                // 取多个候选中最长的累积到 partialText，onResults 时会和 final 比较
                val candidates = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = candidates?.maxByOrNull { it.length }
                if (!text.isNullOrBlank()) {
                    partialText.setLength(0)
                    partialText.append(text)
                    val preview = if (accumulatedText.isNotEmpty()) "${accumulatedText} $text" else text
                    message = preview
                }
            }
        })
        onDispose { recognizer?.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isHolding.value = true
            accumulatedText.setLength(0)
            partialText.setLength(0)
            startListening()
        } else {
            message = "\u6ca1\u6709\u9ea6\u514b\u98ce\u6743\u9650"
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (listening) Color(0xFF4F6BED) else AccentAmber,
            modifier = Modifier
                .size(56.dp)
                .clickable {
                    if (listening) {
                        // 正在录：停止 + 写入输入框
                        isHolding.value = false
                        stopListening()
                        val finalText = accumulatedText.toString().trim()
                        if (finalText.isNotBlank()) onResult(finalText)
                        accumulatedText.setLength(0)
                        partialText.setLength(0)
                    } else if (recognizer == null) {
                        message = "\u8fd9\u53f0\u8bbe\u5907\u4e0d\u652f\u6301\u8bed\u97f3\u8f93\u5165"
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        // 开始录：isHolding=true，onResults 会自动续听
                        isHolding.value = true
                        accumulatedText.setLength(0)
                        partialText.setLength(0)
                        startListening()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, contentDescription = "\u8bed\u97f3\u8f93\u5165", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        if (message.isNotBlank()) {
            Text(message, fontSize = 10.sp, color = MutedGray, maxLines = 2)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QuestScreen(
    viewModel: MathViewModel,
    userSettings: UserSettings,
    allQuestions: List<Question>,
    masteredQuestions: List<MasteredQuestion>,
    onQuit: () -> Unit,
    onStartNextQuest: (Question) -> Unit,
    onNavigateTraining: () -> Unit
) {
    val questState by viewModel.questState.collectAsState()
    val question = questState.question ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val stepIndex = questState.currentStep
    val isLastStep = stepIndex == 6
    val stepMeta = STEP_METAS.getOrNull(stepIndex) ?: STEP_METAS[0]

    // 当前步骤的本地条件：Step6 检查清单至少 2 项；其他步骤 text 非空
    var answerField by remember(stepIndex, question.id) { mutableStateOf(TextFieldValue("", TextRange(0))) }
    val answerText: String = answerField.text
    val checklistLocalOk = stepIndex != 5 || questState.checklistChecked.size >= MIN_CHECKLIST

    // 全屏图片缩放
    var showImageZoom by remember { mutableStateOf(false) }
    // 草稿纸
    var showScratchpad by remember { mutableStateOf(false) }
    // AI 提示气泡
    var showHintBubble by remember { mutableStateOf(true) }
    // 数字键盘
    var keyboardExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var answerFieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(answerFieldFocused) {
        if (answerFieldFocused) bringIntoViewRequester.bringIntoView()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stepMeta.tag, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Text("第 ${stepIndex + 1} 步 / 共 7 步", fontSize = 11.sp, color = MutedGray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onQuit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        },
        containerColor = BgColor
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 顶部 stepper
            StepProgress(currentStep = stepIndex, totalSteps = 7, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))

            // 中部可滚动：题目 + AI 气泡 + 答题
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))

                // 题目卡片
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 题干
                        val fullQuestionText = buildString {
                            append(question.story)
                            if (question.text.isNotBlank()) {
                                if (isNotBlank()) append("\n")
                                append(question.text)
                            }
                        }
                        PinyinText(
                            text = fullQuestionText,
                            showPinyin = true,
                            fontSize = 15.sp,
                            color = TextDark
                        )
                        // 配图
                        if (question.image.isNotBlank()) {
                            AsyncImage(
                                model = question.image,
                                contentDescription = question.imageDesc.ifBlank { "题目配图" },
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showImageZoom = true }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // AI 气泡
                if (showHintBubble) {
                    Surface(shape = RoundedCornerShape(12.dp), color = BubbleBg, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stepMeta.defaultBubble, fontSize = 13.sp, color = TextDark)
                            if (questState.stepFeedback.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("AI: ${questState.stepFeedback}", fontSize = 12.sp, color = Color(0xFF1D4ED8))
                            }
                        }
                    }
                }

                // AI 批改中提示
                if (questState.gradingState is StepGradingState.Grading) {
                    Spacer(modifier = Modifier.height(6.dp))
                    GradingIndicator(fontSize = 12.sp)
                }

                // 批改未通过的反馈卡
                GradingFeedbackCard(
                    gradingState = questState.gradingState,
                    solutionText = questState.solutionText,
                    onRetry = { viewModel.resetGrading() },
                    onReveal = { viewModel.revealSolutionAndAdvance() },
                    onSkip = { viewModel.skipStep() },
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Step6 检查清单
                if (stepIndex == 5) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("勾选检查清单（至少 ${MIN_CHECKLIST} 项）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Spacer(modifier = Modifier.height(8.dp))
                            CHECKLIST_ITEMS.forEachIndexed { i, item ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleChecklistItem(i) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = i in questState.checklistChecked, onCheckedChange = { viewModel.toggleChecklistItem(i) })
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(item, fontSize = 13.sp, color = TextDark)
                                }
                            }
                        }
                    }
                }

                // 答题区（非 Step6）
                if (stepIndex != 5) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp, max = 120.dp)
                            .padding(10.dp)
                        ) {
                            BasicTextField(
                                value = answerField,
                                onValueChange = { v ->
                                    answerField = v
                                    viewModel.setUserAnswer("step_$stepIndex", v.text)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .bringIntoViewRequester(bringIntoViewRequester)
                                    .onFocusEvent { state -> answerFieldFocused = state.isFocused }
                                    .clickable { keyboardController?.show() },
                                textStyle = TextStyle(color = AnswerColor, fontSize = 16.sp),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(GoldText),
                                decorationBox = { inner ->
                                    if (answerText.isEmpty()) {
                                        Text(when (stepIndex) {
                                            0 -> "用自己的话说说这道题在讲什么…"
                                            1 -> "把已知的数字都写出来…"
                                            2 -> "问号问的是什么？…"
                                            3 -> "打算用什么方法？为什么？…"
                                            4 -> "列算式，写出过程…"
                                            else -> "用一两句话总结一下这套方法…"
                                        }, color = PlaceholderColor, fontSize = 14.sp)
                                    }
                                    inner()
                                }
                            )
                        }
                    }
                }
            }

            // 底部键盘 + 运算符条 + 主按钮
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("×", "÷", "−", "+", ".").forEach { key ->
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFB45309), modifier = Modifier.weight(1f).height(36.dp).clickable {
                            val cur = answerField.selection.start
                            val s = answerField.text
                            val ins = if (s.isEmpty() || cur == 0) key else " $key "
                            val newText = s.substring(0, cur) + ins + s.substring(cur)
                            answerField = TextFieldValue(newText, TextRange(cur + ins.length))
                            viewModel.setUserAnswer("step_$stepIndex", newText)
                        }) { Box(contentAlignment = Alignment.Center) { Text(key, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) } }
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF6B6258), modifier = Modifier.weight(1f).height(36.dp).clickable {
                        val sel = answerField.selection
                        val s = answerField.text
                        val (newText, newCur) = if (!sel.collapsed) {
                            s.removeRange(sel.min, sel.max) to sel.min
                        } else if (sel.start > 0) {
                            val idx = sel.start - 1
                            s.removeRange(idx, sel.start) to idx
                        } else {
                            s to 0
                        }
                        answerField = TextFieldValue(newText, TextRange(newCur))
                        viewModel.setUserAnswer("step_$stepIndex", newText)
                    }) { Box(contentAlignment = Alignment.Center) { Text("删除", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } }
                }
                AnimatedVisibility(visible = keyboardExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        NumberKeyboard(
                                onDigit = { d ->
                                    val cur = answerField.selection.start
                                    val txt = answerField.text
                                    val newText = txt.substring(0, cur) + d + txt.substring(cur)
                                    answerField = TextFieldValue(newText, TextRange(cur + 1))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },
                                onOperator = { op ->
                                    val cur = answerField.selection.start
                                    val txt = answerField.text
                                    val ins = if (txt.isEmpty() || cur == 0) op else " $op "
                                    val newText = txt.substring(0, cur) + ins + txt.substring(cur)
                                    answerField = TextFieldValue(newText, TextRange(cur + ins.length))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },
                                onBackspace = {
                                    val sel = answerField.selection
                                    val s = answerField.text
                                    val (newText, newCur) = if (!sel.collapsed) {
                                        s.removeRange(sel.min, sel.max) to sel.min
                                    } else if (sel.start > 0) {
                                        val idx = sel.start - 1
                                        s.removeRange(idx, sel.start) to idx
                                    } else {
                                        s to 0
                                    }
                                    answerField = TextFieldValue(newText, TextRange(newCur))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 操作行：提示 / 草稿 / 语音 / 键盘 / 主按钮
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = if (showHintBubble) Color(0xFFFFF3CD) else Color(0xFFFAF6F0), modifier = Modifier.weight(1f).height(44.dp).clickable { showHintBubble = !showHintBubble }) {
                        Box(contentAlignment = Alignment.Center) { Text(if (showHintBubble) "收起提示" else "AI提示", fontSize = 12.sp, color = TextDark) }
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFAF6F0), modifier = Modifier.weight(1f).height(44.dp).clickable { showScratchpad = true }) {
                        Box(contentAlignment = Alignment.Center) { Text("草稿", fontSize = 13.sp, color = TextDark) }
                    }
                    VoiceButton(
                        onResult = { t ->
                            val s = answerField.text
                            val newText = if (s.isBlank()) t else "$s $t"
                            answerField = TextFieldValue(newText, TextRange(newText.length))
                            viewModel.setUserAnswer("step_$stepIndex", newText)
                        }
                    )
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFAF6F0), modifier = Modifier.weight(1f).height(44.dp).clickable { keyboardExpanded = !keyboardExpanded }) {
                        Box(contentAlignment = Alignment.Center) { Text(if (keyboardExpanded) "收起" else "键盘", fontSize = 13.sp, color = TextDark) }
                    }
                    // 主按钮
                    Button(
                        onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerField.text, checklistLocalOk) { answerField = TextFieldValue("", TextRange(0)) } },
                        enabled = canGoNext(stepIndex, questState, checklistLocalOk),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color(0xFFCCCCCC)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        if (questState.gradingState is StepGradingState.Grading) {
                            GradingIndicator(fontSize = 12.sp)
                        } else {
                            Text(mainButtonLabel(stepIndex, isLastStep, questState), color = GoldText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // 全屏图片缩放
    if (showImageZoom && question.image.isNotBlank()) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showImageZoom = false }) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { showImageZoom = false }, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = question.image,
                    contentDescription = "题目配图放大",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
    }

    // 草稿纸对话框
    if (showScratchpad) {
        ScratchpadDialog(onDismiss = { showScratchpad = false })
    }
}


// ── QuestScreenTablet（平板横屏双栏：左 60% 题目，右 40% 步骤）──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestScreenTablet(
    viewModel: MathViewModel,
    userSettings: UserSettings,
    allQuestions: List<Question>,
    masteredQuestions: List<MasteredQuestion>,
    onQuit: () -> Unit,
    onStartNextQuest: (Question) -> Unit,
    onNavigateTraining: () -> Unit
) {
    val questState by viewModel.questState.collectAsState()
    val question = questState.question ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val stepIndex = questState.currentStep
    val isLastStep = stepIndex == 6
    val stepMeta = STEP_METAS.getOrNull(stepIndex) ?: STEP_METAS[0]
    var answerField by remember(stepIndex, question.id) { mutableStateOf(TextFieldValue("", TextRange(0))) }
    val answerText: String = answerField.text
    val checklistLocalOk = stepIndex != 5 || questState.checklistChecked.size >= MIN_CHECKLIST
    var showImageZoom by remember { mutableStateOf(false) }
    var showScratchpad by remember { mutableStateOf(false) }
    var showHintBubble by remember { mutableStateOf(true) }
    var keyboardExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("${stepMeta.tag} · 第 ${stepIndex + 1}/7 步", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = { IconButton(onClick = onQuit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextDark) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        },
        containerColor = BgColor
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 左 60%：题目 + AI 气泡 + 答题（可滚动）
            Column(modifier = Modifier.weight(0.6f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val fullQuestionTextTablet = buildString {
                            append(question.story)
                            if (question.text.isNotBlank()) {
                                if (isNotBlank()) append("\n")
                                append(question.text)
                            }
                        }
                        PinyinText(text = fullQuestionTextTablet, showPinyin = true, fontSize = 18.sp, color = TextDark)
                        if (question.image.isNotBlank()) {
                            AsyncImage(
                                model = question.image,
                                contentDescription = question.imageDesc.ifBlank { "题目配图" },
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)).clickable { showImageZoom = true }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (showHintBubble) {
                    Surface(shape = RoundedCornerShape(14.dp), color = BubbleBg, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(stepMeta.defaultBubble, fontSize = 15.sp, color = TextDark)
                            if (questState.stepFeedback.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("AI: ${questState.stepFeedback}", fontSize = 14.sp, color = Color(0xFF1D4ED8))
                            }
                        }
                    }
                }
                if (questState.gradingState is StepGradingState.Grading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    GradingIndicator(fontSize = 13.sp)
                }
                GradingFeedbackCard(
                    gradingState = questState.gradingState,
                    solutionText = questState.solutionText,
                    onRetry = { viewModel.resetGrading() },
                    onReveal = { viewModel.revealSolutionAndAdvance() },
                    onSkip = { viewModel.skipStep() },
                    modifier = Modifier.padding(top = 10.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                // Step6 检查清单（平板版）
                if (stepIndex == 5) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("勾选检查清单（至少 ${MIN_CHECKLIST} 项）", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Spacer(modifier = Modifier.height(10.dp))
                            CHECKLIST_ITEMS.forEachIndexed { i, item ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleChecklistItem(i) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = i in questState.checklistChecked, onCheckedChange = { viewModel.toggleChecklistItem(i) })
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(item, fontSize = 14.sp, color = TextDark)
                                }
                            }
                        }
                    }
                } else {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp, max = 140.dp).padding(14.dp)) {
                            BasicTextField(
                                value = answerField,
                                onValueChange = { v -> answerField = v; viewModel.setUserAnswer("step_$stepIndex", v.text) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(color = AnswerColor, fontSize = 17.sp),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(GoldText),
                                decorationBox = { inner ->
                                    if (answerText.isEmpty()) {
                                        Text(when (stepIndex) {
                                            0 -> "用自己的话说说这道题在讲什么…"
                                            1 -> "把已知的数字都写出来…"
                                            2 -> "问号问的是什么？…"
                                            3 -> "打算用什么方法？为什么？…"
                                            4 -> "列算式，写出过程…"
                                            else -> "用一两句话总结一下这套方法…"
                                        }, color = PlaceholderColor, fontSize = 15.sp)
                                    }
                                    inner()
                                }
                            )
                        }
                    }
                }
            }

            // 右 40%：垂直 7 步进度 + 主按钮 + 辅助按钮
            Column(modifier = Modifier.weight(0.4f).fillMaxHeight().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StepProgress(currentStep = stepIndex, totalSteps = 7, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { showHintBubble = !showHintBubble }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (showHintBubble) "收起提示" else "显示提示", fontSize = 14.sp)
                }
                OutlinedButton(onClick = { showScratchpad = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("召唤草稿纸", fontSize = 14.sp)
                }
                VoiceButton(
                    onResult = { t ->
                                    val s = answerField.text
                                    val newText = if (s.isBlank()) t else "$s $t"
                                    answerField = TextFieldValue(newText, TextRange(newText.length))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerField.text, checklistLocalOk) { answerField = TextFieldValue("", TextRange(0)) } },
                    enabled = canGoNext(stepIndex, questState, checklistLocalOk),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBg, disabledContainerColor = Color(0xFF9CA3AF)),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    if (questState.gradingState is StepGradingState.Grading) {
                        GradingIndicator(fontSize = 14.sp)
                    } else {
                        Text(mainButtonLabel(stepIndex, isLastStep, questState), color = GoldText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                }
            }
        }
        // 底部全宽键盘
        AnimatedVisibility(visible = keyboardExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                NumberKeyboard(
                                onDigit = { d ->
                                    val cur = answerField.selection.start
                                    val txt = answerField.text
                                    val newText = txt.substring(0, cur) + d + txt.substring(cur)
                                    answerField = TextFieldValue(newText, TextRange(cur + 1))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },
                                onOperator = { op ->
                                    val cur = answerField.selection.start
                                    val txt = answerField.text
                                    val ins = if (txt.isEmpty() || cur == 0) op else " $op "
                                    val newText = txt.substring(0, cur) + ins + txt.substring(cur)
                                    answerField = TextFieldValue(newText, TextRange(cur + ins.length))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },
                                onBackspace = {
                                    val sel = answerField.selection
                                    val s = answerField.text
                                    val (newText, newCur) = if (!sel.collapsed) {
                                        s.removeRange(sel.min, sel.max) to sel.min
                                    } else if (sel.start > 0) {
                                        val idx = sel.start - 1
                                        s.removeRange(idx, sel.start) to idx
                                    } else {
                                        s to 0
                                    }
                                    answerField = TextFieldValue(newText, TextRange(newCur))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { keyboardExpanded = false }) { Text("收起键盘") }
                }
            }
        }
    }

    if (showImageZoom && question.image.isNotBlank()) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showImageZoom = false }) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { showImageZoom = false }, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = question.image,
                    contentDescription = "题目配图放大",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(20.dp)
                )
            }
        }
    }
    if (showScratchpad) {
        ScratchpadDialog(onDismiss = { showScratchpad = false })
    }
}
