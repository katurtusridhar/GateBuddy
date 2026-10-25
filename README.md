# GATE CSE Companion — Android Application

A premium, native Android application engineered with Kotlin, Jetpack Compose, Room, Android Glance, and WorkManager to execute the **GATE CSE 2026–2027 Master Daily Study Calendar**.

---

## 📱 Application Overview

- **Product Vision:** A personal preparation command centre that delivers reliable daily execution, transparent adherence metrics, intelligent recovery planning for missed tasks, and gamified milestone tracking.
- **Source of Truth:** 100% verified import of `GATE_CSE_Daily_Study_Calendar_2026_2027.pdf` (Oct 19, 2026 – Sep 28, 2027).
- **Architecture:** Offline-first Unidirectional Data Flow (UDF) with MVVM, Room Local Database, Glance AppWidgets, and Background Workers.

---

## 🛠️ Technology Stack

- **Language:** Kotlin 2.0.21
- **UI Framework:** Jetpack Compose (BOM 2024.10.01) + Material 3
- **Widgets:** AndroidX Glance AppWidget 1.1.1
- **Database:** Room 2.6.1 + KSP
- **Background Work:** WorkManager KTX 2.10.0
- **Build System:** Gradle 8.11.1 + Android Gradle Plugin 8.7.2
- **JDK:** OpenJDK 21 LTS (Eclipse Temurin)
- **Compile / Target SDK:** 35 (Android 15) | Min SDK: 26 (Android 8.0)

---

## ✨ Implemented Core Features

### 1. Daily Command Centre (Today Screen)
- Context-aware greeting and live date (Asia/Kolkata timezone support).
- **Target GATE Examination Countdown:** Live countdown with user-configurable date and exam name.
- **Daily Execution Score:** Polished circular progress ring showing `(completed / total) * 100` with explicit in-progress status.
- **Slot 1 & Slot 2 Task Cards:** Clean separation with subject pills, lecture progression, and milestone banners.
- **Sunday Activities:** Dedicated testing and PYQ mistake-analysis sections on Sundays.
- **Explicit Completion Rule:** Tasks never auto-complete; requires user checkbox interaction with haptic feedback.
- **Task Notes & Mistake Analysis:** Dedicated reflection notes dialog for formula reminders and errors.
- **Daily Recovery Queue:** Surfaces overdue tasks assigned to today alongside normal work.

### 2. Interactive Calendar Screen
- Monthly and weekly navigation across all 11 months (October 2026 – September 2027).
- Status-coded calendar grid (Green = Completed on time, Red = Missed/Overdue, Amber = Test/Recovery, Blue = Buffer, Slate = Upcoming).
- Selecting any date loads full task details, slot assignments, and completion controls.

### 3. All Subjects Progression Screen
- Tracks real lecture completion across all 21 subjects (Digital Logic, Computer Networks, DBMS, C Programming, Data Structures, COA, Engineering Maths, Linear Algebra, Operating Systems, Probability & Statistics, Algorithms, Calculus, General Aptitude, Discrete Mathematics, TOC, Compiler Design, Verbal Aptitude, and Revision modules).
- Displays total, completed, remaining, percentage, missed, late, next lecture, and completion milestones.
- Expandable drill-down to inspect individual subject history.

### 4. Learning Analytics & Intelligent Forecast
- **On-Time Adherence Rate:** Distinct metrics for completed on-time vs completed late vs missed vs recovery.
- **7-Day & 28-Day Adherence Trends:** Short-term velocity vs long-term consistency.
- **Preparation Health Card:** Dynamically evaluates `ON_TRACK`, `AT_RISK`, or `BEHIND` based on actual velocity and backlog size.
- **Intelligent Syllabus Forecast:** Estimates syllabus completion date based on 14-day completion pace, 12 available buffer days, and Sunday capacity.
- **Honesty Disclosure:** Transparently clarifies that completion metrics do not guarantee GATE ranks or exam outcomes.

### 5. Assessments, PYQs & Mistake Notebook
- Dedicated tracker for Sunday active recall tests, mock tests, and previous year question sessions.
- Records test name, date, subject, marks obtained, max marks, percentage, attempted/correct count, and mistake notebook analysis.
- Calculates overall average test score and maintains an error log.

### 6. Gamification & Verified Achievements
- 25 achievement badges (First Step, Flawless Execution, 7-Day Consistency, 30-Day Consistency, Master of Recall, Zero Debt, 16 Subject Masteries, Revision Champion, Exam Ready, GATE Conquered).
- Badges unlock strictly upon verified task and test data.

### 7. Settings, Portability & Verification Certificate
- Configure or modify target GATE examination date.
- Configurable daily recovery capacity limit (1 to 4 tasks/day).
- Theme mode selector: Dark (Onyx/Indigo default), Light, or System.
- Study reminder toggle with notification channel dispatch.
- **Schedule Import Verification Report:** Built-in viewer verifying 345 days, 690 tasks, and 0 sequence errors.
- **Data Portability:** Full JSON export to clipboard and JSON restoration with validation.

### 8. Android Home-Screen Widgets (Glance)
- **Widget A — GATE Countdown:** Live days remaining until target examination.
- **Widget B — Today's Tasks:** Slot 1 & Slot 2 lectures with completion checkboxes and date display.
- **Widget C — Preparation Progress:** Completion %, streak days, backlog count, and health badge.
- **Widget D — Upcoming Test:** Next scheduled Sunday test or active recall event.
- Deep links seamlessly open the app to corresponding screens (`app://gatecse/...`).

---

## 📊 Schedule Import Validation Report

- **Source Document:** `GATE_CSE_Daily_Study_Calendar_2026_2027.pdf` (Pages 1 to 16)
- **Calendar Range:** October 19, 2026 – September 28, 2027
- **Calendar Days:** 345 consecutive days (0 date gaps, 100% verified weekdays)
- **Total Scheduled Tasks:** 690 tasks (2 slots/day)
- **Sundays:** 49 Sundays (Slot 1: Weekly Test & Active Recall; Slot 2: PYQ Practice & Mistake Analysis)
- **Designated Buffer Days:** 12 catch-up days (9-day buffer in Jan 2027, 3-day buffer in Mar 2027)
- **Full-Syllabus Revision Days:** 15 days in Sep 2027
- **Saturday Consolidation Sessions:** 6 sessions
- **Milestones Preserved:** 23 subject completion and module start milestones
- **Sequence Numbering Errors:** 0 gaps across all subjects

---

## 🧪 Automated Test Results

Executed via `./gradlew testDebugUnitTest`:

```
TestClass                                  Tests Failures Errors Time 
---------                                  ----- -------- ------ ---- 
com.gatecse.companion.AnalyticsEngineTest  5     0        0      0.101s
com.gatecse.companion.RecoveryPlanningTest 4     0        0      0.003s
com.gatecse.companion.ScheduleDataTest     5     0        0      0.260s
com.gatecse.companion.TaskCompletionTest   3     0        0      0.003s
Total                                      17    0        0      0.367s
```
**VERDICT: PASS (17 tests passed, 0 failures, 0 errors)**

---

## 📦 Built Artifact

- **Debug APK Location:**
  `app/build/outputs/apk/debug/app-debug.apk`
- **File Size:** ~19.85 MB

---

## 🚀 How to Run the App

### Option 1: On an Android Device / Emulator via ADB
1. Connect your Android device via USB with **USB Debugging** enabled (or start an Android Virtual Device in Android Studio).
2. Install the APK:
   ```powershell
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Launch the app:
   ```powershell
   adb shell am start -n com.gatecse.companion.debug/com.gatecse.companion.MainActivity
   ```

### Option 2: In Android Studio
1. Open Android Studio.
2. Select **Open** and choose the directory: `gate-cse-companion`.
3. Let Gradle sync.
4. Click **Run 'app'** (Shift + F10) to deploy to your connected device or emulator.
