package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*

@Database(
    entities = [
        Question::class,
        WrongQuestion::class,
        MasteredQuestion::class,
        DiaryEntry::class,
        MonsterStats::class,
        UserSettings::class,
        SyncTopicEntity::class,
        SyncQuestionEntity::class,
        SyncQuestionAssetEntity::class,
        SyncQuestionOptionEntity::class,
        SyncProgressEntity::class,
        SyncStateEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun wrongQuestionDao(): WrongQuestionDao
    abstract fun masteredQuestionDao(): MasteredQuestionDao
    abstract fun diaryDao(): DiaryDao
    abstract fun monsterStatsDao(): MonsterStatsDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun syncTopicDao(): SyncTopicDao
    abstract fun syncQuestionDao(): SyncQuestionDao
    abstract fun syncQuestionAssetDao(): SyncQuestionAssetDao
    abstract fun syncQuestionOptionDao(): SyncQuestionOptionDao
    abstract fun syncProgressDao(): SyncProgressDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "math_adventure_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS mastered_questions (questionId TEXT NOT NULL PRIMARY KEY)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_topics (id TEXT NOT NULL PRIMARY KEY, grade INTEGER NOT NULL, strand TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, syncedAt TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_questions (id TEXT NOT NULL PRIMARY KEY, topicId TEXT NOT NULL, grade INTEGER NOT NULL, difficulty INTEGER NOT NULL, qtype TEXT NOT NULL, stemText TEXT NOT NULL, layoutJson TEXT NOT NULL, correctAnswerJson TEXT NOT NULL, explanation TEXT NOT NULL, story TEXT NOT NULL, methodHint TEXT NOT NULL, hiddenTrapsJson TEXT NOT NULL, referenceJson TEXT NOT NULL, source TEXT NOT NULL, aiGenerated INTEGER NOT NULL, reviewStatus TEXT NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, syncedAt TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_question_assets (id TEXT NOT NULL PRIMARY KEY, questionId TEXT NOT NULL, role TEXT NOT NULL, slotKey TEXT NOT NULL, label TEXT NOT NULL, assetType TEXT NOT NULL, svgContent TEXT NOT NULL, storagePath TEXT NOT NULL, width INTEGER, height INTEGER, sortOrder INTEGER NOT NULL, createdAt TEXT NOT NULL, syncedAt TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_question_options (id TEXT NOT NULL PRIMARY KEY, questionId TEXT NOT NULL, optionKey TEXT NOT NULL, optionLabel TEXT NOT NULL, optionAssetId TEXT NOT NULL, sortOrder INTEGER NOT NULL, syncedAt TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_progress (questionId TEXT NOT NULL PRIMARY KEY, remoteId TEXT NOT NULL, userId TEXT NOT NULL, status TEXT NOT NULL, userAnswerJson TEXT NOT NULL, isCorrect INTEGER, attemptCount INTEGER NOT NULL, lastAttemptAt TEXT NOT NULL, nextReviewAt TEXT NOT NULL, updatedAt TEXT NOT NULL, syncedAt TEXT NOT NULL, pendingUpload INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
                db.execSQL("ALTER TABLE questions ADD COLUMN qtype TEXT NOT NULL DEFAULT 'fill_blank'")
                db.execSQL("ALTER TABLE questions ADD COLUMN layoutJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE questions ADD COLUMN correctAnswerJson TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
