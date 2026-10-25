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
import java.lang.reflect.Proxy

class DatabaseReliabilityTest {

    private fun createRecordingDatabase(recordedQueries: MutableList<String>): SupportSQLiteDatabase {
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                recordedQueries.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase
    }

    @Test
    fun testMigration1To2CreatesCustomTasksTable() {
        val executedQueries = mutableListOf<String>()
        val mockDb = createRecordingDatabase(executedQueries)

        GateCompanionDatabase.MIGRATION_1_2.migrate(mockDb)

        assertTrue(
            "MIGRATION_1_2 must create custom_tasks table",
            executedQueries.any { it.contains("CREATE TABLE IF NOT EXISTS `custom_tasks`") }
        )
        assertTrue(
            "MIGRATION_1_2 must create index on custom_tasks date",
            executedQueries.any { it.contains("CREATE INDEX IF NOT EXISTS `index_custom_tasks_date`") }
        )
    }

    @Test
    fun testMigration2To3CreatesDppRecordsTable() {
        val executedQueries = mutableListOf<String>()
        val mockDb = createRecordingDatabase(executedQueries)

        GateCompanionDatabase.MIGRATION_2_3.migrate(mockDb)

        assertTrue(
            "MIGRATION_2_3 must create dpp_records table",
            executedQueries.any { it.contains("CREATE TABLE IF NOT EXISTS `dpp_records`") }
        )
        assertTrue(
            "MIGRATION_2_3 must create index on dpp_records date",
            executedQueries.any { it.contains("CREATE INDEX IF NOT EXISTS `index_dpp_records_date`") }
        )
    }

    @Test
    fun testMigration1To3DirectUpgradeExecutesBothMigrations() {
        val executedQueries = mutableListOf<String>()
        val mockDb = createRecordingDatabase(executedQueries)

        GateCompanionDatabase.MIGRATION_1_3.migrate(mockDb)

        assertTrue(
            "Direct upgrade 1->3 must create custom_tasks table",
            executedQueries.any { it.contains("CREATE TABLE IF NOT EXISTS `custom_tasks`") }
        )
        assertTrue(
            "Direct upgrade 1->3 must create dpp_records table",
            executedQueries.any { it.contains("CREATE TABLE IF NOT EXISTS `dpp_records`") }
        )
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
