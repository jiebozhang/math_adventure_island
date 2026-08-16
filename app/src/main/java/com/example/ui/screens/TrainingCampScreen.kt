package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Constants
import com.example.data.model.Question
import com.example.data.model.WrongQuestion
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TrainingCampScreen(
    wrongQuestions: List<WrongQuestion>,
    allQuestions: List<Question>,
    onStartReviewQuest: (Question) -> Unit
) {
    val context = LocalContext.current
    val questionMap = remember(allQuestions) { allQuestions.associateBy { it.id } }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "粗心训练营",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "怪兽卷土重来，间隔3天再打败它们一次，才算真正掌握！",
                    fontSize = 12.sp,
                    color = MutedGray
                )
            }

            if (wrongQuestions.isNotEmpty()) {
                OutlinedButton(
                    onClick = { exportWrongQuestionsText(context, wrongQuestions, questionMap) }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "导出", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导出错题卷")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (wrongQuestions.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "目前没有需要复习的错题，太棒了！",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(wrongQuestions) { wrong ->
                    val question = questionMap[wrong.questionId]
                    if (question != null) {
                        val monster = Constants.MONSTERS[wrong.monsterId]
                        val isDue = wrong.nextReviewDate.isBlank() || wrong.nextReviewDate <= today

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isDue) CardYellow else Color.White),
                            border = BorderStroke(1.dp, BorderGray),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Pets,
                                            contentDescription = null,
                                            tint = DangerRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = monster?.name ?: "怪兽",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = DangerRed
                                        )
                                        Text(
                                            text = "（已失败 ${wrong.failCount} 次）",
                                            fontSize = 12.sp,
                                            color = MutedGray
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = question.text,
                                        fontSize = 13.sp,
                                        color = TextDark,
                                        maxLines = 2
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                if (isDue) {
                                    Button(
                                        onClick = { onStartReviewQuest(question) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text("重新挑战", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    Text(
                                        text = "${wrong.nextReviewDate} 解锁",
                                        fontSize = 12.sp,
                                        color = MutedGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exportWrongQuestionsText(
    context: Context,
    wrongs: List<WrongQuestion>,
    questionMap: Map<String, Question>
) {
    val sb = StringBuilder()
    sb.append("=== 数学冒险岛 · 今日错题卷 ===\n")
    sb.append("日期：${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}\n")
    sb.append("共 ${wrongs.size} 道题\n\n")

    wrongs.forEachIndexed { index, wrong ->
        val q = questionMap[wrong.questionId]
        if (q != null) {
            val monsterName = Constants.MONSTERS[wrong.monsterId]?.name ?: "怪兽"
            sb.append("第 ${index + 1} 题（$monsterName 出没过 ${wrong.failCount} 次）:\n")
            if (q.story.isNotBlank()) sb.append("故事: ${q.story}\n")
            sb.append("题目: ${q.text}\n")
            sb.append("我的答案: ____________\n\n")
        }
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "数学冒险岛错题卷")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "分享/导出错题卷"))
}
