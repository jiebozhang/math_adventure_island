package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BreakDialog
import com.example.ui.components.NavBar
import com.example.ui.components.ParentLockDialog
import com.example.ui.screens.*
import com.example.ui.theme.BgColor
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MathViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: MathViewModel = viewModel()
                val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
                val allQuestions by viewModel.allQuestions.collectAsStateWithLifecycle()
                val wrongQuestions by viewModel.wrongQuestions.collectAsStateWithLifecycle()
                val masteredQuestions by viewModel.masteredQuestions.collectAsStateWithLifecycle()
                val diaryEntries by viewModel.diaryEntries.collectAsStateWithLifecycle()
                val monsterStats by viewModel.monsterStats.collectAsStateWithLifecycle()
                val questState by viewModel.questState.collectAsStateWithLifecycle()

                var currentRoute by remember { mutableStateOf("map") }
                var showParentLockDialog by remember { mutableStateOf(false) }
                var showBreakDialog by remember { mutableStateOf(false) }

                // Timer tick loop
                LaunchedEffect(questState.isTimerRunning, questState.timerSecondsLeft) {
                    if (questState.isTimerRunning && questState.timerSecondsLeft > 0) {
                        delay(1000L)
                        viewModel.updateTimerTick()
                        if (questState.timerSecondsLeft - 1 <= 0) {
                            showBreakDialog = true
                        }
                    }
                }

                if (showBreakDialog) {
                    BreakDialog(onDismiss = { showBreakDialog = false })
                }

                if (showParentLockDialog) {
                    ParentLockDialog(
                        onDismiss = { showParentLockDialog = false },
                        onConfirmPin = { rawPin ->
                            val isCorrect = viewModel.repository.verifyPin(userSettings, rawPin)
                            if (isCorrect) {
                                showParentLockDialog = false
                                currentRoute = "parent"
                            } else {
                                Toast.makeText(this, "密码不正确", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        NavBar(
                            currentRoute = currentRoute,
                            userSettings = userSettings,
                            monsterStats = monsterStats,
                            onNavigate = { route ->
                                if (route == "parent") {
                                    if (userSettings.parentLockEnabled && userSettings.parentPinHash.isNotBlank()) {
                                        showParentLockDialog = true
                                    } else {
                                        currentRoute = "parent"
                                    }
                                } else {
                                    currentRoute = route
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(BgColor)
                    ) {
                        when (currentRoute) {
                            "map" -> MapScreen(
                                userSettings = userSettings,
                                allQuestions = allQuestions,
                                masteredQuestions = masteredQuestions,
                                onStartQuest = { question ->
                                    viewModel.startQuest(question)
                                    currentRoute = "quest"
                                },
                                onNavigate = { route -> currentRoute = route }
                            )

                            "quest" -> QuestScreen(
                                viewModel = viewModel,
                                userSettings = userSettings,
                                allQuestions = allQuestions,
                                masteredQuestions = masteredQuestions,
                                onQuit = {
                                    viewModel.quitQuest()
                                    currentRoute = "map"
                                },
                                onStartNextQuest = { nextQuestion ->
                                    viewModel.startQuest(nextQuestion)
                                    currentRoute = "quest"
                                },
                                onNavigateTraining = {
                                    viewModel.quitQuest()
                                    currentRoute = "camp"
                                }
                            )

                            "codex" -> MonsterCodexScreen(
                                monsterStats = monsterStats,
                                wrongQuestions = wrongQuestions,
                                allQuestions = allQuestions
                            )

                            "diary" -> DiaryScreen(
                                userSettings = userSettings,
                                diaryEntries = diaryEntries,
                                wrongQuestions = wrongQuestions,
                                allQuestions = allQuestions,
                                monsterStats = monsterStats
                            )

                            "camp" -> TrainingCampScreen(
                                wrongQuestions = wrongQuestions,
                                allQuestions = allQuestions,
                                onStartReviewQuest = { question ->
                                    viewModel.startReviewQuest(question) {
                                        currentRoute = "quest"
                                    }
                                }
                            )

                            "parent" -> ParentConsoleScreen(
                                userSettings = userSettings,
                                allQuestions = allQuestions,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
