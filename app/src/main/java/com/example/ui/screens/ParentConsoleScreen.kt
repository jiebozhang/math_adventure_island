package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Constants
import com.example.data.model.Question
import com.example.data.model.UserSettings
import com.example.ui.theme.*
import com.example.ui.viewmodel.MathViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentConsoleScreen(
    userSettings: UserSettings,
    allQuestions: List<Question>,
    viewModel: MathViewModel
) {
    val context = LocalContext.current

    var timerMinutes by remember(userSettings) { mutableStateOf(userSettings.timerMinutes.toFloat()) }
    var pinyinEnabled by remember(userSettings) { mutableStateOf(userSettings.pinyinEnabled) }
    var autoVoice by remember(userSettings) { mutableStateOf(userSettings.autoVoice) }
    var ttsRate by remember(userSettings) { mutableStateOf(userSettings.ttsRate.toFloat()) }
    var llmJudgeEnabled by remember(userSettings) { mutableStateOf(userSettings.llmJudgeEnabled) }
    var llmProvider by remember(userSettings) { mutableStateOf(userSettings.llmProvider) }
    var llmApiBase by remember(userSettings) { mutableStateOf(userSettings.llmApiBase) }
    var llmModel by remember(userSettings) { mutableStateOf(userSettings.llmModel) }
    var llmApiKey by remember(userSettings) { mutableStateOf(userSettings.llmApiKey) }
    var llmTimeoutSeconds by remember(userSettings) { mutableStateOf(userSettings.llmTimeoutSeconds.toFloat()) }
    var showApiKey by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    var parentLockEnabled by remember(userSettings) { mutableStateOf(userSettings.parentLockEnabled) }
    var speechEngine by remember(userSettings) { mutableStateOf(userSettings.speechEngine) }
    var speechPauseThreshold by remember(userSettings) { mutableStateOf(userSettings.speechPauseThreshold) }
    var soundEffectsEnabled by remember(userSettings) { mutableStateOf(userSettings.soundEffectsEnabled) }
    var speechEngineExpanded by remember { mutableStateOf(false) }

    var showPinDialog by remember { mutableStateOf(false) }
    var newPinText by remember { mutableStateOf("") }

    // AI Generation state
    var selectedTopicId by remember { mutableStateOf(Constants.KNOWLEDGE_MAP[0].id) }
    var aiGenCount by remember { mutableStateOf("5") }
    var aiGenPrompt by remember { mutableStateOf("围绕进位加法生成故事趣味题目") }
    var isAiGenerating by remember { mutableStateOf(false) }

    // Add Custom Question state
    var customStory by remember { mutableStateOf("") }
    var customText by remember { mutableStateOf("") }
    var customAnswer by remember { mutableStateOf("") }
    var customMethodHint by remember { mutableStateOf("") }
    var customTraps by remember { mutableStateOf("") }
    var customImagePath by remember { mutableStateOf("") }
    var jsonImportText by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val imageDir = File(context.filesDir, "question_images").apply { mkdirs() }
                val extension = context.contentResolver.getType(uri)?.substringAfterLast('/')?.let { ".${it.lowercase()}" } ?: ".img"
                val target = File(imageDir, "question_${System.currentTimeMillis()}$extension")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                customImagePath = target.absolutePath
            }.onFailure {
                Toast.makeText(context, "图片读取失败，可以稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("设置/修改家长密码", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请输入新的4位以上数字/字符密码：", fontSize = 13.sp)
                    OutlinedTextField(
                        value = newPinText,
                        onValueChange = { newPinText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinText.length < 4) {
                            Toast.makeText(context, "密码至少需要4位", Toast.LENGTH_SHORT).show()
                        } else {
                            val hash = viewModel.repository.hashPin(newPinText)
                            viewModel.updateUserSettings { it.copy(parentLockEnabled = true, parentPinHash = hash) }
                            parentLockEnabled = true
                            showPinDialog = false
                            Toast.makeText(context, "密码设置成功！", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "家长控制台",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        // 1. Focus Timer & Lock Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBlue),
            border = BorderStroke(1.dp, BorderGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("结界专注时长与安全", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)

                Text("结界时长：${timerMinutes.toInt()} 分钟", fontSize = 14.sp)
                Slider(
                    value = timerMinutes,
                    onValueChange = { timerMinutes = it },
                    valueRange = 5f..60f,
                    steps = 11,
                    onValueChangeFinished = {
                        viewModel.updateUserSettings { it.copy(timerMinutes = timerMinutes.toInt()) }
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开启家长控制台密码锁", fontSize = 14.sp)
                    Switch(
                        checked = parentLockEnabled,
                        onCheckedChange = { checked ->
                            if (checked && userSettings.parentPinHash.isBlank()) {
                                showPinDialog = true
                            } else {
                                parentLockEnabled = checked
                                viewModel.updateUserSettings { it.copy(parentLockEnabled = checked) }
                            }
                        }
                    )
                }

                OutlinedButton(
                    onClick = { showPinDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("设置/修改密码")
                }
            }
        }

        // 2. Audio & Pinyin Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardPurple),
            border = BorderStroke(1.dp, BorderGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("拼音与朗读设置", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("题目上方显示拼音标注", fontSize = 14.sp)
                    Switch(
                        checked = pinyinEnabled,
                        onCheckedChange = {
                            pinyinEnabled = it
                            viewModel.updateUserSettings { s -> s.copy(pinyinEnabled = it) }
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("进入闯关时自动朗读题目", fontSize = 14.sp)
                    Switch(
                        checked = autoVoice,
                        onCheckedChange = {
                            autoVoice = it
                            viewModel.updateUserSettings { s -> s.copy(autoVoice = it) }
                        }
                    )
                }

                Text("朗读语速: ${ttsRate.toInt()}", fontSize = 14.sp)
                Slider(
                    value = ttsRate,
                    onValueChange = { ttsRate = it },
                    valueRange = 100f..200f,
                    onValueChangeFinished = {
                        viewModel.updateUserSettings { s -> s.copy(ttsRate = ttsRate.toInt()) }
                    }
                )

                ExposedDropdownMenuBox(
                    expanded = speechEngineExpanded,
                    onExpandedChange = { speechEngineExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (speechEngine == "google_free") "系统语音识别" else "设备默认识别",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("语音识别引擎") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = speechEngineExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = speechEngineExpanded,
                        onDismissRequest = { speechEngineExpanded = false }
                    ) {
                        listOf("google_free" to "系统语音识别", "device_default" to "设备默认识别").forEach { (id, title) ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = {
                                    speechEngine = id
                                    speechEngineExpanded = false
                                    viewModel.updateUserSettings { s -> s.copy(speechEngine = id) }
                                }
                            )
                        }
                    }
                }

                Text("语音断句停顿：${"%.1f".format(speechPauseThreshold)} 秒", fontSize = 14.sp)
                Slider(
                    value = speechPauseThreshold,
                    onValueChange = { speechPauseThreshold = it },
                    valueRange = 0.5f..3f,
                    onValueChangeFinished = {
                        viewModel.updateUserSettings { s -> s.copy(speechPauseThreshold = speechPauseThreshold) }
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("播放通关音效", fontSize = 14.sp)
                    Switch(
                        checked = soundEffectsEnabled,
                        onCheckedChange = {
                            soundEffectsEnabled = it
                            viewModel.updateUserSettings { s -> s.copy(soundEffectsEnabled = it) }
                        }
                    )
                }
            }
        }

        // 3. Gemini AI Settings
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardYellow),
            border = BorderStroke(1.dp, BorderGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI 智能判题 & 辅导配置", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("启用 AI 智能判题与费曼对话", fontSize = 14.sp)
                    Switch(
                        checked = llmJudgeEnabled,
                        onCheckedChange = {
                            llmJudgeEnabled = it
                            viewModel.updateUserSettings { s -> s.copy(llmJudgeEnabled = it) }
                        }
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (llmProvider) {
                            "anthropic" -> "Anthropic 原生协议"
                            else -> "OpenAI 兼容协议"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("接口类型") },
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("OpenAI 兼容协议") },
                            onClick = {
                                llmProvider = "openai_compatible"
                                providerExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Anthropic 原生协议") },
                            onClick = {
                                llmProvider = "anthropic"
                                providerExpanded = false
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = llmApiBase,
                    onValueChange = { llmApiBase = it },
                    label = { Text("API Base（支持自定义接口地址）") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = llmModel,
                    onValueChange = { llmModel = it },
                    label = { Text("模型名称 (如 gemini-3.5-flash)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = llmApiKey,
                    onValueChange = { llmApiKey = it },
                    label = { Text("API Key (为空则使用默认系统 Secrets)") },
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "切换显示"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("请求超时时间：${llmTimeoutSeconds.toInt()} 秒", fontSize = 14.sp)
                Slider(
                    value = llmTimeoutSeconds,
                    onValueChange = { llmTimeoutSeconds = it },
                    valueRange = 5f..120f,
                    steps = 22
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.updateUserSettings { s ->
                                s.copy(
                                    llmJudgeEnabled = llmJudgeEnabled,
                                    llmProvider = llmProvider,
                                    llmApiBase = llmApiBase.trim().trimEnd('/'),
                                    llmModel = llmModel,
                                    llmApiKey = llmApiKey.trim(),
                                    llmTimeoutSeconds = llmTimeoutSeconds.toInt()
                                )
                            }
                            Toast.makeText(context, "AI 配置已保存！", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("保存 AI 配置")
                    }

                    OutlinedButton(
                        onClick = {
                            val pendingSettings = userSettings.copy(
                                llmJudgeEnabled = llmJudgeEnabled,
                                llmProvider = llmProvider,
                                llmApiBase = llmApiBase.trim().trimEnd('/'),
                                llmModel = llmModel.trim(),
                                llmApiKey = llmApiKey.trim(),
                                llmTimeoutSeconds = llmTimeoutSeconds.toInt()
                            )
                            viewModel.testLlmConnection(pendingSettings) { success, msg ->
                                Toast.makeText(context, if (success) "连接成功: $msg" else "失败: $msg", Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("测试连接")
                    }
                }
            }
        }

        // 4. AI Batch Question Generator
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardGreen),
            border = BorderStroke(1.dp, BorderGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI 批量生成新题目", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)

                var topicExpanded by remember { mutableStateOf(false) }
                val currentTopicTitle = Constants.KNOWLEDGE_MAP.firstOrNull { it.id == selectedTopicId }?.title ?: ""

                ExposedDropdownMenuBox(
                    expanded = topicExpanded,
                    onExpandedChange = { topicExpanded = it }
                ) {
                    OutlinedTextField(
                        value = currentTopicTitle,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("目标关卡") },
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = topicExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = topicExpanded,
                        onDismissRequest = { topicExpanded = false }
                    ) {
                        Constants.KNOWLEDGE_MAP.forEach { topic ->
                            DropdownMenuItem(
                                text = { Text(topic.title) },
                                onClick = {
                                    selectedTopicId = topic.id
                                    topicExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = aiGenPrompt,
                    onValueChange = { aiGenPrompt = it },
                    label = { Text("生成要求") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        isAiGenerating = true
                        val count = aiGenCount.toIntOrNull() ?: 5
                        viewModel.generateAiBatchQuestions(selectedTopicId, aiGenPrompt, count) { generated ->
                            isAiGenerating = false
                            Toast.makeText(context, "成功生成 $generated 道题目！", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isAiGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAiGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("生成中...")
                    } else {
                        Text("调用 AI 生成题目", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Add Custom Question
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("手动添加自定义题目", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)

                OutlinedTextField(
                    value = customStory,
                    onValueChange = { customStory = it },
                    label = { Text("故事引入 (如：粗心怪在果园里捣乱)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text("题目内容") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customAnswer,
                    onValueChange = { customAnswer = it },
                    label = { Text("正确答案 (数字)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customMethodHint,
                    onValueChange = { customMethodHint = it },
                    label = { Text("方法提示") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customTraps,
                    onValueChange = { customTraps = it },
                    label = { Text("易错点提示 (逗号分隔)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选择配图")
                    }
                    if (customImagePath.isNotBlank()) {
                        Text("图片已保存", color = SuccessGreen, fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = {
                        if (customText.isBlank() || customAnswer.isBlank()) {
                            Toast.makeText(context, "请填写题目与答案", Toast.LENGTH_SHORT).show()
                        } else {
                            val trapsList = customTraps.split("，", ",").map { it.trim() }.filter { it.isNotBlank() }
                            val newQ = Question(
                                id = "custom_${Random.nextInt(100000, 999999)}",
                                topicId = selectedTopicId,
                                story = customStory,
                                text = customText,
                                answer = customAnswer,
                                methodHint = customMethodHint,
                                hiddenTrapsJson = org.json.JSONArray(trapsList).toString(),
                                image = customImagePath,
                                isCustom = true
                            )
                            viewModel.addCustomQuestion(newQ) {
                                customStory = ""
                                customText = ""
                                customAnswer = ""
                                customMethodHint = ""
                                customTraps = ""
                                customImagePath = ""
                                Toast.makeText(context, "成功添加自定义题目！", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("保存自定义题目", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardGreen),
            border = BorderStroke(1.dp, BorderGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("JSON 批量导入题库", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                OutlinedTextField(
                    value = jsonImportText,
                    onValueChange = { jsonImportText = it },
                    label = { Text("题目 JSON 数组") },
                    placeholder = { Text("[{\"story\":\"...\",\"text\":\"...\",\"answer\":\"...\"}]") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.importQuestionsFromJson(jsonImportText) { count, error ->
                            Toast.makeText(
                                context,
                                error ?: "已导入 $count 道题目",
                                Toast.LENGTH_LONG
                            ).show()
                            if (error == null) jsonImportText = ""
                        }
                    },
                    enabled = jsonImportText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("导入 JSON 题库", fontWeight = FontWeight.Bold)
                }
            }
        }

        val customQuestions = allQuestions.filter { it.isCustom }
        if (customQuestions.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderGray),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("自定义题目管理", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                    customQuestions.forEach { question ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(question.text, modifier = Modifier.weight(1f), maxLines = 1)
                            TextButton(onClick = {
                                viewModel.deleteQuestion(question.id) {
                                    Toast.makeText(context, "题目及配图已删除", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("删除", color = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
