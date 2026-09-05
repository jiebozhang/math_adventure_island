package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.Dimens

@Composable
fun HomeScreen(
    userSettings: UserSettings,
    allQuestions: List<Question>,
    masteredQuestions: List<MasteredQuestion>,
    wrongQuestions: List<WrongQuestion>,
    monsterStats: List<MonsterStats>,
    onContinueQuest: () -> Unit,
    onStartQuest: () -> Unit,
    onViewMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val masteredIds = masteredQuestions.map { it.questionId }.toSet()
    val firstUnmastered = allQuestions.firstOrNull { it.id !in masteredIds }
    val masteredInTopic = if (firstUnmastered != null) {
        allQuestions.count { it.topicId == firstUnmastered.topicId && it.id in masteredIds }
    } else 0
    val totalInTopic = if (firstUnmastered != null) {
        allQuestions.count { it.topicId == firstUnmastered.topicId }
    } else 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(Dimens.md)
    ) {
        Spacer(modifier = Modifier.height(Dimens.xs))
        UserCard(userSettings)
        ContinueCard(
            question = firstUnmastered,
            masteredCount = masteredInTopic,
            totalInTopic = totalInTopic,
            onContinue = onViewMap,
            onStart = onViewMap
        )
        DailyTaskCard(
            userSettings = userSettings,
            onTaskClick = { }
        )
        ThinkingStonesGrid(
            masteredCount = masteredQuestions.size,
            totalQuestions = allQuestions.size
        )
        Spacer(modifier = Modifier.height(Dimens.lg))
    }
}
