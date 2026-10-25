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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
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

        fun getInstance(context: Context): GateCompanionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GateCompanionDatabase::class.java,
                    "gate_cse_companion.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
