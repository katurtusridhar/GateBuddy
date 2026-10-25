package com.gatecse.companion.ui.screens.today

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gatecse.companion.R
import com.gatecse.companion.data.CandidateDateSlot
import com.gatecse.companion.data.RescheduleValidationResult
import com.gatecse.companion.data.ScheduleRepository
import com.gatecse.companion.model.AssessmentResultEntity
import com.gatecse.companion.model.PreparationAdherenceUi
import com.gatecse.companion.model.SubjectRegistry
import com.gatecse.companion.model.TaskItemUi
import com.gatecse.companion.model.TaskType
import com.gatecse.companion.model.UserSettingsEntity
import com.gatecse.companion.ui.components.BacklogLockCard
import com.gatecse.companion.ui.components.BacklogSequentialSchedulerDialog
import com.gatecse.companion.ui.components.HealthStatusBadge
import com.gatecse.companion.ui.components.MetricStatCard
import com.gatecse.companion.ui.components.ProgressRing
import com.gatecse.companion.ui.components.RescheduleBacklogDialog
import com.gatecse.companion.ui.components.TaskCard
import com.gatecse.companion.ui.theme.GateTheme
import com.gatecse.companion.widget.WidgetUpdateHelper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    repository: ScheduleRepository,
    onNavigateToCalendar: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToAchievements: () -> Unit = {},
    refreshTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = GateTheme.colors
    val kolkataZone = remember { ZoneId.of("Asia/Kolkata") }
    var todayDate by remember { mutableStateOf(LocalDate.now(kolkataZone)) }
    val todayDateStr = remember(todayDate) { todayDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
    val displayDateHeader = remember(todayDate) { todayDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) }

    var todayTasks by remember { mutableStateOf<List<TaskItemUi>>(emptyList()) }
    var unrescheduledBacklogs by remember { mutableStateOf<List<TaskItemUi>>(emptyList()) }
    var adherenceMetrics by remember { mutableStateOf<PreparationAdherenceUi?>(null) }
    val userSettings by repository.getUserSettingsFlow().collectAsState(initial = null)
    val allProgressList by repository.getAllProgressFlow().collectAsState(initial = emptyList())
    val activeRecoveriesList by repository.getActiveRecoveriesFlow().collectAsState(initial = emptyList())

    var showExamDatePicker by remember { mutableStateOf(false) }
    var showAddCustomTaskDialog by remember { mutableStateOf(false) }
    var showEnterTestMarksDialog by remember { mutableStateOf(false) }
    var showBatchSchedulerDialog by remember { mutableStateOf(false) }
    var taskToReschedule by remember { mutableStateOf<TaskItemUi?>(null) }
    var candidateRescheduleDates by remember { mutableStateOf<List<String>>(emptyList()) }
    var candidateSlots by remember { mutableStateOf<List<CandidateDateSlot>>(emptyList()) }

    fun refreshData() {
        scope.launch {
            repository.reconcileRecovery(todayDateStr, userSettings?.dailyRecoveryLimit ?: 2)
            todayTasks = repository.getTasksForDate(todayDateStr, todayDateStr)
            unrescheduledBacklogs = repository.getUnrescheduledBacklogs(todayDateStr)
            candidateRescheduleDates = repository.getCandidateRescheduleDates(todayDateStr)
            candidateSlots = repository.getCandidateSlotsInfo(todayDateStr)
            adherenceMetrics = repository.getPreparationAdherence(todayDateStr)
            WidgetUpdateHelper.updateAllWidgets(context)
        }
    }

    // Lifecycle observer: ALWAYS refreshes data when screen is resumed/navigated to and checks for midnight date transition
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentNow = LocalDate.now(kolkataZone)
                if (currentNow != todayDate) {
                    todayDate = currentNow
                }
                refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(todayDateStr, userSettings?.dailyRecoveryLimit, allProgressList, activeRecoveriesList) {
        refreshData()
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0L) {
            refreshData()
        }
    }

    val isRestricted = unrescheduledBacklogs.isNotEmpty()
    val completedCount = todayTasks.count { it.isCompleted }
    val totalCount = todayTasks.size
    val dailyPercent = if (totalCount > 0) ((completedCount.toDouble() / totalCount.toDouble()) * 100).toInt() else 0

    val targetExamDateStr = userSettings?.targetExamDate
    val daysUntilExam: Long? = if (!targetExamDateStr.isNullOrEmpty()) {
        try {
            val examDate = LocalDate.parse(targetExamDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            ChronoUnit.DAYS.between(todayDate, examDate).coerceAtLeast(0)
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }

    val isSunday = todayDate.dayOfWeek.name == "SUNDAY"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. App Header with IIT Bombay emblem logo and title "GateBuddy"
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.size(48.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "IIT Bombay Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hello, Sridhar 👋",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Target: IIT Bombay CSE • AIR < 100",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary
                    )
                }

                // Daily Points Pill (Requirement 5)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Score",
                            tint = colors.amber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${adherenceMetrics?.dailyPointsToday ?: 0} pts",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
            }
        }

        // 2. BACKLOG LOCKOUT BARRIER (Requirement 2: Restrict all other activity until backlogs are rescheduled)
        if (isRestricted) {
            item {
                BacklogLockCard(
                    backlogCount = unrescheduledBacklogs.size,
                    onOpenScheduler = { showBatchSchedulerDialog = true }
                )
            }
        }

        // 3. GATE Countdown Card (Luxury Cyber-Slate Glassmorphic)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = Color.Transparent,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            colors.sky.copy(alpha = 0.45f),
                            colors.cardBorder,
                            colors.primary.copy(alpha = 0.25f)
                        )
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.card,
                                    colors.cardElevated.copy(alpha = 0.95f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Target pill tag
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.sky.copy(alpha = 0.12f),
                                border = BorderStroke(0.5.dp, colors.sky.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(colors.sky)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = (userSettings?.targetExamName ?: "TARGET GATE CSE").uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.sky,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (daysUntilExam != null) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "$daysUntilExam",
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.sky
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.padding(bottom = 5.dp)) {
                                        Text(
                                            text = "DAYS REMAINING",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textSecondary,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                                Text(
                                    text = "Exam Date: $targetExamDateStr",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textMuted
                                )
                            } else {
                                Text(
                                    text = "Exam Date Not Set",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.amber
                                )
                                Text(
                                    text = "Configure target date for live countdown",
                                    fontSize = 11.sp,
                                    color = colors.textMuted
                                )
                            }
                        }

                        // Glass action button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.primary.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { showExamDatePicker = true }
                        ) {
                            Text(
                                text = if (daysUntilExam != null) "Change" else "Set Date",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.sky,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Daily Execution Ring & Point System Overview (Luxury $5000 Upgrade)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = Color.Transparent,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            colors.emerald.copy(alpha = 0.45f),
                            colors.cardBorder,
                            colors.sky.copy(alpha = 0.3f)
                        )
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.card,
                                    colors.cardElevated.copy(alpha = 0.95f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProgressRing(
                            progressPercent = dailyPercent,
                            completedCount = completedCount,
                            totalCount = totalCount
                        )

                        Spacer(modifier = Modifier.width(18.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            // Top Tag Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.emerald.copy(alpha = 0.12f),
                                    border = BorderStroke(0.5.dp, colors.emerald.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(colors.emerald)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "SRIDHAR'S MISSION",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.emerald,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Text(
                                    text = "+30 pts max",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.amber
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (dailyPercent == 100 && totalCount > 0) "All Slots Conquered! 🏆" else "$completedCount of $totalCount Done",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (isRestricted) {
                                    "⚠️ Restricted: Reschedule backlogs first"
                                } else if (dailyPercent == 100) {
                                    "Outstanding consistency! Points logged."
                                } else {
                                    "Lec (+10) • Rev (+5) • PS (+5)"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isRestricted) colors.rose else colors.textMuted
                            )
                        }
                    }
                }
            }
        }

        // 5. Stat Row: 7-Day Average Points & Backlog Count
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    title = "7-Day Avg Score",
                    value = "${adherenceMetrics?.averageDailyPoints?.toInt() ?: 0} pts",
                    subtitle = "Rolling daily points",
                    accentColor = colors.primary,
                    modifier = Modifier.weight(1f)
                )

                MetricStatCard(
                    title = "Backlog Status",
                    value = "${unrescheduledBacklogs.size + (adherenceMetrics?.currentBacklog ?: 0)}",
                    subtitle = if (unrescheduledBacklogs.isEmpty() && (adherenceMetrics?.currentBacklog ?: 0) == 0) "Zero debt • Flawless" else "Recovery active",
                    accentColor = if (unrescheduledBacklogs.isEmpty() && (adherenceMetrics?.currentBacklog ?: 0) == 0) colors.emerald else colors.rose,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 6. Preparation Health Status Card (Navigates to Analytics graph)
        if (adherenceMetrics != null) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onNavigateToAnalytics() },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                colors.primary.copy(alpha = 0.35f),
                                colors.cardBorder,
                                colors.sky.copy(alpha = 0.2f)
                            )
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        colors.card,
                                        colors.cardElevated.copy(alpha = 0.9f)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "PREPARATION HEALTH",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    HealthStatusBadge(health = adherenceMetrics!!.health)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isRestricted) "Action Required: Reschedule ${unrescheduledBacklogs.size} overdue lectures." else adherenceMetrics!!.healthReason,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Details",
                                tint = colors.sky,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }


        // 8. Section Header: Today's Tasks + Add Custom Task Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSunday) "SUNDAY ACTIVITIES" else "TODAY'S SCHEDULED SLOTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.sky,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))

                // "+ Add Task" Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.primaryContainer,
                    modifier = Modifier.clickable {
                        if (isRestricted) {
                            showBatchSchedulerDialog = true
                            Toast.makeText(context, "Reschedule backlogs first to continue.", Toast.LENGTH_SHORT).show()
                        } else {
                            showAddCustomTaskDialog = true
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Custom Task",
                            tint = colors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add Task",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
            }
        }

        // 8. Task Cards
        if (todayTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.card),
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Tasks Scheduled Today",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your calendar runs October 19, 2026 to September 28, 2027.",
                            fontSize = 12.sp,
                            color = colors.textMuted
                        )
                    }
                }
            }
        } else {
            items(todayTasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    isRestricted = isRestricted,
                    onRestrictedClick = {
                        showBatchSchedulerDialog = true
                        Toast.makeText(context, "Please reschedule all backlogs before recording today's work.", Toast.LENGTH_SHORT).show()
                    },
                    onToggleComplete = { isComplete, notes ->
                        if (isRestricted && isComplete) {
                            showBatchSchedulerDialog = true
                            Toast.makeText(context, "Backlog resolution required first.", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                repository.markTaskComplete(task.id, isComplete, todayDateStr, notes)
                                refreshData()
                            }
                        }
                    },
                    onEnterMarks = if (task.type == TaskType.SUNDAY_TEST) {
                        { showEnterTestMarksDialog = true }
                    } else null,
                    onDeleteCustomTask = if (task.isCustom) {
                        {
                            val customId = task.id.removePrefix("CUSTOM_").toLongOrNull()
                            if (customId != null) {
                                scope.launch {
                                    repository.deleteCustomTask(customId)
                                    refreshData()
                                }
                            }
                        }
                    } else null,
                    onReschedule = run {
                        val canReschedule = !task.isBooster &&
                            !task.isRevision &&
                            !task.isSundayActivity &&
                            !task.isCustom &&
                            !task.subject.contains("Revision", ignoreCase = true) &&
                            !task.subject.contains("Problem", ignoreCase = true) &&
                            !task.title.contains("Revision", ignoreCase = true) &&
                            !task.title.contains("Problem", ignoreCase = true)

                        if (task.isAssignedForRecovery && canReschedule) {
                            {
                                scope.launch {
                                    candidateRescheduleDates = repository.getCandidateRescheduleDates(todayDateStr)
                                    taskToReschedule = task
                                }
                            }
                        } else null
                    }
                )
            }
        }
    }

    // Dialog: Sequential Backlog Resolution (Requirement 2)
    if (showBatchSchedulerDialog && unrescheduledBacklogs.isNotEmpty()) {
        BacklogSequentialSchedulerDialog(
            backlogs = unrescheduledBacklogs,
            candidateDates = candidateRescheduleDates,
            candidateSlots = candidateSlots,
            onDismiss = { showBatchSchedulerDialog = false },
            onConfirmAll = { assignments ->
                scope.launch {
                    val result = repository.updateBatchRecoveryDates(assignments, todayDateStr)
                    when (result) {
                        is RescheduleValidationResult.Success -> {
                            unrescheduledBacklogs = emptyList()
                            showBatchSchedulerDialog = false
                            refreshData()
                            Toast.makeText(context, "All backlogs scheduled! Previous schedule intact.", Toast.LENGTH_LONG).show()
                        }
                        is RescheduleValidationResult.Error -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    // Dialog: Record Test Marks & Mistake Log (Requirement 1 & 12)
    if (showEnterTestMarksDialog) {
        var testName by remember { mutableStateOf("Weekly Test & Active Recall") }
        var subjectCode by remember { mutableStateOf("DL") }
        var marksObtained by remember { mutableStateOf("") }
        var maxMarks by remember { mutableStateOf("50") }
        var mistakesAnalysis by remember { mutableStateOf("") }
        val commonSubjects = remember { listOf("DL", "CN", "DBMS", "C", "OS", "COA", "GA", "EM") }

        AlertDialog(
            onDismissRequest = { showEnterTestMarksDialog = false },
            title = { Text("Enter Test Score & Mistake Log") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subjectCode,
                        onValueChange = { subjectCode = it.uppercase() },
                        label = { Text("Subject Code (e.g. DL, CN, DBMS)") },
                        placeholder = { Text("Enter subject code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Quick subject chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        commonSubjects.forEach { code ->
                            val isSelected = subjectCode.equals(code, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.primary.copy(alpha = 0.25f) else colors.card,
                                border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.cardBorder),
                                modifier = Modifier.clickable { subjectCode = code }
                            ) {
                                Text(
                                    text = code,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colors.sky else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = testName,
                        onValueChange = { testName = it },
                        label = { Text("Test Name / Topic") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = marksObtained,
                            onValueChange = { marksObtained = it },
                            label = { Text("Marks") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = maxMarks,
                            onValueChange = { maxMarks = it },
                            label = { Text("Max Marks") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = mistakesAnalysis,
                        onValueChange = { mistakesAnalysis = it },
                        label = { Text("Mistake Notebook Analysis") },
                        placeholder = { Text("Log silly mistakes, formula gaps, and time pressure issues...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val score = marksObtained.toDoubleOrNull() ?: 0.0
                        val max = maxMarks.toDoubleOrNull() ?: 50.0
                        val pct = if (max > 0) (score / max) * 100.0 else 0.0
                        val cleanCode = subjectCode.trim().uppercase().ifBlank { "TEST" }
                        val subjectDisplayName = SubjectRegistry.getSubjectInfoByCode(cleanCode)?.name ?: cleanCode

                        scope.launch {
                            repository.recordAssessment(
                                AssessmentResultEntity(
                                    testDate = todayDateStr,
                                    testName = testName.ifBlank { "Sunday Assessment ($cleanCode)" },
                                    subjectName = subjectDisplayName,
                                    marksObtained = score,
                                    maxMarks = max,
                                    percentage = pct,
                                    mistakesAnalysis = mistakesAnalysis
                                )
                            )
                            val sundayTestTask = todayTasks.firstOrNull { it.type == TaskType.SUNDAY_TEST }
                            if (sundayTestTask != null) {
                                repository.markTaskComplete(sundayTestTask.id, true, todayDateStr, "[$cleanCode] Marks: $score/$max")
                            }
                            showEnterTestMarksDialog = false
                            refreshData()
                            Toast.makeText(context, "Test score & mistakes recorded for $cleanCode!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save Score & Mistake Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnterTestMarksDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Dialog: Add Custom Task (Requirement 13)
    if (showAddCustomTaskDialog) {
        var taskTitle by remember { mutableStateOf("") }
        var selectedCode by remember { mutableStateOf("PRB") }
        var customNotes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCustomTaskDialog = false },
            title = { Text("Add Custom Preparation Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        label = { Text("Task Description") },
                        placeholder = { Text("e.g. Extra 20 PYQ on Dijkstra Algorithm") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Subject Code:", fontSize = 12.sp, color = colors.textSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("DL", "CN", "DBMS", "OS", "DS", "ALGO", "PRB").forEach { code ->
                            val isSel = selectedCode == code
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) colors.primary else colors.cardBorder.copy(alpha = 0.3f),
                                modifier = Modifier.clickable { selectedCode = code }
                            ) {
                                Text(
                                    text = code,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else colors.textPrimary
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customNotes,
                        onValueChange = { customNotes = it },
                        label = { Text("Notes / Target") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (taskTitle.isNotBlank()) {
                            scope.launch {
                                repository.addCustomTask(
                                    date = todayDateStr,
                                    title = taskTitle,
                                    subjectCode = selectedCode,
                                    notes = customNotes,
                                    pointsValue = 5
                                )
                                refreshData()
                                Toast.makeText(context, "Task added (+5 pts)!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showAddCustomTaskDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Add Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomTaskDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Exam Date Picker Dialog
    if (showExamDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = Instant.now().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showExamDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val selectedDate = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            val formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            scope.launch {
                                val currentSettings = userSettings ?: UserSettingsEntity()
                                repository.saveUserSettings(
                                    currentSettings.copy(targetExamDate = formattedDate)
                                )
                                WidgetUpdateHelper.updateAllWidgets(context)
                            }
                        }
                        showExamDatePicker = false
                    }
                ) {
                    Text("Save", color = colors.sky)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExamDatePicker = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Dialog: Reschedule Backlog Task
    taskToReschedule?.let { task ->
        RescheduleBacklogDialog(
            task = task,
            candidateDates = candidateRescheduleDates,
            candidateSlots = candidateSlots,
            onDismiss = { taskToReschedule = null },
            onConfirm = { newDate ->
                scope.launch {
                    val result = repository.updateRecoveryDate(task.id, newDate, todayDateStr)
                    when (result) {
                        is RescheduleValidationResult.Success -> {
                            taskToReschedule = null
                            refreshData()
                            Toast.makeText(context, "Rescheduled to $newDate! Previous schedule intact.", Toast.LENGTH_SHORT).show()
                        }
                        is RescheduleValidationResult.Error -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }
}
