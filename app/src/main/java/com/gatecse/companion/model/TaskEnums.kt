package com.gatecse.companion.model

enum class TaskStatus {
    UPCOMING,
    DUE_TODAY,
    COMPLETED_ON_TIME,
    COMPLETED_LATE,
    MISSED,
    RECOVERY_SCHEDULED
}

enum class TaskType {
    RECORDED,
    LIVE,
    BUFFER,
    REVISION,
    REVISION_MOCK,
    SUNDAY_TEST,
    SUNDAY_PYQ,
    BOOSTER_REVISION,
    BOOSTER_PROBLEM_SOLVING,
    CUSTOM,
    OTHER
}

enum class PreparationHealth {
    ON_TRACK,
    AT_RISK,
    BEHIND
}

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}
