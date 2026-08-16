package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Constants
import com.example.data.model.Monster
import com.example.data.model.MasteredQuestion
import com.example.data.model.Question
import com.example.data.model.UserSettings
import com.example.ui.components.PinyinText
import com.example.ui.components.ScratchpadDialog
import com.example.ui.components.VoiceInputButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.ActiveQuestState
import com.example.ui.viewmodel.MathViewModel
import com.example.util.TTSManager
import com.example.util.FeedbackSoundManager

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
    val question = questState.question ?: return

    val context = LocalContext.current
    var showScratchpad by remember { mutableStateOf(false) }
    var showHintDialog by remember { mutableStateOf(false) }
    var showFeynmanHint by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var soundPlayed by remember(question.id) { mutableStateOf(false) }
    var pinyinEnabled by remember { mutableStateOf(userSettings.pinyinEnabled) }

    val nextQuestion = remember(question.id, question.topicId, allQuestions, masteredQuestions) {
        val masteredIds = masteredQuestions.map { it.questionId }.toSet()
        allQuestions.firstOrNull { it.topicId == question.topicId && it.id != question.id && it.id !in masteredIds }
    }

    val ttsManager = remember { TTSManager(context) }
    LaunchedEffect(userSettings.ttsRate) {
        ttsManager.setSpeechRate(userSettings.ttsRate)
    }
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.stopSpeaking()
            ttsManager.shutdown()
        }
    }

    LaunchedEffect(questState.judgeResult) {
        val result = questState.judgeResult
        if (result != null && !soundPlayed && userSettings.soundEffectsEnabled) {
            FeedbackSoundManager.play(result.correct)
            soundPlayed = true
        }
    }

    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            kotlinx.coroutines.delay(200L)
            while (ttsManager.isSpeaking()) {
                kotlinx.coroutines.delay(100L)
            }
            isSpeaking = false
        }
    }

    if (showScratchpad) {
        ScratchpadDialog(onDismiss = { showScratchpad = false })
    }

    LaunchedEffect(question.id, questState.currentStep) {
        if (questState.currentStep == 0) {
            kotlinx.coroutines.delay(30_000L)
            if (questState.currentStep == 0) showFeynmanHint = true
        }
    }

    if (showHintDialog) {
        AlertDialog(
            onDismissRequest = { showHintDialog = false },
            title = { Text("小提示", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val traps = try {
                        org.json.JSONArray(question.hiddenTrapsJson)
                    } catch (e: Exception) {
                        org.json.JSONArray()
                    }
                    if (traps.length() == 0) {
                        Text("仔细读题，关注题目中的已知条件和关键词哦！")
                    } else {
                        for (i in 0 until traps.length()) {
                            Text("• ${traps.optString(i)}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHintDialog = false }) {
                    Text("我知道啦")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(12.dp)
            .imePadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onQuit,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("返回地图", fontSize = 12.sp)
                }

                TextButton(onClick = { showHintDialog = true }) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("提示", color = TextDark, fontSize = 12.sp)
                }

                TextButton(onClick = { showScratchpad = true }) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = TextDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("草稿纸", color = TextDark, fontSize = 12.sp)
                }

                TextButton(onClick = { pinyinEnabled = !pinyinEnabled }) {
                    Text("拼音:${if (pinyinEnabled) "开" else "关"}", fontSize = 12.sp, color = TextDark)
                }

                FilledTonalButton(
                    onClick = {
                        if (isSpeaking) {
                            ttsManager.stopSpeaking()
                            isSpeaking = false
                        } else {
                            ttsManager.speak("${question.story}。${question.text}")
                            isSpeaking = true
                        }
                    }
                ) {
                    Icon(if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSpeaking) "停止朗读" else "朗读题目", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Countdown Timer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                val mins = questState.timerSecondsLeft / 60
                val secs = questState.timerSecondsLeft % 60
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    color = MutedGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SevenStepProgress(
            currentStep = questState.currentStep,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        // Story Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardYellow,
            border = BorderStroke(1.dp, BorderGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                PinyinText(
                    text = if (questState.isReview) "【复习挑战】${question.story}" else question.story,
                    showPinyin = pinyinEnabled,
                    fontSize = 14.sp,
                    color = AccentOrange
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (questState.currentStep == 0 && showFeynmanHint) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBlue.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "试着填一下：题目告诉我们有 ${question.conditionsRef.ifBlank { "___" }}，想让我们求 ${question.questionRef.ifBlank { "___" }}？",
                    fontSize = 13.sp,
                    color = TextDark,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Step content follows the story and can share the single page scroll.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Panel (Step Content)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Step Title
                    Text(
                        text = "第${questState.currentStep + 1}步：${Constants.STEP_NAMES[questState.currentStep]}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Question Text
                    PinyinText(
                        text = question.text,
                        showPinyin = pinyinEnabled,
                        fontSize = 15.sp,
                        color = TextDark
                    )

                    if (question.image.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = question.image,
                            contentDescription = "题目配图",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step Renderers
                    when (questState.currentStep) {
                        0 -> Step1ReadView(questState, viewModel, pinyinEnabled)
                        1 -> Step2ConditionsView(questState, viewModel)
                        2 -> Step3QuestionView(questState, viewModel)
                        3 -> Step4MethodView(questState, viewModel, pinyinEnabled)
                        4 -> Step5FormulaView(questState, viewModel)
                        5 -> Step6CheckView(questState, viewModel)
                        6 -> Step7SummaryView(
                            state = questState,
                            viewModel = viewModel,
                            nextQuestion = nextQuestion,
                            onStartNextQuest = onStartNextQuest,
                            onNavigateTraining = onNavigateTraining,
                            onQuit = onQuit
                        )
                    }
                }
            }

        }
        }
    }
}

@Composable
private fun SevenStepProgress(currentStep: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Constants.STEP_NAMES.forEachIndexed { index, name ->
            val isDone = index < currentStep
            val isCurrent = index == currentStep
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = when {
                        isDone -> Icons.Default.CheckCircle
                        isCurrent -> Icons.Default.PlayCircle
                        else -> Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = "第 ${index + 1} 步：$name",
                    tint = when {
                        isDone -> SuccessGreen
                        isCurrent -> NavbarBlue
                        else -> MutedGray
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${index + 1}",
                    fontSize = 10.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) NavbarBlue else MutedGray
                )
            }
        }
    }
}

@Composable
private fun Step1ReadView(
    state: ActiveQuestState,
    viewModel: MathViewModel,
    pinyinEnabled: Boolean
) {
    var inputText by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // AI Dialogue Bubbles
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.step1Conv.messages.forEach { (role, msg) ->
                val isAi = role == "ai"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAi) CardBlue else CardPurple,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            color = TextDark,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        if (state.isAiThinking) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(state.aiStatusText, fontSize = 12.sp, color = MutedGray)
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("试着用一两句话讲讲题目大概在说什么吧...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoiceInputButton(
                onResult = { inputText = (inputText + " " + it).trim() },
                modifier = Modifier.weight(1f, fill = false)
            )
            Button(
                onClick = {
                    if (state.step1Conv.passed) {
                        viewModel.nextStep()
                    } else if (inputText.isNotBlank()) {
                        viewModel.sendStep1FeynmanUserMessage(inputText) {
                            inputText = ""
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
            ) {
                Text(if (state.step1Conv.passed) "下一步" else "发送", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Step2ConditionsView(state: ActiveQuestState, viewModel: MathViewModel) {
    var text by remember { mutableStateOf(state.userAnswers["conditions"] ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = CardBlue) {
            Text("题目里藏着哪些已知条件呢？把它们写下来吧～", fontSize = 13.sp, color = TextDark, modifier = Modifier.padding(10.dp))
        }

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                viewModel.setUserAnswer("conditions", it)
            },
            placeholder = { Text("例如：第一排358，第二排比第一排多476") },
            modifier = Modifier.fillMaxWidth()
        )

        VoiceInputButton(onResult = {
            text = (text + " " + it).trim()
            viewModel.setUserAnswer("conditions", text)
        })

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { viewModel.prevStep() }) { Text("上一步") }
            Button(
                onClick = { if (text.isNotBlank()) viewModel.nextStep() },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
            ) {
                Text("下一步", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Step3QuestionView(state: ActiveQuestState, viewModel: MathViewModel) {
    var text by remember { mutableStateOf(state.userAnswers["question"] ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = CardBlue) {
            Text("那这道题最后是要我们求什么呢？", fontSize = 13.sp, color = TextDark, modifier = Modifier.padding(10.dp))
        }

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                viewModel.setUserAnswer("question", it)
            },
            placeholder = { Text("例如：求两排密码合在一起是多少") },
            modifier = Modifier.fillMaxWidth()
        )

        VoiceInputButton(onResult = {
            text = (text + " " + it).trim()
            viewModel.setUserAnswer("question", text)
        })

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { viewModel.prevStep() }) { Text("上一步") }
            Button(
                onClick = { if (text.isNotBlank()) viewModel.nextStep() },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
            ) {
                Text("下一步", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Step4MethodView(state: ActiveQuestState, viewModel: MathViewModel, pinyinEnabled: Boolean) {
    var inputText by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Method Quick Pick Buttons
        Text("快速选择解题方法：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Constants.METHOD_OPTIONS.forEach { method ->
                FilterChip(
                    selected = inputText.contains(method),
                    onClick = { inputText = "我要用${method}，因为" },
                    label = { Text(method, fontSize = 11.sp) }
                )
            }
        }

        // AI Dialogue Bubbles
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.step4Conv.messages.forEach { (role, msg) ->
                val isAi = role == "ai"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAi) CardBlue else CardPurple,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(text = msg, fontSize = 13.sp, color = TextDark, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        }

        if (state.isAiThinking) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(state.aiStatusText, fontSize = 12.sp, color = MutedGray)
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("说说你打算用什么方法以及理由...") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { viewModel.prevStep() }) { Text("上一步") }
            Button(
                onClick = {
                    viewModel.setUserAnswer("method", inputText)
                    viewModel.nextStep()
                },
                enabled = inputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
            ) {
                Text(if (state.step4Conv.passed) "下一步" else "发送", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Step5FormulaView(state: ActiveQuestState, viewModel: MathViewModel) {
    var text by remember { mutableStateOf(state.userAnswers["formula_answer"] ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = CardBlue) {
            Text("把你的算式和最终答案写下来吧，可以写过程。例如：358 + 476 = 834", fontSize = 13.sp, color = TextDark, modifier = Modifier.padding(10.dp))
        }

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                viewModel.setUserAnswer("formula_answer", it)
            },
            placeholder = { Text("算式与答案...") },
            modifier = Modifier.fillMaxWidth()
        )

        VoiceInputButton(onResult = {
            text = (text + " " + it).trim()
            viewModel.setUserAnswer("formula_answer", text)
        })

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { viewModel.prevStep() }) { Text("上一步") }
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        viewModel.nextStep()
                    }
                },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
            ) {
                Text("下一步", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Step6CheckView(state: ActiveQuestState, viewModel: MathViewModel) {
    var check1 by remember { mutableStateOf(false) }
    var check2 by remember { mutableStateOf(false) }
    var check3 by remember { mutableStateOf(false) }
    var check4 by remember { mutableStateOf(false) }

    val checkedCount = listOf(check1, check2, check3, check4).count { it }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = CardBlue) {
            Text("检查怪最讨厌认真检查的勇者了！勾选至少2项你已经检查过的项目：", fontSize = 13.sp, color = TextDark, modifier = Modifier.padding(10.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = check1, onCheckedChange = { check1 = it })
            Text("数字有没有抄错？", fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = check2, onCheckedChange = { check2 = it })
            Text("单位对不对？", fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = check3, onCheckedChange = { check3 = it })
            Text("有没有漏看条件？", fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = check4, onCheckedChange = { check4 = it })
            Text("算式和答案对得上吗？", fontSize = 13.sp)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { viewModel.prevStep() }) { Text("上一步") }
            Button(
                onClick = { viewModel.nextStep() },
                enabled = checkedCount >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
            ) {
                Text("提交答案", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Step7SummaryView(
    state: ActiveQuestState,
    viewModel: MathViewModel,
    nextQuestion: Question?,
    onStartNextQuest: (Question) -> Unit,
    onNavigateTraining: () -> Unit,
    onQuit: () -> Unit
) {
    val result = state.judgeResult
    var noteText by remember { mutableStateOf("") }
    var selectedScore by remember { mutableStateOf(5) }
    var selectedMonsterId by remember { mutableStateOf<String?>(null) }
    var showCaptureAnimation by remember { mutableStateOf(false) }

    if (state.isAiThinking) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(state.aiStatusText, color = MutedGray)
        }
        return
    }

    if (result == null) return

    if (showCaptureAnimation) {
        val progress by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800),
            label = "monster-capture"
        )
        LaunchedEffect(showCaptureAnimation) {
            kotlinx.coroutines.delay(800L)
            selectedMonsterId?.let { monsterId ->
                viewModel.submitWrongQuestSummary(monsterId) {
                    if (nextQuestion != null) onStartNextQuest(nextQuestion) else onNavigateTraining()
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = "捕获怪兽",
                tint = DangerRed,
                modifier = Modifier
                    .size((44 - progress * 18).dp)
                    .offset(x = (progress * 120).dp, y = (-progress * 24).dp)
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (result.correct) {
            // Correct Answer Section
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
                Text("太棒了！你打退了这只怪兽！", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
            }

            Text("正确答案：${state.question?.answer}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text("判题方式：${result.reason}", fontSize = 12.sp, color = MutedGray)

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("这次你觉得学到了什么方法？（可选）") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("给自己打几分？", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { star ->
                    IconButton(onClick = { selectedScore = star }) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "$star 星",
                            tint = if (star <= selectedScore) AccentAmber else MutedGray
                        )
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.submitCorrectQuestSummary(noteText, selectedScore) { rewardClaimedNow ->
                        if (nextQuestion != null) onStartNextQuest(nextQuestion) else onNavigateTraining()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (nextQuestion != null) "继续挑战下一题" else "扫清本区域，去粗心训练营",
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = {
                    viewModel.submitCorrectQuestSummary(noteText, selectedScore) { onQuit() }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("休息一下，返回地图")
            }
        } else {
            // Wrong Answer Section
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = DangerRed, modifier = Modifier.size(32.dp))
                Text("哎呀，怪兽跑掉了，我们再想想！", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DangerRed)
            }

            Text("正确答案：${state.question?.answer}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text("你觉得刚才是哪只怪兽在捣乱？", fontSize = 14.sp, fontWeight = FontWeight.Bold)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(180.dp)) {
                items(Constants.MONSTERS.values.toList()) { monster ->
                    val isSelected = selectedMonsterId == monster.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AccentAmber else CardPink
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMonsterId = monster.id }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = monster.name,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else TextDark
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = monster.desc,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White else MutedGray
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    selectedMonsterId?.let { mId ->
                        showCaptureAnimation = true
                    }
                },
                enabled = selectedMonsterId != null && !showCaptureAnimation,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (nextQuestion != null) "继续挑战下一题" else "去粗心训练营复习旧怪兽",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = { onQuit() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("休息一下，返回地图")
            }
        }
    }
}
