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
