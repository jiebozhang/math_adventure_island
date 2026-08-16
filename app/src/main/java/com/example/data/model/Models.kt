package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class Topic(
    val id: String,
    val title: String,
    val gradeArea: String, // "review" or "preview"
    val coreConcept: String,
    val commonMistake: String
)

data class Monster(
    val id: String,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val desc: String,
    val trainingTip: String
)

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey val id: String,
    val topicId: String,
    val story: String,
    val text: String,
    val answer: String,
    val methodHint: String = "",
    val hiddenTrapsJson: String = "[]", // Stores List<String> as JSON string
    val conditionsRef: String = "",
    val questionRef: String = "",
    val image: String = "",
    val imageDesc: String = "",
    val isCustom: Boolean = false,
    val isAiVariant: Boolean = false
)

@Entity(tableName = "wrong_questions")
data class WrongQuestion(
    @PrimaryKey val questionId: String,
    val monsterId: String,
    val failCount: Int = 1,
    val nextReviewDate: String = ""
)

@Entity(tableName = "mastered_questions")
data class MasteredQuestion(
    @PrimaryKey val questionId: String
)

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val topicTitle: String,
    val result: String, // "success" or "fail"
    val note: String,
    val score: Int? = null
)

@Entity(tableName = "monster_stats")
data class MonsterStats(
    @PrimaryKey val monsterId: String,
    val seenCount: Int = 0,
    val defeatedCount: Int = 0
)

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,
    val level: Int = 1,
    val exp: Int = 0,
    val streakDays: Int = 1,
    val lastLoginDate: String = "",
    val timerMinutes: Int = 25,
    val immersiveMode: Boolean = false,
    val pinyinEnabled: Boolean = true,
    val autoVoice: Boolean = false,
    val ttsRate: Int = 150,
    val llmJudgeEnabled: Boolean = false,
    val llmProvider: String = "openai_compatible",
    val llmApiBase: String = "https://generativelanguage.googleapis.com/",
    val llmModel: String = "gemini-3.5-flash",
    val llmApiKey: String = "",
    val llmTimeoutSeconds: Int = 20,
    val speechInputEnabled: Boolean = true,
    val speechPauseThreshold: Float = 1.4f,
    val speechEngine: String = "google_free",
    val soundEffectsEnabled: Boolean = true,
    val parentLockEnabled: Boolean = false,
    val parentPinHash: String = "",
    val dailyTasksDate: String = "",
    val dailyCorrectCount: Int = 0,
    val dailyDefeatedCount: Int = 0,
    val dailyMethodNotesCount: Int = 0,
    val dailyRewardClaimed: Boolean = false
)
