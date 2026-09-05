package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*

@Composable
fun MonsterCodexScreen(
    monsterStats: List<MonsterStats>,
    wrongQuestions: List<WrongQuestion>,
    allQuestions: List<Question>,
    onStartReviewQuest: (Question) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("图鉴", "错题本")

    val d = Dimens.current
    Column(modifier = modifier.fillMaxSize()) {
        // Segmented control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.screenPadding, vertical = d.sm),
            horizontalArrangement = Arrangement.Center
        ) {
            tabs.forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    label = { Text(label, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        if (selectedTab == 0) {
            MonsterGrid(monsterStats)
        } else {
            WrongBookList(
                wrongQuestions = wrongQuestions,
                allQuestions = allQuestions,
                onStartReviewQuest = onStartReviewQuest
            )
        }
    }
}

@Composable
private fun MonsterGrid(monsterStats: List<MonsterStats>) {
    val d = Dimens.current
    val statsMap = monsterStats.associateBy { it.monsterId }
    // 平板横屏下自动增加列数，避免卡片被拉得过宽
    LazyVerticalGrid(
        columns = GridCells.Fixed(d.codexColumns),
        contentPadding = PaddingValues(d.screenPadding),
        verticalArrangement = Arrangement.spacedBy(if (d.isTablet) 20.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(if (d.isTablet) 20.dp else 12.dp)
    ) {
        items(Constants.MONSTERS.values.toList(), key = { it.id }) { monster ->
            val stats = statsMap[monster.id]
            val seen = stats?.seenCount ?: 0
            val defeated = stats?.defeatedCount ?: 0
            val color = runCatching {
                Color(monster.colorHex.removePrefix("#").toLong(16) or 0xFF000000L)
            }.getOrDefault(MutedGray)
            Surface(
                shape = RoundedCornerShape(d.cardCornerRadius),
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(d.cardPaddingSmall),
                    verticalArrangement = Arrangement.spacedBy(d.xs)
                ) {
                    Text(monster.name, fontSize = d.bodyFontSize, fontWeight = FontWeight.Bold, color = color)
                    Text(
                        monster.desc,
                        fontSize = d.captionFontSize,
                        color = TextSecondary,
                        maxLines = if (d.isTablet) 3 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("见过 $seen", fontSize = d.microFontSize, color = MutedGray)
                        Text("击败 $defeated", fontSize = d.microFontSize, color = color, fontWeight = FontWeight.SemiBold)
                    }
                    if (monster.trainingTip.isNotBlank()) {
                        Text(
                            "\uD83C\uDFAF ${monster.trainingTip}",
                            fontSize = d.microFontSize,
                            color = TextSecondary,
                            maxLines = if (d.isTablet) 3 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WrongBookList(
    wrongQuestions: List<WrongQuestion>,
    allQuestions: List<Question>,
    onStartReviewQuest: (Question) -> Unit
) {
    val d = Dimens.current
    if (wrongQuestions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d.sm)) {
                Text("\uD83C\uDFC6", fontSize = if (d.isTablet) 72.sp else 48.sp)
                Text("错题本是空的！", fontSize = d.titleFontSize, fontWeight = FontWeight.Bold, color = TextDark)
                Text("答错题会自动记录在这里", fontSize = d.bodyFontSize, color = MutedGray)
            }
        }
        return
    }
    val questionMap = allQuestions.associateBy { it.id }
    val grouped = wrongQuestions.groupBy { it.monsterId }

    // 宽屏下错题本限宽居中，避免一行文字拉得太长影响阅读
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(d.screenPadding),
        verticalArrangement = Arrangement.spacedBy(d.sm)
    ) {
        grouped.forEach { (monsterId, wrongs) ->
            val monster = Constants.MONSTERS[monsterId]
            item {
                Text(
                    "${monster?.name ?: "怪兽"} · ${wrongs.size} 题",
                    fontSize = d.bodyFontSize,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(top = d.sm)
                )
            }
            items(wrongs, key = { it.questionId }) { wrong ->
                val question = questionMap[wrong.questionId]
                if (question != null) {
                    Surface(
                        shape = RoundedCornerShape(d.cardPaddingSmall),
                        color = SurfaceWhite,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartReviewQuest(question) }
                    ) {
                        Row(
                            modifier = Modifier.padding(d.cardPaddingSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(d.sm)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    question.story.ifBlank { question.text },
                                    fontSize = d.captionFontSize,
                                    color = TextDark,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("答错 ${wrong.failCount} 次", fontSize = d.microFontSize, color = DangerRed)
                            }
                            Icon(Icons.Default.ArrowForward, contentDescription = "复习", tint = AccentOrange, modifier = Modifier.size(d.iconSmall))
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(d.lg)) }
    }
}
