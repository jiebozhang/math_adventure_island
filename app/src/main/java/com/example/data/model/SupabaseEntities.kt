package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_topics")
data class SyncTopicEntity(
    @PrimaryKey val id: String,
    val grade: Int,
    val strand: String,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncedAt: String = ""
)

@Entity(tableName = "sync_questions")
data class SyncQuestionEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val grade: Int,
    val difficulty: Int,
    val qtype: String,
    val stemText: String,
    val layoutJson: String,
    val correctAnswerJson: String,
    val explanation: String = "",
    val story: String = "",
    val methodHint: String = "",
    val hiddenTrapsJson: String = "[]",
    val referenceJson: String = "{}",
    val source: String = "manual",
    val aiGenerated: Boolean = false,
    val reviewStatus: String = "approved",
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncedAt: String = ""
)

@Entity(tableName = "sync_question_assets")
data class SyncQuestionAssetEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val role: String,
    val slotKey: String = "",
    val label: String = "",
    val assetType: String,
    val svgContent: String = "",
    val storagePath: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: String = "",
    val syncedAt: String = ""
)

@Entity(tableName = "sync_question_options")
data class SyncQuestionOptionEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val optionKey: String,
    val optionLabel: String = "",
    val optionAssetId: String = "",
    val sortOrder: Int = 0,
    val syncedAt: String = ""
)

@Entity(tableName = "sync_progress")
data class SyncProgressEntity(
    @PrimaryKey val questionId: String,
    val remoteId: String = "",
    val userId: String = "",
    val status: String = "new",
    val userAnswerJson: String = "{}",
    val isCorrect: Boolean? = null,
    val attemptCount: Int = 0,
    val lastAttemptAt: String = "",
    val nextReviewAt: String = "",
    val updatedAt: String = "",
    val syncedAt: String = "",
    val pendingUpload: Boolean = false
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String
)

data class SyncQuestionBundle(
    val question: SyncQuestionEntity,
    val assets: List<SyncQuestionAssetEntity>,
    val options: List<SyncQuestionOptionEntity>
)

data class SyncUiState(
    val isSyncing: Boolean = false,
    val lastSyncAt: String = "",
    val lastSummary: String = "",
    val lastError: String = "",
    val available: Boolean = false
)
