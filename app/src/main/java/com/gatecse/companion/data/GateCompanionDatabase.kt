package com.gatecse.companion.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gatecse.companion.model.AchievementEntity
import com.gatecse.companion.model.AssessmentResultEntity
import com.gatecse.companion.model.CustomTaskEntity
import com.gatecse.companion.model.DppRecordEntity
import com.gatecse.companion.model.RecoveryAssignmentEntity
import com.gatecse.companion.model.ScheduleTaskEntity
import com.gatecse.companion.model.TaskProgressEntity
import com.gatecse.companion.model.UserSettingsEntity

@Database(
    entities = [
        ScheduleTaskEntity::class,
        TaskProgressEntity::class,
        RecoveryAssignmentEntity::class,
        AssessmentResultEntity::class,
        AchievementEntity::class,
        UserSettingsEntity::class,
        CustomTaskEntity::class,
        DppRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class GateCompanionDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: GateCompanionDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `subjectCode` TEXT NOT NULL,
                        `subjectName` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `completedAtTimestamp` INTEGER,
                        `notes` TEXT NOT NULL,
                        `pointsValue` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_tasks_date` ON `custom_tasks` (`date`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dpp_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `subjectCode` TEXT NOT NULL,
                        `subjectName` TEXT NOT NULL,
                        `topic` TEXT NOT NULL,
                        `totalQuestions` INTEGER NOT NULL,
                        `questionsCorrect` INTEGER NOT NULL,
                        `accuracyPercentage` REAL NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAtTimestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dpp_records_date` ON `dpp_records` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dpp_records_subjectCode` ON `dpp_records` (`subjectCode`)")
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
            }
        }

        fun getInstance(context: Context): GateCompanionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GateCompanionDatabase::class.java,
                    "gate_cse_companion.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
