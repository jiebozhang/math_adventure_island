package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Constants
import com.example.data.model.Monster
import com.example.data.model.MonsterStats
import com.example.data.model.Question
import com.example.data.model.WrongQuestion
import com.example.ui.theme.*

private fun monsterIcon(iconName: String): ImageVector = when (iconName) {
    "visibility" -> Icons.Default.Visibility
    "calculate" -> Icons.Default.Calculate
    "menu_book" -> Icons.Default.MenuBook
    "square_foot" -> Icons.Default.SquareFoot
    "extension" -> Icons.Default.Extension
    "search" -> Icons.Default.Search
    else -> Icons.Default.Pets
}

private fun monsterColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(NavbarBlue)

@Composable
fun MonsterCodexScreen(
    monsterStats: List<MonsterStats>,
    wrongQuestions: List<WrongQuestion>,
    allQuestions: List<Question>
) {
    var selectedMonster by remember { mutableStateOf<Monster?>(null) }

    if (selectedMonster != null) {
        val m = selectedMonster!!
        val wrongsForMonster = wrongQuestions.filter { it.monsterId == m.id }
        val questionMap = remember(allQuestions) { allQuestions.associateBy { it.id } }

        AlertDialog(
            onDismissRequest = { selectedMonster = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(monsterIcon(m.iconName), contentDescription = null, tint = monsterColor(m.colorHex))
                    Text(m.name, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = CardYellow) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("训练秘籍", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(m.trainingTip, fontSize = 12.sp, color = TextDark)
                        }
                    }

                    Text("弱点题目记录：", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    if (wrongsForMonster.isEmpty()) {
                        Text("最近还没有被这只怪兽捣乱过，继续保持！", fontSize = 12.sp, color = SuccessGreen)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            wrongsForMonster.forEach { w ->
                                val q = questionMap[w.questionId]
                                val textSnippet = q?.text?.take(25) ?: "未知题目"
                                Text("• $textSnippet... （出现 ${w.failCount} 次）", fontSize = 12.sp, color = TextDark)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMonster = null }) {
                    Text("知道啦")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(16.dp)
    ) {
        Text(
            text = "怪兽图鉴",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )
        Text(
            text = "每一只怪兽代表一种错误原因，认识它们，就能打败它们！",
            fontSize = 13.sp,
            color = MutedGray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val statsMap = remember(monsterStats) { monsterStats.associateBy { it.monsterId } }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(Constants.MONSTERS.values.toList()) { monster ->
                val stat = statsMap[monster.id]
                val seen = stat?.seenCount ?: 0
                val defeated = stat?.defeatedCount ?: 0

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = monsterColor(monster.colorHex).copy(alpha = 0.14f)
                    ),
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMonster = monster }
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = monsterColor(monster.colorHex),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = monsterIcon(monster.iconName),
                                    contentDescription = monster.name,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Text(
                            text = monster.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        Text(
                            text = monster.desc,
                            fontSize = 12.sp,
                            color = TextDark.copy(alpha = 0.8f)
                        )

                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "遇见 $seen 次 / 打退 $defeated 次",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedGray,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = "点击查看秘籍",
                            fontSize = 12.sp,
                            color = NavbarBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
