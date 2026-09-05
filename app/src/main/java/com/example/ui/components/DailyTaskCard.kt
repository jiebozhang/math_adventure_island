package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserSettings
import com.example.data.model.Constants
import com.example.ui.theme.*

data class DailyTaskItem(
    val label: String,
    val current: Int,
    val goal: Int
)

@Composable
fun DailyTaskCard(
    userSettings: UserSettings,
    onTaskClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks = listOf(
        DailyTaskItem("答对 3 道题", userSettings.dailyCorrectCount, Constants.DAILY_TASK_GOALS["correct"] ?: 3),
        DailyTaskItem("击败 1 只怪兽", userSettings.dailyDefeatedCount, Constants.DAILY_TASK_GOALS["defeated"] ?: 1),
        DailyTaskItem("写下 1 条方法笔记", userSettings.dailyMethodNotesCount, Constants.DAILY_TASK_GOALS["notes"] ?: 1)
    )
    val doneCount = tasks.count { it.current >= it.goal }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("今日任务", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (doneCount == tasks.size) SuccessGreen.copy(alpha = 0.15f) else AccentAmber.copy(alpha = 0.2f)
                ) {
                    Text(
                        "$doneCount / ${tasks.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (doneCount == tasks.size) SuccessGreen else AccentOrange,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            tasks.forEachIndexed { index, task ->
                val done = task.current >= task.goal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (done) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (done) SuccessGreen else MutedGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        task.label,
                        fontSize = 14.sp,
                        color = if (done) MutedGray else TextDark,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${minOf(task.current, task.goal)} / ${task.goal}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done) SuccessGreen else MutedGray
                    )
                }
            }
        }
    }
}
