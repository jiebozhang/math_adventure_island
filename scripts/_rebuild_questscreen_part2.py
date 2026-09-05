# -*- coding: utf-8 -*-
import io

p = 'app/src/main/java/com/example/ui/screens/QuestScreen.kt'
s = io.open(p, encoding='utf-8').read()

# ── QuestScreen (手机版) ──
part2 = u'''
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
    var answerText by remember(stepIndex, question.id) { mutableStateOf("") }
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

    Scaffold(
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
            StepProgress(current = stepIndex, total = 7, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))

            // 中部可滚动：题目 + AI 气泡 + 答题
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))

                // 题目卡片
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 题干
                        PinyinText(
                            text = question.story.ifBlank { question.text },
                            fontSize = 15.sp,
                            color = TextDark,
                            fontWeight = FontWeight.Medium,
                            maxLines = 5
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
                                value = answerText,
                                onValueChange = { v ->
                                    answerText = v
                                    viewModel.setUserAnswer("step_$stepIndex", v)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
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
                            answerText = if (answerText.isBlank()) key else "$answerText $key "
                            viewModel.setUserAnswer("step_$stepIndex", answerText)
                        }) { Box(contentAlignment = Alignment.Center) { Text(key, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) } }
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF6B6258), modifier = Modifier.weight(1f).height(36.dp).clickable {
                        answerText = if (answerText.isBlank()) ""
                            else {
                            val ops = listOf(" + ", " - ", " × ", " ÷ ")
                            val matched = ops.firstOrNull { answerText.endsWith(it) }
                            if (matched != null) answerText.dropLast(matched.length).trimEnd() else answerText.dropLast(1)
                        }
                        viewModel.setUserAnswer("step_$stepIndex", answerText)
                    }) { Box(contentAlignment = Alignment.Center) { Text("删除", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } }
                }
                AnimatedVisibility(visible = keyboardExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
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
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 操作行：草稿 / 语音 / 键盘 / 主按钮
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFAF6F0), modifier = Modifier.weight(1f).height(40.dp).clickable { showScratchpad = true }) {
                        Box(contentAlignment = Alignment.Center) { Text("草稿", fontSize = 13.sp, color = TextDark) }
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFAF6F0), modifier = Modifier.weight(1f).height(40.dp).clickable { keyboardExpanded = !keyboardExpanded }) {
                        Box(contentAlignment = Alignment.Center) { Text(if (keyboardExpanded) "收起" else "键盘", fontSize = 13.sp, color = TextDark) }
                    }
                    // 主按钮
                    Button(
                        onClick = { handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText, checklistLocalOk) { answerText = "" } },
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
'''

io.open(p, 'a', encoding='utf-8').write(part2)
print('part2 appended, total chars:', len(s) + len(part2))
