package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BreakDialog
import com.example.ui.components.BottomTabBar
import com.example.ui.components.ParentLockDialog
import com.example.ui.screens.*
import com.example.ui.theme.Dimens
import com.example.ui.theme.BgColor
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ProvideDimens
import com.example.ui.viewmodel.MathViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                ProvideDimens(windowSizeClass) {
                val viewModel: MathViewModel = viewModel()
                val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
                val allQuestions by viewModel.allQuestions.collectAsStateWithLifecycle()
                val wrongQuestions by viewModel.wrongQuestions.collectAsStateWithLifecycle()
                val masteredQuestions by viewModel.masteredQuestions.collectAsStateWithLifecycle()
                val diaryEntries by viewModel.diaryEntries.collectAsStateWithLifecycle()
                val monsterStats by viewModel.monsterStats.collectAsStateWithLifecycle()
                val questState by viewModel.questState.collectAsStateWithLifecycle()
                val syncUiState by viewModel.syncUiState.collectAsStateWithLifecycle()

                // Tab state
                var currentTab by remember { mutableStateOf("home") }
                // Sub-screen within 冒险 tab
                var adventureSubRoute by remember { mutableStateOf("home") }
                // Dialogs
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
                                currentTab = "parent"
                            } else {
                                Toast.makeText(this, "密码不正确", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomTabBar(
                            currentRoute = currentTab,
                            onNavigate = { route ->
                                if (route == "parent") {
                                    if (userSettings.parentLockEnabled && userSettings.parentPinHash.isNotBlank()) {
                                        showParentLockDialog = true
                                    } else {
                                        currentTab = "parent"
                                    }
                                } else {
                                    currentTab = route
                                    if (route == "home") adventureSubRoute = "home"
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
                        when (currentTab) {
                            "home" -> {
                                when (adventureSubRoute) {
                                    "home" -> HomeScreen(
                                        userSettings = userSettings,
                                        allQuestions = allQuestions,
                                        masteredQuestions = masteredQuestions,
                                        wrongQuestions = wrongQuestions,
                                        monsterStats = monsterStats,
                                        onContinueQuest = {
                                            val masteredIds = masteredQuestions.map { it.questionId }.toSet()
                                            val nextQ = allQuestions.firstOrNull { it.id !in masteredIds }
                                            if (nextQ != null) {
                                                viewModel.startQuest(nextQ)
                                                adventureSubRoute = "quest"
                                            }
                                        },
                                        onStartQuest = {
                                            adventureSubRoute = "map"
                                        },
                                        onViewMap = {
                                            adventureSubRoute = "map"
                                        }
                                    )

                                    "map" -> MapScreen(
                                        userSettings = userSettings,
                                        allQuestions = allQuestions,
                                        masteredQuestions = masteredQuestions,
                                        onStartQuest = { question ->
                                            viewModel.startQuest(question)
                                            adventureSubRoute = "quest"
                                        }
                                    )

                                    "quest" -> {
                                        val isWide = Dimens.current.isTwoPane
                                        if (isWide) {
                                            QuestScreenTablet(
                                                viewModel = viewModel,
                                                userSettings = userSettings,
                                                allQuestions = allQuestions,
                                                masteredQuestions = masteredQuestions,
                                                onQuit = {
                                                    viewModel.quitQuest()
                                                    adventureSubRoute = "map"
                                                },
                                                onStartNextQuest = { nextQuestion ->
                                                    viewModel.startQuest(nextQuestion)
                                                    adventureSubRoute = "quest"
                                                },
                                                onNavigateTraining = {
                                                    viewModel.quitQuest()
                                                    currentTab = "codex"
                                                }
                                            )
                                        } else {
                                            QuestScreen(
                                                viewModel = viewModel,
                                                userSettings = userSettings,
                                                allQuestions = allQuestions,
                                                masteredQuestions = masteredQuestions,
                                                onQuit = {
                                                    viewModel.quitQuest()
                                                    adventureSubRoute = "map"
                                                },
                                                onStartNextQuest = { nextQuestion ->
                                                    viewModel.startQuest(nextQuestion)
                                                    adventureSubRoute = "quest"
                                                },
                                                onNavigateTraining = {
                                                    viewModel.quitQuest()
                                                    currentTab = "codex"
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            "codex" -> {
                                if (questState.question != null) {
                                    val isWide = Dimens.current.isTwoPane
                                    if (isWide) {
                                        QuestScreenTablet(
                                            viewModel = viewModel,
                                            userSettings = userSettings,
                                            allQuestions = allQuestions,
                                            masteredQuestions = masteredQuestions,
                                            onQuit = {
                                                viewModel.quitQuest()
                                                currentTab = "codex"
                                            },
                                            onStartNextQuest = { nextQuestion ->
                                                viewModel.startQuest(nextQuestion)
                                            },
                                            onNavigateTraining = {
                                                viewModel.quitQuest()
                                            }
                                        )
                                    } else {
                                        QuestScreen(
                                            viewModel = viewModel,
                                            userSettings = userSettings,
                                            allQuestions = allQuestions,
                                            masteredQuestions = masteredQuestions,
                                            onQuit = {
                                                viewModel.quitQuest()
                                                currentTab = "codex"
                                            },
                                            onStartNextQuest = { nextQuestion ->
                                                viewModel.startQuest(nextQuestion)
                                            },
                                            onNavigateTraining = {
                                                viewModel.quitQuest()
                                            }
                                        )
                                    }
                                } else {
                                    MonsterCodexScreen(
                                        monsterStats = monsterStats,
                                        wrongQuestions = wrongQuestions,
                                        allQuestions = allQuestions,
                                        onStartReviewQuest = { question ->
                                            viewModel.startReviewQuest(question) {
                                                // Stay on codex tab but show quest
                                            }
                                        }
                                    )
                                }
                            }

                            "diary" -> DiaryScreen(
                                userSettings = userSettings,
                                diaryEntries = diaryEntries,
                                wrongQuestions = wrongQuestions,
                                allQuestions = allQuestions,
                                monsterStats = monsterStats
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
}
