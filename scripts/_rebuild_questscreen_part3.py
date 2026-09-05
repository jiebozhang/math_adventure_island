# -*- coding: utf-8 -*-
import io

p = 'app/src/main/java/com/example/ui/screens/QuestScreen.kt'
s_before = io.open(p, encoding='utf-8').read()

part3 = u'''

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
    var answerText by remember(stepIndex, question.id) { mutableStateOf("") }
    val checklistLocalOk = stepIndex != 5 || questState.checklistChecked.size >= MIN_CHECKLIST
    var showImageZoom by remember { mutableStateOf(false) }
    var showScratchpad by remember { mutableStateOf(false) }
    var showHintBubble by remember { mutableStateOf(true) }
    var keyboardExpanded by remember { mutableStateOf(false) }

    Scaffold(
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
                        PinyinText(text = question.story.ifBlank { question.text }, fontSize = 18.sp, color = TextDark, fontWeight = FontWeight.Medium, maxLines = 6)
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
                                value = answerText,
                                onValueChange = { v -> answerText = v; viewModel.setUserAnswer("step_$stepIndex", v) },
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
                StepProgress(current = stepIndex, total = 7, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { showHintBubble = !showHintBubble }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (showHintBubble) "收起提示" else "显示提示", fontSize = 14.sp)
                }
                OutlinedButton(onClick = { showScratchpad = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("召唤草稿纸", fontSize = 14.sp)
                }
                Button(
                    onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText, checklistLocalOk) { answerText = "" } },
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
                    onDigit = { d -> answerText += d; viewModel.setUserAnswer("step_$stepIndex", answerText) },
                    onDelete = {
                        val ops = listOf(" + ", " - ", " × ", " ÷ ")
                        val matched = ops.firstOrNull { answerText.endsWith(it) }
                        answerText = if (matched != null) answerText.dropLast(matched.length).trimEnd() else answerText.dropLast(1)
                        viewModel.setUserAnswer("step_$stepIndex", answerText)
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
'''

io.open(p, 'a', encoding='utf-8').write(part3)
print('part3 appended, total chars:', len(s_before) + len(part3))
