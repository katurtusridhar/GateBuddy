package com.gatecse.companion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleDataTest {

    private lateinit var jsonString: String
    private lateinit var rootJson: JSONObject

    @Before
    fun setup() {
        val assetFile = File("src/main/assets/gate_cse_schedule.json")
        assertTrue("Asset file must exist at src/main/assets/gate_cse_schedule.json", assetFile.exists())
        jsonString = assetFile.readText()
        rootJson = JSONObject(jsonString)
    }

    @Test
    fun testCalendarTotalDaysAndTaskCount() {
        val metadata = rootJson.getJSONObject("metadata")
        assertEquals(345, metadata.getInt("totalDays"))
        assertEquals(690, metadata.getInt("totalTasks"))
        assertEquals(49, metadata.getInt("totalSundays"))
        assertEquals("2026-10-19", metadata.getString("startDate"))
        assertEquals("2027-09-28", metadata.getString("endDate"))

        val daysArray = rootJson.getJSONArray("days")
        val tasksArray = rootJson.getJSONArray("tasks")
        assertEquals("Must contain exactly 345 days", 345, daysArray.length())
        assertEquals("Must contain exactly 690 tasks (2 slots/day)", 690, tasksArray.length())
    }

    @Test
    fun testDateContinuityAndWeekdayIntegrity() {
        val daysArray = rootJson.getJSONArray("days")
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var expectedDate = LocalDate.parse("2026-10-19", dateFormatter)

        for (i in 0 until daysArray.length()) {
            val dayObj = daysArray.getJSONObject(i)
            val dateStr = dayObj.getString("date")
            val actualDate = LocalDate.parse(dateStr, dateFormatter)
            assertEquals("Date at index $i must match continuous progression", expectedDate, actualDate)

            val expectedWeekday = expectedDate.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val actualWeekday = dayObj.getString("dayOfWeek")
            assertEquals("Weekday at $dateStr must match calendar", expectedWeekday, actualWeekday)

            expectedDate = expectedDate.plusDays(1)
        }
    }

    @Test
    fun testTaskIdentifiersAreUniqueAndWellFormed() {
        val tasksArray = rootJson.getJSONArray("tasks")
        val ids = mutableSetOf<String>()

        for (i in 0 until tasksArray.length()) {
            val taskObj = tasksArray.getJSONObject(i)
            val id = taskObj.getString("id")
            assertTrue("Task ID must follow TASK_XXXX format: $id", id.matches(Regex("^TASK_\\d{4}$")))
            assertFalse("Duplicate Task ID found: $id", ids.contains(id))
            ids.add(id)
        }
        assertEquals(690, ids.size)
    }

    @Test
    fun testSubjectLectureSequenceIntegrity() {
        val tasksArray = rootJson.getJSONArray("tasks")
        val subjectSequences = mutableMapOf<String, MutableList<Int>>()

        for (i in 0 until tasksArray.length()) {
            val taskObj = tasksArray.getJSONObject(i)
            val subject = taskObj.getString("subject")
            val curNum = taskObj.getInt("curNum")
            if (curNum > 0 && !taskObj.getBoolean("isSundayActivity")) {
                subjectSequences.getOrPut(subject) { mutableListOf() }.add(curNum)
            }
        }

        // Verify each core subject starts and has zero sequence gaps
        for ((subj, nums) in subjectSequences) {
            for (k in 0 until nums.size - 1) {
                assertEquals(
                    "Subject '$subj' must have consecutive numbering without gaps",
                    nums[k] + 1,
                    nums[k + 1]
                )
            }
        }

        // Verify known subject endpoints
        assertEquals(11, subjectSequences["Digital Logic"]?.size)
        assertEquals(42, subjectSequences["Digital Logic"]?.first())
        assertEquals(52, subjectSequences["Digital Logic"]?.last())

        assertEquals(55, subjectSequences["Computer Network"]?.size)
        assertEquals(19, subjectSequences["Computer Network"]?.first())
        assertEquals(73, subjectSequences["Computer Network"]?.last())

        assertEquals(15, subjectSequences["DBMS"]?.size)
        assertEquals(28, subjectSequences["DBMS"]?.first())
        assertEquals(42, subjectSequences["DBMS"]?.last())

        assertEquals(43, subjectSequences["Fundamental of C + C Prog"]?.size)
        assertEquals(1, subjectSequences["Fundamental of C + C Prog"]?.first())
        assertEquals(43, subjectSequences["Fundamental of C + C Prog"]?.last())

        assertEquals(22, subjectSequences["Compiler Design"]?.size)
        assertEquals(1, subjectSequences["Compiler Design"]?.first())
        assertEquals(22, subjectSequences["Compiler Design"]?.last())
    }

    @Test
    fun testSundayActivitiesArePreserved() {
        val daysArray = rootJson.getJSONArray("days")
        val tasksArray = rootJson.getJSONArray("tasks")
        val tasksById = (0 until tasksArray.length()).map { tasksArray.getJSONObject(it) }.associateBy { it.getString("id") }

        var sundayCount = 0
        for (i in 0 until daysArray.length()) {
            val dayObj = daysArray.getJSONObject(i)
            if (dayObj.getBoolean("isSunday")) {
                sundayCount++
                val taskIds = dayObj.getJSONArray("taskIds")
                assertEquals("Every Sunday must have exactly 2 tasks", 2, taskIds.length())

                val t1 = tasksById[taskIds.getString(0)]!!
                val t2 = tasksById[taskIds.getString(1)]!!

                assertEquals("Slot 1 on Sunday must be SUNDAY_TEST", "SUNDAY_TEST", t1.getString("type"))
                assertEquals("Slot 2 on Sunday must be SUNDAY_PYQ", "SUNDAY_PYQ", t2.getString("type"))
                assertTrue("Sunday tasks must have isSundayActivity = true", t1.getBoolean("isSundayActivity"))
                assertTrue("Sunday tasks must have isSundayActivity = true", t2.getBoolean("isSundayActivity"))
            }
        }
        assertEquals(49, sundayCount)
    }
}
