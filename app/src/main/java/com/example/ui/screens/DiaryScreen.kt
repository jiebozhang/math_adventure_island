package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*

@Composable
fun DiaryScreen(
    userSettings: UserSettings,
    diaryEntries: List<DiaryEntry>,
    wrongQuestions: List<WrongQuestion>,
    allQuestions: List<Question>,
    monsterStats: List<MonsterStats>,
    modifier: Modifier = Modifier
) {
    val totalDefeated = monsterStats.sumOf { it.defeatedCount }
    val successCount = diaryEntries.count { it.result == "success" }
    val failCount = diaryEntries.count { it.result == "fail" }
    val correctRate = if (diaryEntries.isNotEmpty()) successCount * 100 / diaryEntries.size else 0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overview
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NavbarBlue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("成长日记", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem("练习天数", "${userSettings.streakDays}", androidx.compose.ui.graphics.Color.White)
                        StatItem("答对率", "$correctRate%", androidx.compose.ui.graphics.Color.White)
                        StatItem("击败怪兽", "$totalDefeated", androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
        // Entries
        if (diaryEntries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDCD3", fontSize = 48.sp)
                        Text("还没有日记", fontSize = 16.sp, color = MutedGray)
                        Text("开始闯关后，你的成长记录会出现在这里", fontSize = 12.sp, color = MutedGray)
                    }
                }
            }
        } else {
            items(diaryEntries, key = { it.id }) { entry ->
                val isSuccess = entry.result == "success"
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (isSuccess) SuccessGreen else DangerRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.topicTitle.ifBlank { "练习" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                            if (entry.note.isNotBlank()) {
                                Text(
                                    entry.note,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(entry.date, fontSize = 11.sp, color = MutedGray)
                                if (entry.score != null) {
                                    Text("\u2B50".repeat(entry.score), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = color.copy(alpha = 0.8f))
    }
}
