package com.gatecse.companion.ui.screens.calendar

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gatecse.companion.data.CandidateDateSlot
import com.gatecse.companion.data.RescheduleValidationResult
import com.gatecse.companion.data.ScheduleRepository
import com.gatecse.companion.model.CalendarDayUi
import com.gatecse.companion.model.TaskItemUi
import com.gatecse.companion.model.TaskStatus
import com.gatecse.companion.ui.components.CyberCard
import com.gatecse.companion.ui.components.CyberPillBadge
import com.gatecse.companion.ui.components.RescheduleBacklogDialog
import com.gatecse.companion.ui.components.TaskCard
import com.gatecse.companion.ui.theme.GateTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(
    repository: ScheduleRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = GateTheme.colors
    val kolkataZone = remember { ZoneId.of("Asia/Kolkata") }
    var todayDate by remember { mutableStateOf(LocalDate.now(kolkataZone)) }
    val todayDateStr = remember(todayDate) { todayDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }

    var currentYearMonth by remember {
        val initialYm = YearMonth.from(todayDate)
        val minYm = YearMonth.of(2026, 10)
        val maxYm = YearMonth.of(2027, 9)
        mutableStateOf(
            if (initialYm.isBefore(minYm)) minYm
            else if (initialYm.isAfter(maxYm)) maxYm
            else initialYm
        )
    }

    var selectedDateStr by remember { mutableStateOf(todayDateStr) }
    val allProgressList by repository.getAllProgressFlow().collectAsState(initial = emptyList())
    val activeRecoveriesList by repository.getActiveRecoveriesFlow().collectAsState(initial = emptyList())
    var monthDays by remember { mutableStateOf<List<CalendarDayUi>>(emptyList()) }
    var selectedDateTasks by remember { mutableStateOf<List<TaskItemUi>>(emptyList()) }
    var taskToReschedule by remember { mutableStateOf<TaskItemUi?>(null) }
    var candidateRescheduleDates by remember { mutableStateOf<List<String>>(emptyList()) }
    var candidateSlots by remember { mutableStateOf<List<CandidateDateSlot>>(emptyList()) }

    fun loadMonth() {
        scope.launch {
            monthDays = repository.getCalendarMonthDays(currentYearMonth, todayDateStr)
            candidateRescheduleDates = repository.getCandidateRescheduleDates(todayDateStr)
            candidateSlots = repository.getCandidateSlotsInfo(todayDateStr)
        }
    }

    fun loadSelectedTasks() {
        scope.launch {
            selectedDateTasks = repository.getTasksForDate(selectedDateStr, todayDateStr)
        }
    }

    // Lifecycle observer to handle date transition at 12 AM
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentNow = LocalDate.now(kolkataZone)
                if (currentNow != todayDate) {
                    todayDate = currentNow
                }
                loadMonth()
                loadSelectedTasks()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentYearMonth, allProgressList, activeRecoveriesList) {
        loadMonth()
    }

    LaunchedEffect(selectedDateStr, allProgressList, activeRecoveriesList) {
        loadSelectedTasks()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Month Header & Navigation with Quick "Today" jump
        item {
            CyberCard(
                borderHighlight = colors.sky,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            val prev = currentYearMonth.minusMonths(1)
                            if (!prev.isBefore(YearMonth.of(2026, 10))) {
                                currentYearMonth = prev
                            }
                        },
                        enabled = !currentYearMonth.minusMonths(1).isBefore(YearMonth.of(2026, 10))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Month",
                            tint = if (!currentYearMonth.minusMonths(1).isBefore(YearMonth.of(2026, 10))) colors.textPrimary else colors.textMuted
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // "Today" Quick-Jump Chip (Fixes "today isn't working")
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.sky.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, colors.sky.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable {
                                val targetYm = YearMonth.from(todayDate)
                                val minYm = YearMonth.of(2026, 10)
                                val maxYm = YearMonth.of(2027, 9)
                                currentYearMonth = when {
                                    targetYm.isBefore(minYm) -> minYm
                                    targetYm.isAfter(maxYm) -> maxYm
                                    else -> targetYm
                                }
                                selectedDateStr = todayDateStr
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(colors.sky)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "TODAY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.sky,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val next = currentYearMonth.plusMonths(1)
                            if (!next.isAfter(YearMonth.of(2027, 9))) {
                                currentYearMonth = next
                            }
                        },
                        enabled = !currentYearMonth.plusMonths(1).isAfter(YearMonth.of(2027, 9))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Month",
                            tint = if (!currentYearMonth.plusMonths(1).isAfter(YearMonth.of(2027, 9))) colors.textPrimary else colors.textMuted
                        )
                    }
                }
            }
        }

        // Calendar Grid Card
        item {
            CyberCard(
                borderHighlight = colors.sky,
                borderSecondary = colors.cardBorder,
                contentPadding = PaddingValues(14.dp)
            ) {
                // Day names: Mon Tue Wed Thu Fri Sat Sun
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMuted,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Calendar days grid
                val firstDayOfMonth = currentYearMonth.atDay(1)
                val dayOfWeekOffset = (firstDayOfMonth.dayOfWeek.value - 1)
                val totalSlots = dayOfWeekOffset + currentYearMonth.lengthOfMonth()
                val totalRows = (totalSlots + 6) / 7

                for (row in 0 until totalRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (col in 0..6) {
                            val slotIndex = row * 7 + col
                            val dayNum = slotIndex - dayOfWeekOffset + 1

                            if (dayNum in 1..currentYearMonth.lengthOfMonth()) {
                                val dateStr = currentYearMonth.atDay(dayNum).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                val dayData = monthDays.firstOrNull { it.date == dateStr }
                                val isSelected = dateStr == selectedDateStr

                                CalendarDayCell(
                                    dayNum = dayNum,
                                    dayData = dayData,
                                    isSelected = isSelected,
                                    onClick = { selectedDateStr = dateStr },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // Status Legend Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.cardElevated.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, colors.cardBorder.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(color = colors.emerald, text = "Done")
                    LegendItem(color = colors.rose, text = "Missed")
                    LegendItem(color = colors.amber, text = "Test/Rec")
                    LegendItem(color = colors.sky, text = "Buffer")
                    LegendItem(color = colors.cardBorder, text = "Upcoming")
                }
            }
        }

        // Selected Date Tasks Header
        item {
            val selectedDateObj = try {
                LocalDate.parse(selectedDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e: Exception) {
                todayDate
            }
            val formattedHeader = selectedDateObj.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    CyberPillBadge(text = "TASKS FOR THIS DATE", accentColor = colors.sky)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formattedHeader,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${selectedDateTasks.size} tasks",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            }
        }

        // Selected Date Task Items
        if (selectedDateTasks.isEmpty()) {
            item {
                CyberCard(
                    borderHighlight = colors.cardBorder,
                    contentPadding = PaddingValues(28.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks scheduled for this date.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textMuted
                        )
                    }
                }
            }
        } else {
            items(selectedDateTasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onToggleComplete = { isComplete, notes ->
                        if (task.displayDate == todayDateStr || !isComplete) {
                            scope.launch {
                                repository.markTaskComplete(task.id, isComplete, todayDateStr, notes)
                                loadMonth()
                                loadSelectedTasks()
                            }
                        }
                    },
                    onReschedule = run {
                        val canReschedule = !task.isBooster &&
                            !task.isRevision &&
                            !task.isSundayActivity &&
                            !task.isCustom &&
                            !task.subject.contains("Revision", ignoreCase = true) &&
                            !task.subject.contains("Problem", ignoreCase = true) &&
                            !task.title.contains("Revision", ignoreCase = true) &&
                            !task.title.contains("Problem", ignoreCase = true) &&
                            (task.status == TaskStatus.MISSED || task.status == TaskStatus.RECOVERY_SCHEDULED || task.isAssignedForRecovery || (!task.isFuture && !task.isCompleted))

                        if (canReschedule) {
                            {
                                scope.launch {
                                    candidateRescheduleDates = repository.getCandidateRescheduleDates(todayDateStr)
                                    candidateSlots = repository.getCandidateSlotsInfo(todayDateStr)
                                    taskToReschedule = task
                                }
                            }
                        } else null
                    }
                )
            }
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
                    val result = repository.updateRecoveryDate(task.id, newDate)
                    if (result is RescheduleValidationResult.Success) {
                        taskToReschedule = null
                        loadMonth()
                        loadSelectedTasks()
                    } else if (result is RescheduleValidationResult.Error) {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun CalendarDayCell(
    dayNum: Int,
    dayData: CalendarDayUi?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GateTheme.colors
    val isToday = dayData?.isToday == true

    val statusDotColor = when {
        dayData == null -> Color.Transparent
        dayData.completedTasks == dayData.totalTasks && dayData.totalTasks > 0 -> colors.emerald
        dayData.missedTasks > 0 -> colors.rose
        dayData.recoveryTasks > 0 -> colors.amber
        dayData.isSunday -> colors.amber
        dayData.isBuffer -> colors.sky
        dayData.isPast && dayData.completedTasks > 0 -> colors.emerald.copy(alpha = 0.6f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) colors.primary.copy(alpha = 0.28f)
                else if (isToday) colors.cardBorder.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .border(
                BorderStroke(
                    if (isSelected) 1.5.dp else if (isToday) 1.dp else 0.dp,
                    if (isSelected) colors.primary else if (isToday) colors.sky.copy(alpha = 0.8f) else Color.Transparent
                ),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$dayNum",
                fontSize = 12.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) colors.primary else if (isToday) colors.sky else colors.textPrimary
            )
            if (statusDotColor != Color.Transparent) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.5.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    val colors = GateTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 10.sp,
            color = colors.textMuted
        )
    }
}
