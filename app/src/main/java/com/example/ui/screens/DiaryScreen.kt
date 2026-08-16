package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.Constants
import com.example.data.model.DiaryEntry
import com.example.data.model.MonsterStats
import com.example.data.model.Question
import com.example.data.model.UserSettings
import com.example.data.model.WrongQuestion
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DiaryScreen(
    userSettings: UserSettings,
    diaryEntries: List<DiaryEntry>,
    wrongQuestions: List<WrongQuestion>,
    allQuestions: List<Question>,
    monsterStats: List<MonsterStats>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(16.dp)
    ) {
        Text(
            text = "成长档案",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "等级：Lv.${userSettings.level}   连胜：${userSettings.streakDays} 天",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AccentAmber
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Weakness Profile Card
        WeaknessProfileCard(wrongQuestions = wrongQuestions)

        OutlinedButton(
            onClick = { shareWrongQuestionHtml(context, allQuestions, wrongQuestions, monsterStats) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("导出今日/本周错题卷")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "学习足迹",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayEntries = diaryEntries.filter { it.date == today }
        val weekEntries = diaryEntries.filter { isCurrentWeek(it.date) }
        val weekCorrect = weekEntries.count { it.result == "success" }
        val weekRate = if (weekEntries.isEmpty()) 0 else weekCorrect * 100 / weekEntries.size
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBlue),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("每日 / 每周战报", fontWeight = FontWeight.Bold, color = TextDark)
                Text("今日完成 ${todayEntries.size} 题；本周正确率 $weekRate%（${weekEntries.size} 题）", fontSize = 13.sp, color = TextDark)
                Text("本周方法笔记 ${weekEntries.count { it.note.isNotBlank() }} 次 · 平均自评 ${weekEntries.mapNotNull { it.score }.averageOrZero()} 星", fontSize = 12.sp, color = MutedGray)
            }
        }

        if (diaryEntries.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("还没有战报，快去闯关吧！", color = MutedGray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(diaryEntries) { entry ->
                    DiaryEntryRow(entry = entry)
                }
            }
        }
    }
}

private fun isCurrentWeek(dateText: String): Boolean {
    return runCatching {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateText) ?: return false
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.WEEK_OF_YEAR) == target.get(Calendar.WEEK_OF_YEAR)
    }.getOrDefault(false)
}

private fun List<Int>.averageOrZero(): String =
    if (isEmpty()) "0.0" else "%.1f".format(Locale.getDefault(), average())

private fun shareWrongQuestionHtml(
    context: Context,
    allQuestions: List<Question>,
    wrongQuestions: List<WrongQuestion>,
    monsterStats: List<MonsterStats>
) {
    runCatching {
        val statsMap = monsterStats.associateBy { it.monsterId }
        val body = wrongQuestions.mapIndexed { index, wrong ->
            val question = allQuestions.firstOrNull { it.id == wrong.questionId } ?: return@mapIndexed ""
            val monster = Constants.MONSTERS[wrong.monsterId]
            val imageHtml = question.image.takeIf { it.isNotBlank() }?.let { path ->
                val file = File(path)
                if (file.isFile) {
                    val mime = when (file.extension.lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "application/octet-stream"
                    }
                    "<img src=\"data:$mime;base64,${Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)}\" />"
                } else ""
            } ?: ""
            val seen = statsMap[wrong.monsterId]?.seenCount ?: 0
            """
            <section><h2>${index + 1}. ${question.story.escapeHtml()}</h2>
            $imageHtml<p>${question.text.escapeHtml()}</p>
            <p>对应怪兽：${monster?.name ?: "未知怪兽"}（出现/练习 ${wrong.failCount} 次，累计见过 $seen 次）</p>
            <div class="answer">请在这里写出你的解答：<br/><br/><br/><br/></div></section>
            """.trimIndent()
        }.joinToString("\n")
        val html = """
            <!doctype html><html><head><meta charset="utf-8"><title>数学冒险岛错题卷</title>
            <style>body{font-family:sans-serif;max-width:800px;margin:24px auto;color:#222}section{page-break-inside:avoid;border-bottom:1px solid #ddd;padding:12px 0}img{max-width:100%;max-height:240px}.answer{min-height:120px;border-bottom:1px dashed #999}</style>
            </head><body><h1>数学冒险岛错题卷</h1>$body</body></html>
        """.trimIndent()
        val file = File(context.cacheDir, "math_adventure_wrong_questions.html")
        file.writeText(html, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "分享错题卷"))
    }.onFailure {
        Toast.makeText(context, "错题卷导出失败，请稍后重试", Toast.LENGTH_SHORT).show()
    }
}

private fun String.escapeHtml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

@Composable
private fun WeaknessProfileCard(wrongQuestions: List<WrongQuestion>) {
    val monsterCounts = wrongQuestions.groupBy { it.monsterId }
        .mapValues { entry -> entry.value.sumOf { it.failCount } }
        .toList()
        .sortedByDescending { it.second }

    if (monsterCounts.isEmpty()) return

    val topMonster = Constants.MONSTERS[monsterCounts.first().first] ?: return
    val topCount = monsterCounts.first().second

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardPurple),
        border = BorderStroke(1.dp, BorderGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Assessment, contentDescription = null, tint = NavbarBlue)
                Text(
                    text = "当前弱点画像",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "目前错题本里出现最多的是【${topMonster.name}】，一共 $topCount 次。",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextDark
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "训练秘籍：${topMonster.trainingTip}",
                fontSize = 12.sp,
                color = MutedGray
            )
        }
    }
}

@Composable
private fun DiaryEntryRow(entry: DiaryEntry) {
    val isSuccess = entry.result == "success"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSuccess) CardGreen else CardPink),
        border = BorderStroke(1.dp, BorderGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isSuccess) SuccessGreen else DangerRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${entry.date}  ${entry.topicTitle}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                if (entry.score != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${entry.score}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                        Icon(Icons.Default.Star, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (entry.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "心得：${entry.note}",
                    fontSize = 12.sp,
                    color = MutedGray
                )
            }
        }
    }
}
