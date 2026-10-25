package com.gatecse.companion.model

data class SubjectInfo(
    val code: String,
    val name: String,
    val priorCompletedLectures: Int,
    val totalLectures: Int,
    val type: TaskType = TaskType.RECORDED
)

object SubjectRegistry {

    val subjects = listOf(
        SubjectInfo("DL", "Digital Logic", priorCompletedLectures = 41, totalLectures = 52, TaskType.RECORDED),
        SubjectInfo("CN", "Computer Network", priorCompletedLectures = 18, totalLectures = 73, TaskType.RECORDED),
        SubjectInfo("DBMS", "DBMS", priorCompletedLectures = 27, totalLectures = 42, TaskType.RECORDED),
        SubjectInfo("C-PROG", "Fundamental of C + C Prog", priorCompletedLectures = 0, totalLectures = 43, TaskType.RECORDED),
        SubjectInfo("BOC", "Basics Of Computer System", priorCompletedLectures = 0, totalLectures = 10, TaskType.RECORDED),
        SubjectInfo("DS", "Data Structures (Old PW)", priorCompletedLectures = 0, totalLectures = 29, TaskType.RECORDED),
        SubjectInfo("COA", "COA", priorCompletedLectures = 0, totalLectures = 51, TaskType.LIVE),
        SubjectInfo("EM", "Foundation of Engg Maths", priorCompletedLectures = 0, totalLectures = 25, TaskType.RECORDED),
        SubjectInfo("LA", "Linear Algebra", priorCompletedLectures = 0, totalLectures = 23, TaskType.LIVE),
        SubjectInfo("OS", "Operating System", priorCompletedLectures = 0, totalLectures = 43, TaskType.LIVE),
        SubjectInfo("PS", "Probability & Statistics", priorCompletedLectures = 0, totalLectures = 41, TaskType.LIVE),
        SubjectInfo("ALGO", "Algorithms", priorCompletedLectures = 0, totalLectures = 52, TaskType.RECORDED),
        SubjectInfo("CALC", "Calculus & Optimization", priorCompletedLectures = 0, totalLectures = 23, TaskType.LIVE),
        SubjectInfo("GA", "General Aptitude", priorCompletedLectures = 0, totalLectures = 29, TaskType.LIVE),
        SubjectInfo("DM", "Discrete Mathematics", priorCompletedLectures = 0, totalLectures = 53, TaskType.LIVE),
        SubjectInfo("TOC", "TOC", priorCompletedLectures = 0, totalLectures = 29, TaskType.RECORDED),
        SubjectInfo("CD", "Compiler Design", priorCompletedLectures = 0, totalLectures = 22, TaskType.RECORDED),
        SubjectInfo("VA", "Verbal Aptitude", priorCompletedLectures = 0, totalLectures = 5, TaskType.LIVE),
        SubjectInfo("MOCK", "Full-Syllabus Revision & Mock Practice", priorCompletedLectures = 0, totalLectures = 15, TaskType.REVISION_MOCK),
        SubjectInfo("REV", "Saturday Revision", priorCompletedLectures = 0, totalLectures = 6, TaskType.REVISION),
        SubjectInfo("M-BUF", "Math Buffer & Revision", priorCompletedLectures = 0, totalLectures = 3, TaskType.BUFFER),
        SubjectInfo("BUF", "Buffer & Catch-Up", priorCompletedLectures = 0, totalLectures = 12, TaskType.BUFFER)
    )

    private val nameToCodeMap = subjects.associate { it.name.lowercase() to it.code }
    private val codeToInfoMap = subjects.associateBy { it.code.uppercase() }

    fun getCodeForSubject(subjectName: String): String {
        val clean = subjectName.trim().lowercase()
        return nameToCodeMap[clean] ?: when {
            clean.contains("digital") -> "DL"
            clean.contains("network") -> "CN"
            clean.contains("dbms") -> "DBMS"
            clean.contains("c prog") || clean.contains("fundamental of c") -> "C-PROG"
            clean.contains("basics of computer") -> "BOC"
            clean.contains("data structure") -> "DS"
            clean.contains("coa") -> "COA"
            clean.contains("math buffer") -> "M-BUF"
            clean.contains("engg math") || clean.contains("foundation of engg") -> "EM"
            clean.contains("linear algebra") -> "LA"
            clean.contains("operating system") -> "OS"
            clean.contains("probability") -> "PS"
            clean.contains("algorithm") -> "ALGO"
            clean.contains("calculus") -> "CALC"
            clean.contains("general aptitude") -> "GA"
            clean.contains("discrete") -> "DM"
            clean.contains("toc") || clean.contains("theory of comp") -> "TOC"
            clean.contains("compiler") -> "CD"
            clean.contains("verbal") -> "VA"
            clean.contains("mock") || clean.contains("full-syllabus") -> "MOCK"
            clean.contains("revision") -> "REV"
            clean.contains("buffer") -> "BUF"
            else -> clean.take(4).uppercase()
        }
    }

    fun getSubjectInfoByCode(code: String): SubjectInfo? {
        return codeToInfoMap[code.trim().uppercase()]
    }

    fun getPriorCompletedLectures(subjectName: String): Int {
        val code = getCodeForSubject(subjectName)
        return codeToInfoMap[code]?.priorCompletedLectures ?: 0
    }
}
