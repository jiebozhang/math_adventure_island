package com.example.data.db

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions")
    fun getAllQuestions(): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    suspend fun getQuestionById(id: String): Question?

    @Query("SELECT * FROM questions WHERE topicId = :topicId")
    suspend fun getQuestionsByTopic(topicId: String): List<Question>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: Question)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun deleteQuestionById(id: String)
}

@Dao
interface SyncTopicDao {
    @Query("SELECT * FROM sync_topics")
    suspend fun getAll(): List<SyncTopicEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(topics: List<SyncTopicEntity>)
}

@Dao
interface SyncQuestionDao {
    @Query("SELECT * FROM sync_questions ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SyncQuestionEntity>

    @Query("SELECT * FROM sync_questions WHERE qtype = 'matching' ORDER BY updatedAt DESC")
    suspend fun getMatchingQuestions(): List<SyncQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(questions: List<SyncQuestionEntity>)
}

@Dao
interface SyncQuestionAssetDao {
    @Query("SELECT * FROM sync_question_assets WHERE questionId = :questionId ORDER BY sortOrder")
    suspend fun getByQuestionId(questionId: String): List<SyncQuestionAssetEntity>

    @Query("SELECT * FROM sync_question_assets")
    suspend fun getAll(): List<SyncQuestionAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assets: List<SyncQuestionAssetEntity>)
}

@Dao
interface SyncQuestionOptionDao {
    @Query("SELECT * FROM sync_question_options WHERE questionId = :questionId ORDER BY sortOrder")
    suspend fun getByQuestionId(questionId: String): List<SyncQuestionOptionEntity>

    @Query("SELECT * FROM sync_question_options")
    suspend fun getAll(): List<SyncQuestionOptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(options: List<SyncQuestionOptionEntity>)
}

@Dao
interface SyncProgressDao {
    @Query("SELECT * FROM sync_progress WHERE pendingUpload = 1")
    suspend fun getPendingUploads(): List<SyncProgressEntity>

    @Query("SELECT * FROM sync_progress")
    suspend fun getAll(): List<SyncProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(progress: List<SyncProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: SyncProgressEntity)

    @Query("UPDATE sync_progress SET pendingUpload = 0, syncedAt = :syncedAt WHERE questionId IN (:questionIds)")
    suspend fun markUploaded(questionIds: List<String>, syncedAt: String)
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)
}

@Dao
interface WrongQuestionDao {
    @Query("SELECT * FROM wrong_questions")
    fun getAllWrongQuestions(): Flow<List<WrongQuestion>>

    @Query("SELECT * FROM wrong_questions WHERE questionId = :questionId LIMIT 1")
    suspend fun getWrongQuestion(questionId: String): WrongQuestion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(wrongQuestion: WrongQuestion)

    @Query("DELETE FROM wrong_questions WHERE questionId = :questionId")
    suspend fun deleteWrongQuestion(questionId: String)
}

@Dao
interface MasteredQuestionDao {
    @Query("SELECT * FROM mastered_questions")
    fun getAllMasteredQuestions(): Flow<List<MasteredQuestion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(masteredQuestion: MasteredQuestion)

    @Query("DELETE FROM mastered_questions WHERE questionId = :questionId")
    suspend fun delete(questionId: String)
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY id DESC LIMIT 200")
    fun getAllDiaryEntries(): Flow<List<DiaryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DiaryEntry)
}

@Dao
interface MonsterStatsDao {
    @Query("SELECT * FROM monster_stats")
    fun getAllMonsterStats(): Flow<List<MonsterStats>>

    @Query("SELECT * FROM monster_stats WHERE monsterId = :monsterId LIMIT 1")
    suspend fun getMonsterStats(monsterId: String): MonsterStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: MonsterStats)
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getUserSettingsFlow(): Flow<UserSettings?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getUserSettings(): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: UserSettings)
}
