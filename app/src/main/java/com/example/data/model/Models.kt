package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class Topic(
    val id: String,
    val title: String,
    val gradeArea: String = "", // "review" or "preview"，E.1 后已废弃，留着兼容老卡片
    val coreConcept: String = "",
    val commonMistake: String = "",
    // E.1: 三层结构（grade → semester → unit）字段
    val grade: Int = 3,           // 1-6
    val semester: Int = 1,        // 1=上册 / 2=下册
    val unitOrder: Int = 1,       // 该学期内的单元序号
    val unitType: String = "unit", // "unit" 正式单元 / "reading" 阅读拓展
    // v15: 领域（calc/figure/unit/wisdom）与思想印记
    val strand: String = "calc",
    val thinkingTag: String = "",
    val unitName: String = ""
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
    val qtype: String = "fill_blank",
    val layoutJson: String = "{}",
    val correctAnswerJson: String = "",
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

// E.2 辅助判断：question 是否是 text_answer 型 matching。
// 用于 UI 分支（题目预览区、Step5 答题区）和 ViewModel 判分分支（performTextAnswerMatching）。
fun Question.isTextAnswerMatching(): Boolean {
    if (qtype != "matching") return false
    return runCatching {
        org.json.JSONObject(correctAnswerJson).has("text_answer")
    }.getOrDefault(false)
}

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
