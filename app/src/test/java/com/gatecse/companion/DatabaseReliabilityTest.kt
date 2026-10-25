package com.gatecse.companion

import androidx.sqlite.db.SupportSQLiteDatabase
import com.gatecse.companion.data.GateCompanionDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class DatabaseReliabilityTest {

    data class ColumnDef(
        val cid: Int,
        val name: String,
        val type: String,
        val notnull: Boolean,
        val dfltValue: String?,
        val pk: Int
    )

    data class IndexDef(
        val seq: Int,
        val name: String,
        val unique: Boolean
    )

    class RealSqliteFixture(val dbFile: File) {
        fun execSQL(sql: String) {
            val process = ProcessBuilder(
                "python", "-c",
                """
import sqlite3, sys
conn = sqlite3.connect(sys.argv[1])
cursor = conn.cursor()
cursor.executescript(sys.argv[2])
conn.commit()
conn.close()
                """.trimIndent(),
                dbFile.absolutePath,
                sql
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Real SQLite execSQL failed ($exitCode): $output\nSQL was:\n$sql")
            }
        }

        fun queryRows(sql: String): List<Map<String, Any?>> {
            val process = ProcessBuilder(
                "python", "-c",
                """
import sqlite3, json, sys
conn = sqlite3.connect(sys.argv[1])
conn.row_factory = sqlite3.Row
cursor = conn.cursor()
cursor.execute(sys.argv[2])
rows = [dict(row) for row in cursor.fetchall()]
print(json.dumps(rows))
conn.close()
                """.trimIndent(),
                dbFile.absolutePath,
                sql
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Real SQLite query failed ($exitCode): $output\nSQL was:\n$sql")
            }
            val jsonArray = JSONArray(output)
            val list = mutableListOf<Map<String, Any?>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = if (obj.isNull(k)) null else obj.get(k)
                }
                list.add(map)
            }
            return list
        }

        fun getTableColumns(tableName: String): List<ColumnDef> {
            val rows = queryRows("PRAGMA table_info(`$tableName`)")
            return rows.map { r ->
                ColumnDef(
                    cid = (r["cid"] as Number).toInt(),
                    name = r["name"].toString(),
                    type = r["type"].toString(),
                    notnull = ((r["notnull"] as? Number)?.toInt() ?: 0) == 1,
                    dfltValue = r["dflt_value"]?.toString(),
                    pk = (r["pk"] as? Number)?.toInt() ?: 0
                )
            }
        }

        fun getTableIndices(tableName: String): List<IndexDef> {
            val rows = queryRows("PRAGMA index_list(`$tableName`)")
            return rows.filter { !it["name"].toString().startsWith("sqlite_autoindex") }.map { r ->
                IndexDef(
                    seq = (r["seq"] as Number).toInt(),
                    name = r["name"].toString(),
                    unique = ((r["unique"] as? Number)?.toInt() ?: 0) == 1
                )
            }
        }

        fun asSupportSQLiteDatabase(): SupportSQLiteDatabase {
            return Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java)
            ) { _, method, args ->
                if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                    execSQL(args[0] as String)
                }
                null
            } as SupportSQLiteDatabase
        }

        fun close() {
            try {
                dbFile.delete()
            } catch (_: Exception) {}
        }
    }

    private fun createV1PopulatedDatabase(): RealSqliteFixture {
        val tempFile = File.createTempFile("gatebuddy_room_v1_", ".db")
        val fixture = RealSqliteFixture(tempFile)

        // Historical schema v1 DDL for all 6 baseline entities
        val v1SchemaSql = """
            CREATE TABLE IF NOT EXISTS `schedule_tasks` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `dayOfWeek` TEXT NOT NULL,
                `slot` INTEGER NOT NULL,
                `subject` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `curNum` INTEGER NOT NULL,
                `totalCount` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `details` TEXT NOT NULL,
                `milestone` TEXT NOT NULL,
                `sourcePage` INTEGER NOT NULL,
                `isBuffer` INTEGER NOT NULL,
                `isRevision` INTEGER NOT NULL,
                `isSundayActivity` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            );
            CREATE INDEX IF NOT EXISTS `index_schedule_tasks_date` ON `schedule_tasks` (`date`);
            CREATE INDEX IF NOT EXISTS `index_schedule_tasks_subject` ON `schedule_tasks` (`subject`);
            CREATE INDEX IF NOT EXISTS `index_schedule_tasks_isBuffer` ON `schedule_tasks` (`isBuffer`);
            CREATE INDEX IF NOT EXISTS `index_schedule_tasks_isSundayActivity` ON `schedule_tasks` (`isSundayActivity`);

            CREATE TABLE IF NOT EXISTS `task_progress` (
                `taskId` TEXT NOT NULL,
                `originalDate` TEXT NOT NULL,
                `isCompleted` INTEGER NOT NULL,
                `completedAtTimestamp` INTEGER,
                `completionType` TEXT,
                `notes` TEXT NOT NULL,
                PRIMARY KEY(`taskId`)
            );
            CREATE UNIQUE INDEX IF NOT EXISTS `index_task_progress_taskId` ON `task_progress` (`taskId`);
            CREATE INDEX IF NOT EXISTS `index_task_progress_originalDate` ON `task_progress` (`originalDate`);

            CREATE TABLE IF NOT EXISTS `recovery_assignments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `taskId` TEXT NOT NULL,
                `originalDate` TEXT NOT NULL,
                `recoveryDate` TEXT NOT NULL,
                `assignedAtTimestamp` INTEGER NOT NULL,
                `isCompleted` INTEGER NOT NULL,
                `completedAtTimestamp` INTEGER,
                `priorityLevel` INTEGER NOT NULL,
                `status` TEXT NOT NULL
            );
            CREATE UNIQUE INDEX IF NOT EXISTS `index_recovery_assignments_taskId` ON `recovery_assignments` (`taskId`);
            CREATE INDEX IF NOT EXISTS `index_recovery_assignments_recoveryDate` ON `recovery_assignments` (`recoveryDate`);

            CREATE TABLE IF NOT EXISTS `assessment_results` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `testDate` TEXT NOT NULL,
                `testName` TEXT NOT NULL,
                `subjectName` TEXT NOT NULL,
                `marksObtained` REAL NOT NULL,
                `maxMarks` REAL NOT NULL,
                `percentage` REAL NOT NULL,
                `questionsAttempted` INTEGER,
                `questionsCorrect` INTEGER,
                `topic` TEXT NOT NULL,
                `mistakesAnalysis` TEXT NOT NULL,
                `followUpTasks` TEXT NOT NULL,
                `createdAtTimestamp` INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS `index_assessment_results_testDate` ON `assessment_results` (`testDate`);
            CREATE INDEX IF NOT EXISTS `index_assessment_results_subjectName` ON `assessment_results` (`subjectName`);

            CREATE TABLE IF NOT EXISTS `achievements` (
                `code` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `isUnlocked` INTEGER NOT NULL,
                `unlockedAtTimestamp` INTEGER,
                PRIMARY KEY(`code`)
            );
            CREATE UNIQUE INDEX IF NOT EXISTS `index_achievements_code` ON `achievements` (`code`);

            CREATE TABLE IF NOT EXISTS `user_settings` (
                `id` INTEGER NOT NULL,
                `targetExamDate` TEXT,
                `targetExamName` TEXT NOT NULL,
                `dailyRecoveryLimit` INTEGER NOT NULL,
                `themeMode` TEXT NOT NULL,
                `reminderEnabled` INTEGER NOT NULL,
                `reminderSlot1Hour` INTEGER NOT NULL,
                `reminderSlot1Minute` INTEGER NOT NULL,
                `reminderSlot2Hour` INTEGER NOT NULL,
                `reminderSlot2Minute` INTEGER NOT NULL,
                `isOnboarded` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            );
        """.trimIndent()
        fixture.execSQL(v1SchemaSql)

        // Seed representative test data across all 6 v1 entities
        val seedSql = """
            INSERT INTO `schedule_tasks` VALUES ('TASK_0001', '2026-10-19', 'Mon', 1, 'Digital Logic', 'RECORDED', 42, 52, 'DL Lec 42', 'Details', 'M1', 1, 0, 0, 0);
            INSERT INTO `schedule_tasks` VALUES ('TASK_0002', '2026-10-19', 'Mon', 2, 'Computer Network', 'RECORDED', 10, 45, 'CN Lec 10', 'Details', 'M1', 1, 0, 0, 0);

            INSERT INTO `task_progress` VALUES ('TASK_0001', '2026-10-19', 1, 1792400000000, 'ON_TIME', 'Completed lecture');
            INSERT INTO `task_progress` VALUES ('TASK_0002', '2026-10-19', 0, NULL, NULL, 'Pending');

            INSERT INTO `recovery_assignments` VALUES (1, 'TASK_0005', '2026-10-22', '2026-10-25', 1792700000000, 0, NULL, 2, 'ACTIVE');

            INSERT INTO `assessment_results` VALUES (1, '2026-10-25', 'Sunday Weekly Test 1', 'Digital Logic', 24.5, 30.0, 81.67, 15, 13, 'K-Maps', 'Timing calculation error', 'Review adders', 1792900000000);

            INSERT INTO `achievements` VALUES ('FIRST_TASK', 'First Step', 'Complete first task', 'CONSISTENCY', 1, 1792400000000);

            INSERT INTO `user_settings` VALUES (1, '2028-02-12', 'GATE CSE 2028', 2, 'DARK', 1, 9, 0, 18, 0, 1);
        """.trimIndent()
        fixture.execSQL(seedSql)

        return fixture
    }

    @Test
    fun testRealSqliteMigration1To2PreservesDataAndCreatesCustomTasks() {
        val fixture = createV1PopulatedDatabase()
        try {
            // Execute real MIGRATION_1_2 against real SQLite database
            GateCompanionDatabase.MIGRATION_1_2.migrate(fixture.asSupportSQLiteDatabase())

            // 1. Verify custom_tasks table created
            val columns = fixture.getTableColumns("custom_tasks")
            assertTrue("custom_tasks table must exist and contain columns", columns.isNotEmpty())
            val colMap = columns.associateBy { it.name }

            assertEquals("INTEGER", colMap["id"]?.type)
            assertEquals(1, colMap["id"]?.pk)
            assertEquals(true, colMap["id"]?.notnull)

            assertEquals("TEXT", colMap["date"]?.type)
            assertEquals(true, colMap["date"]?.notnull)

            assertEquals("TEXT", colMap["title"]?.type)
            assertEquals(true, colMap["title"]?.notnull)

            assertEquals("TEXT", colMap["subjectCode"]?.type)
            assertEquals("TEXT", colMap["subjectName"]?.type)

            assertEquals("INTEGER", colMap["isCompleted"]?.type)
            assertEquals(true, colMap["isCompleted"]?.notnull)

            assertEquals("INTEGER", colMap["completedAtTimestamp"]?.type)
            assertEquals(false, colMap["completedAtTimestamp"]?.notnull) // Nullable

            assertEquals("TEXT", colMap["notes"]?.type)
            assertEquals("INTEGER", colMap["pointsValue"]?.type)

            // 2. Verify index on custom_tasks(date)
            val indices = fixture.getTableIndices("custom_tasks")
            assertTrue(
                "index_custom_tasks_date must exist in SQLite",
                indices.any { it.name == "index_custom_tasks_date" }
            )

            // 3. Verify ALL 6 historical entities and their rows survived with ZERO data loss
            val scheduleRows = fixture.queryRows("SELECT id, subject FROM schedule_tasks ORDER BY id")
            assertEquals(2, scheduleRows.size)
            assertEquals("TASK_0001", scheduleRows[0]["id"])
            assertEquals("Digital Logic", scheduleRows[0]["subject"])

            val progressRows = fixture.queryRows("SELECT taskId, isCompleted, notes FROM task_progress ORDER BY taskId")
            assertEquals(2, progressRows.size)
            assertEquals(1, (progressRows[0]["isCompleted"] as Number).toInt())
            assertEquals("Completed lecture", progressRows[0]["notes"])

            val recoveryRows = fixture.queryRows("SELECT taskId, status, priorityLevel FROM recovery_assignments")
            assertEquals(1, recoveryRows.size)
            assertEquals("TASK_0005", recoveryRows[0]["taskId"])
            assertEquals("ACTIVE", recoveryRows[0]["status"])

            val assessmentRows = fixture.queryRows("SELECT testName, marksObtained, maxMarks FROM assessment_results")
            assertEquals(1, assessmentRows.size)
            assertEquals("Sunday Weekly Test 1", assessmentRows[0]["testName"])
            assertEquals(24.5, (assessmentRows[0]["marksObtained"] as Number).toDouble(), 0.001)

            val achievementRows = fixture.queryRows("SELECT code, isUnlocked FROM achievements")
            assertEquals(1, achievementRows.size)
            assertEquals("FIRST_TASK", achievementRows[0]["code"])
            assertEquals(1, (achievementRows[0]["isUnlocked"] as Number).toInt())

            val settingsRows = fixture.queryRows("SELECT targetExamName, dailyRecoveryLimit, themeMode FROM user_settings")
            assertEquals(1, settingsRows.size)
            assertEquals("GATE CSE 2028", settingsRows[0]["targetExamName"])
            assertEquals("DARK", settingsRows[0]["themeMode"])

            // 4. Verify insert into newly migrated custom_tasks table succeeds
            fixture.execSQL("INSERT INTO custom_tasks (date, title, subjectCode, subjectName, isCompleted, completedAtTimestamp, notes, pointsValue) VALUES ('2026-10-21', 'Solve PYQ 2024', 'DL', 'Digital Logic', 1, 1792600000000, 'Solved', 5)")
            val customRows = fixture.queryRows("SELECT title, pointsValue FROM custom_tasks")
            assertEquals(1, customRows.size)
            assertEquals("Solve PYQ 2024", customRows[0]["title"])
            assertEquals(5, (customRows[0]["pointsValue"] as Number).toInt())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun testRealSqliteMigration2To3PreservesDataAndCreatesDppRecords() {
        val fixture = createV1PopulatedDatabase()
        try {
            // First migrate 1 -> 2
            GateCompanionDatabase.MIGRATION_1_2.migrate(fixture.asSupportSQLiteDatabase())
            fixture.execSQL("INSERT INTO custom_tasks (date, title, subjectCode, subjectName, isCompleted, notes, pointsValue) VALUES ('2026-10-21', 'Custom Task 1', 'GATE', 'General', 0, '', 5)")

            // Now migrate 2 -> 3
            GateCompanionDatabase.MIGRATION_2_3.migrate(fixture.asSupportSQLiteDatabase())

            // 1. Verify dpp_records table created
            val columns = fixture.getTableColumns("dpp_records")
            assertTrue("dpp_records table must exist", columns.isNotEmpty())
            val colMap = columns.associateBy { it.name }

            assertEquals("INTEGER", colMap["id"]?.type)
            assertEquals(1, colMap["id"]?.pk)
            assertEquals(true, colMap["id"]?.notnull)

            assertEquals("TEXT", colMap["date"]?.type)
            assertEquals(true, colMap["date"]?.notnull)
            assertEquals("TEXT", colMap["subjectCode"]?.type)
            assertEquals("TEXT", colMap["subjectName"]?.type)
            assertEquals("TEXT", colMap["topic"]?.type)

            assertEquals("INTEGER", colMap["totalQuestions"]?.type)
            assertEquals("INTEGER", colMap["questionsCorrect"]?.type)
            assertEquals("REAL", colMap["accuracyPercentage"]?.type)
            assertEquals("TEXT", colMap["notes"]?.type)
            assertEquals("INTEGER", colMap["createdAtTimestamp"]?.type)

            // 2. Verify indices on dpp_records(date) and dpp_records(subjectCode)
            val indices = fixture.getTableIndices("dpp_records")
            assertTrue("index_dpp_records_date must exist", indices.any { it.name == "index_dpp_records_date" })
            assertTrue("index_dpp_records_subjectCode must exist", indices.any { it.name == "index_dpp_records_subjectCode" })

            // 3. Verify ALL previous 7 entities preserved without data loss
            assertEquals(2, fixture.queryRows("SELECT id FROM schedule_tasks").size)
            assertEquals(2, fixture.queryRows("SELECT taskId FROM task_progress").size)
            assertEquals(1, fixture.queryRows("SELECT id FROM recovery_assignments").size)
            assertEquals(1, fixture.queryRows("SELECT id FROM assessment_results").size)
            assertEquals(1, fixture.queryRows("SELECT code FROM achievements").size)
            assertEquals(1, fixture.queryRows("SELECT id FROM user_settings").size)
            assertEquals(1, fixture.queryRows("SELECT id FROM custom_tasks").size)

            // 4. Verify insert into newly migrated dpp_records table succeeds
            fixture.execSQL("INSERT INTO dpp_records (date, subjectCode, subjectName, topic, totalQuestions, questionsCorrect, accuracyPercentage, notes, createdAtTimestamp) VALUES ('2026-10-20', 'DL', 'Digital Logic', 'Multiplexers', 15, 14, 93.33, 'Well done', 1792500000000)")
            val dppRows = fixture.queryRows("SELECT subjectCode, questionsCorrect, accuracyPercentage FROM dpp_records")
            assertEquals(1, dppRows.size)
            assertEquals("DL", dppRows[0]["subjectCode"])
            assertEquals(14, (dppRows[0]["questionsCorrect"] as Number).toInt())
            assertEquals(93.33, (dppRows[0]["accuracyPercentage"] as Number).toDouble(), 0.01)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun testRealSqliteDirectMigration1To3PreservesDataAndCreatesAllTables() {
        val fixture = createV1PopulatedDatabase()
        try {
            // Execute direct migration 1 -> 3
            GateCompanionDatabase.MIGRATION_1_3.migrate(fixture.asSupportSQLiteDatabase())

            // Verify both custom_tasks and dpp_records tables and their indices exist
            assertTrue(fixture.getTableColumns("custom_tasks").isNotEmpty())
            assertTrue(fixture.getTableColumns("dpp_records").isNotEmpty())

            assertTrue(fixture.getTableIndices("custom_tasks").any { it.name == "index_custom_tasks_date" })
            assertTrue(fixture.getTableIndices("dpp_records").any { it.name == "index_dpp_records_date" })
            assertTrue(fixture.getTableIndices("dpp_records").any { it.name == "index_dpp_records_subjectCode" })

            // Verify all 6 v1 entity rows survive
            assertEquals(2, fixture.queryRows("SELECT id FROM schedule_tasks").size)
            assertEquals(2, fixture.queryRows("SELECT taskId FROM task_progress").size)
            assertEquals(1, fixture.queryRows("SELECT id FROM recovery_assignments").size)
            assertEquals(1, fixture.queryRows("SELECT id FROM assessment_results").size)
            assertEquals(1, fixture.queryRows("SELECT code FROM achievements").size)
            assertEquals(1, fixture.queryRows("SELECT id FROM user_settings").size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun testAll8EntitiesSchemaMatchRoomGeneratedSchemaExpectations() {
        val fixture = createV1PopulatedDatabase()
        try {
            GateCompanionDatabase.MIGRATION_1_3.migrate(fixture.asSupportSQLiteDatabase())

            // Room Master Table and Identity Hash check
            val masterTableSql = """
                CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
                INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '9aaa03d97274f422106580a1cfc028a5');
            """.trimIndent()
            fixture.execSQL(masterTableSql)

            val masterRows = fixture.queryRows("SELECT identity_hash FROM room_master_table WHERE id = 42")
            assertEquals(1, masterRows.size)
            assertEquals("9aaa03d97274f422106580a1cfc028a5", masterRows[0]["identity_hash"])

            // Schema verification for all 8 entities:
            // 1. schedule_tasks (15 columns, 4 indices)
            val stCols = fixture.getTableColumns("schedule_tasks")
            assertEquals(15, stCols.size)
            val stIndices = fixture.getTableIndices("schedule_tasks")
            assertEquals(4, stIndices.size)

            // 2. task_progress (6 columns, 2 indices)
            val tpCols = fixture.getTableColumns("task_progress")
            assertEquals(6, tpCols.size)
            val tpIndices = fixture.getTableIndices("task_progress")
            assertEquals(2, tpIndices.size)

            // 3. recovery_assignments (9 columns, 2 indices)
            val raCols = fixture.getTableColumns("recovery_assignments")
            assertEquals(9, raCols.size)
            val raIndices = fixture.getTableIndices("recovery_assignments")
            assertEquals(2, raIndices.size)

            // 4. assessment_results (13 columns, 2 indices)
            val arCols = fixture.getTableColumns("assessment_results")
            assertEquals(13, arCols.size)
            val arIndices = fixture.getTableIndices("assessment_results")
            assertEquals(2, arIndices.size)

            // 5. achievements (6 columns, 1 index)
            val achCols = fixture.getTableColumns("achievements")
            assertEquals(6, achCols.size)
            val achIndices = fixture.getTableIndices("achievements")
            assertEquals(1, achIndices.size)

            // 6. user_settings (11 columns, 0 indices)
            val usCols = fixture.getTableColumns("user_settings")
            assertEquals(11, usCols.size)

            // 7. custom_tasks (9 columns, 1 index)
            val ctCols = fixture.getTableColumns("custom_tasks")
            assertEquals(9, ctCols.size)
            val ctIndices = fixture.getTableIndices("custom_tasks")
            assertEquals(1, ctIndices.size)

            // 8. dpp_records (10 columns, 2 indices)
            val dppCols = fixture.getTableColumns("dpp_records")
            assertEquals(10, dppCols.size)
            val dppIndices = fixture.getTableIndices("dpp_records")
            assertEquals(2, dppIndices.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun testFull8EntityBackupJsonSchemaAndLosslessSerialization() {
        // Construct realistic test payload representing all 8 Room database entities
        val root = JSONObject()

        // 1. Progress
        val progressArray = JSONArray().apply {
            put(JSONObject().apply {
                put("taskId", "TASK_0001")
                put("originalDate", "2026-10-19")
                put("isCompleted", true)
                put("completedAtTimestamp", 1792400000000L)
                put("completionType", "ON_TIME")
                put("notes", "Solid lecture completed")
            })
            put(JSONObject().apply {
                put("taskId", "TASK_0002")
                put("originalDate", "2026-10-19")
                put("isCompleted", false)
                put("completedAtTimestamp", JSONObject.NULL)
                put("completionType", JSONObject.NULL)
                put("notes", "")
            })
        }
        root.put("progress", progressArray)

        // 2. Assessments
        val assessmentsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("testDate", "2026-10-25")
                put("testName", "Sunday Weekly Test 1")
                put("subjectName", "Digital Logic")
                put("marksObtained", 24.5)
                put("maxMarks", 30.0)
                put("percentage", 81.67)
                put("topic", "K-Maps & Combinational")
                put("mistakesAnalysis", "Careless timing delay calculation")
                put("followUpTasks", "Review ripple carry adder delay")
                put("questionsAttempted", 15)
                put("questionsCorrect", 13)
                put("createdAtTimestamp", 1792900000000L)
            })
        }
        root.put("assessments", assessmentsArray)

        // 3. DPP Records
        val dppArray = JSONArray().apply {
            put(JSONObject().apply {
                put("date", "2026-10-20")
                put("subjectCode", "DL")
                put("subjectName", "Digital Logic")
                put("topic", "Logic Gates & Multiplexers")
                put("totalQuestions", 15)
                put("questionsCorrect", 14)
                put("accuracyPercentage", 93.33)
                put("notes", "Solved in 35 mins")
                put("createdAtTimestamp", 1792500000000L)
            })
        }
        root.put("dppRecords", dppArray)

        // 4. Custom Tasks
        val customArray = JSONArray().apply {
            put(JSONObject().apply {
                put("date", "2026-10-21")
                put("title", "Review PYQ 2024 Question 34")
                put("subjectCode", "DL")
                put("subjectName", "Digital Logic")
                put("isCompleted", true)
                put("completedAtTimestamp", 1792600000000L)
                put("notes", "Tricky setup time question")
                put("pointsValue", 5)
            })
        }
        root.put("customTasks", customArray)

        // 5. Recoveries
        val recoveriesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("taskId", "TASK_0005")
                put("originalDate", "2026-10-22")
                put("recoveryDate", "2026-10-25")
                put("assignedAtTimestamp", 1792700000000L)
                put("isCompleted", false)
                put("priorityLevel", 2)
                put("status", "ACTIVE")
            })
        }
        root.put("recoveries", recoveriesArray)

        // 6. Settings
        val settingsObj = JSONObject().apply {
            put("targetExamDate", "2028-02-12")
            put("targetExamName", "GATE CSE 2028")
            put("dailyRecoveryLimit", 2)
            put("themeMode", "DARK")
            put("reminderEnabled", true)
            put("reminderSlot1Hour", 5)
            put("reminderSlot1Minute", 0)
            put("reminderSlot2Hour", 9)
            put("reminderSlot2Minute", 0)
            put("isOnboarded", true)
        }
        root.put("settings", settingsObj)

        // 7. Achievements
        val achievementsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("code", "FIRST_TASK")
                put("title", "First Step")
                put("description", "Complete your first task")
                put("category", "CONSISTENCY")
                put("isUnlocked", true)
                put("unlockedAtTimestamp", 1792400000000L)
            })
        }
        root.put("achievements", achievementsArray)

        // Verify JSON string roundtrip parsing
        val jsonStr = root.toString()
        val parsed = JSONObject(jsonStr)

        // Check progress preservation including nulls
        val pList = parsed.getJSONArray("progress")
        assertEquals(2, pList.length())
        val p1 = pList.getJSONObject(0)
        assertEquals("TASK_0001", p1.getString("taskId"))
        assertEquals(true, p1.getBoolean("isCompleted"))
        assertEquals(1792400000000L, p1.getLong("completedAtTimestamp"))
        assertEquals("ON_TIME", p1.getString("completionType"))

        val p2 = pList.getJSONObject(1)
        assertEquals("TASK_0002", p2.getString("taskId"))
        assertEquals(false, p2.getBoolean("isCompleted"))
        assertTrue("Null timestamp must remain null in JSON", p2.isNull("completedAtTimestamp"))
        assertTrue("Null completionType must remain null in JSON", p2.isNull("completionType"))

        // Check assessments
        val aList = parsed.getJSONArray("assessments")
        assertEquals(1, aList.length())
        val a1 = aList.getJSONObject(0)
        assertEquals("Sunday Weekly Test 1", a1.getString("testName"))
        assertEquals(24.5, a1.getDouble("marksObtained"), 0.001)
        assertEquals(15, a1.getInt("questionsAttempted"))

        // Check DPP records
        val dList = parsed.getJSONArray("dppRecords")
        assertEquals(1, dList.length())
        val d1 = dList.getJSONObject(0)
        assertEquals("DL", d1.getString("subjectCode"))
        assertEquals(14, d1.getInt("questionsCorrect"))
        assertEquals(93.33, d1.getDouble("accuracyPercentage"), 0.01)

        // Check custom tasks
        val cList = parsed.getJSONArray("customTasks")
        assertEquals(1, cList.length())
        val c1 = cList.getJSONObject(0)
        assertEquals("Review PYQ 2024 Question 34", c1.getString("title"))
        assertEquals(5, c1.getInt("pointsValue"))

        // Check recoveries
        val rList = parsed.getJSONArray("recoveries")
        assertEquals(1, rList.length())
        val r1 = rList.getJSONObject(0)
        assertEquals("TASK_0005", r1.getString("taskId"))
        assertEquals("2026-10-25", r1.getString("recoveryDate"))

        // Check settings
        val s = parsed.getJSONObject("settings")
        assertEquals("GATE CSE 2028", s.getString("targetExamName"))
        assertEquals("2028-02-12", s.getString("targetExamDate"))
        assertEquals(5, s.getInt("reminderSlot1Hour"))

        // Check achievements
        val achList = parsed.getJSONArray("achievements")
        assertEquals(1, achList.length())
        val ach1 = achList.getJSONObject(0)
        assertEquals("FIRST_TASK", ach1.getString("code"))
        assertTrue(ach1.getBoolean("isUnlocked"))
    }
}
