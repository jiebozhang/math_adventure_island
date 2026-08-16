package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Constants
import com.example.data.model.Question
import com.example.data.model.MasteredQuestion
import com.example.data.model.Topic
import com.example.data.model.UserSettings
import com.example.ui.theme.*

data class IslandCardData(
    val title: String,
    val desc: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color,
    val action: String // "review", "preview", "camp", "diary"
)

@Composable
fun MapScreen(
    userSettings: UserSettings,
    allQuestions: List<Question>,
    masteredQuestions: List<MasteredQuestion>,
    onStartQuest: (Question) -> Unit,
    onNavigate: (String) -> Unit
) {
    var viewState by remember { mutableStateOf("islands") } // "islands" or "topics"
    var currentGradeArea by remember { mutableStateOf("review") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(16.dp)
    ) {
        if (viewState == "islands") {
            Text(
                text = "欢迎回来，小数勇者！今天也要元气满满地闯关哦～",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Daily Task Card
            DailyTaskCard(userSettings = userSettings)

            Spacer(modifier = Modifier.height(16.dp))

            // Island Grid Cards
            val islands = listOf(
                IslandCardData("一二年级复习岛", "查漏补缺，练扎实", CardBlue, Icons.Default.MenuBook, Color(0xFF2E86C1), "review"),
                IslandCardData("三年级预习岛", "带着问题去预习", CardGreen, Icons.Default.AutoStories, Color(0xFF27AE60), "preview"),
                IslandCardData("粗心训练营", "打败遇到的变体题", CardYellow, Icons.Default.FitnessCenter, Color(0xFFF39C12), "camp"),
                IslandCardData("Boss挑战赛", "综合大关卡，敬请期待！", CardPink, Icons.Default.MilitaryTech, Color(0xFFC0392B), "boss"),
                IslandCardData("成长档案馆", "查看成长足迹", CardPurple, Icons.Default.Assessment, Color(0xFF8E44AD), "diary")
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(islands) { island ->
                    IslandCard(
                        island = island,
                        onClick = {
                            when (island.action) {
                                "review" -> {
                                    currentGradeArea = "review"
                                    viewState = "topics"
                                }
                                "preview" -> {
                                    currentGradeArea = "preview"
                                    viewState = "topics"
                                }
                                "camp" -> onNavigate("camp")
                                "diary" -> onNavigate("diary")
                            }
                        }
                    )
                }
            }
        } else {
            // Topics view
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                IconButton(onClick = { viewState = "islands" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = if (currentGradeArea == "review") "一二年级复习岛" else "三年级预习岛",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            val topics = Constants.KNOWLEDGE_MAP.filter { it.gradeArea == currentGradeArea }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(topics) { topic ->
                    TopicRowCard(
                        topic = topic,
                        questions = allQuestions.filter { it.topicId == topic.id },
                        masteredQuestions = masteredQuestions,
                        onStartQuest = onStartQuest
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyTaskCard(userSettings: UserSettings) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardYellow),
        border = BorderStroke(1.dp, BorderGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = AccentOrange.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "今日冒险任务",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TaskItemRow(
                desc = "完成 3 道题目",
                current = userSettings.dailyCorrectCount,
                goal = 3,
                icon = Icons.Default.Article
            )
            TaskItemRow(
                desc = "击败 1 只怪兽",
                current = userSettings.dailyDefeatedCount,
                goal = 1,
                icon = Icons.Default.Pets
            )
            TaskItemRow(
                desc = "总结 1 次数学方法",
                current = userSettings.dailyMethodNotesCount,
                goal = 1,
                icon = Icons.Default.Lightbulb
            )

            Spacer(modifier = Modifier.height(10.dp))

            val allDone = userSettings.dailyCorrectCount >= 3 &&
                    userSettings.dailyDefeatedCount >= 1 &&
                    userSettings.dailyMethodNotesCount >= 1

            val tipText = when {
                userSettings.dailyRewardClaimed -> "今天的奖励已经领过啦，明天继续加油！"
                allDone -> "三个任务都完成了！奖励已发放！"
                else -> "全部完成可以额外获得 ${Constants.DAILY_TASK_REWARD_EXP} 点经验"
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (allDone) SuccessGreen.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = tipText,
                    fontSize = 12.sp,
                    color = if (allDone) SuccessGreen else MutedGray,
                    fontWeight = if (allDone) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TaskItemRow(
    desc: String,
    current: Int,
    goal: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val isDone = current >= goal
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(
            imageVector = if (isDone) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (isDone) SuccessGreen else MutedGray,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "$desc（${minOf(current, goal)}/$goal）",
            fontSize = 13.sp,
            fontWeight = if (isDone) FontWeight.Medium else FontWeight.Normal,
            color = if (isDone) SuccessGreen else TextDark
        )
    }
}

@Composable
private fun IslandCard(
    island: IslandCardData,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = island.color),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = island.action != "boss") { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = island.iconColor,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = island.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = island.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            Text(
                text = island.desc,
                fontSize = 12.sp,
                color = TextDark.copy(alpha = 0.75f),
                lineHeight = 16.sp
            )

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Button(
                    onClick = onClick,
                    enabled = island.action != "boss",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentAmber,
                        disabledContainerColor = MutedGray.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (island.action == "boss") "未开启" else "进入岛屿",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicRowCard(
    topic: Topic,
    questions: List<Question>,
    masteredQuestions: List<MasteredQuestion>,
    onStartQuest: (Question) -> Unit
) {
    val masteredIds = remember(masteredQuestions) { masteredQuestions.mapTo(hashSetOf()) { it.questionId } }
    val masteredCount = questions.count { it.id in masteredIds }
    val totalCount = questions.size
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderGray),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = topic.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "核心概念：${topic.coreConcept}",
                    fontSize = 12.sp,
                    color = MutedGray
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "易错点：${topic.commonMistake}",
                    fontSize = 12.sp,
                    color = DangerRed
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "掌握 $masteredCount/$totalCount 题",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (masteredCount == totalCount && totalCount > 0) SuccessGreen else MutedGray
            )
            LinearProgressIndicator(
                progress = { if (totalCount == 0) 0f else masteredCount.toFloat() / totalCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = SuccessGreen,
                trackColor = BorderGray,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    if (questions.isNotEmpty()) {
                        onStartQuest(questions.shuffled().first())
                    }
                },
                enabled = questions.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("开始闯关", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
