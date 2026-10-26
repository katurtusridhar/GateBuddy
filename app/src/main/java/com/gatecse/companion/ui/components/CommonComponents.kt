package com.gatecse.companion.ui.components

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.gatecse.companion.data.CandidateDateSlot
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatecse.companion.model.PreparationHealth
import com.gatecse.companion.model.TaskItemUi
import com.gatecse.companion.model.TaskStatus
import com.gatecse.companion.model.TaskType
import com.gatecse.companion.ui.theme.GateTheme

@Composable
fun TaskCard(
    task: TaskItemUi,
    onToggleComplete: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier,
    isRestricted: Boolean = false,
    onRestrictedClick: (() -> Unit)? = null,
    onEnterMarks: (() -> Unit)? = null,
    onDeleteCustomTask: (() -> Unit)? = null,
    onReschedule: (() -> Unit)? = null
) {
    var showNotesDialog by remember { mutableStateOf(false) }
    var currentNotes by remember(task.userNotes) { mutableStateOf(task.userNotes) }
    val view = LocalView.current
    val colors = GateTheme.colors

    val borderColor by animateColorAsState(
        targetValue = when {
            isRestricted -> colors.rose.copy(alpha = 0.5f)
            task.isCompleted -> colors.emerald.copy(alpha = 0.6f)
            else -> colors.cardBorder
        },
        animationSpec = tween(300),
        label = "borderColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (task.isCompleted) colors.emerald.copy(alpha = 0.6f)
            else if (isRestricted) colors.rose.copy(alpha = 0.6f)
            else colors.cardBorder
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
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
            // Header Row: Slot / Category badge, Subject tag, and Status chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Badge
                val (slotLabel, slotBg, slotTextColor) = when {
                    task.isAssignedForRecovery -> Triple("CATCH-UP (+10)", colors.amberContainer, colors.amber)
                    task.type == TaskType.SUNDAY_TEST -> Triple("SUNDAY TEST", colors.amberContainer, colors.amber)
                    task.isBooster -> Triple("BOOSTER (+5)", colors.emeraldContainer, colors.emerald)
                    task.isCustom -> Triple("CUSTOM (+${task.pointsValue})", colors.primaryContainer, colors.primary)
                    task.isSundayActivity -> Triple("SUNDAY PYQ", colors.amberContainer, colors.amber)
                    else -> Triple("SLOT ${task.slot}", colors.primaryContainer, colors.primary)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = slotBg
                ) {
                    Text(
                        text = slotLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = slotTextColor
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Subject Pill with Subject Code
                if (task.subjectCode.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.skyContainer
                    ) {
                        Text(
                            text = "[${task.subjectCode}] ${task.subject}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.sky,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Status chip (Strictly BACKLOG only for missed core lectures; MISSED for Sunday tests/boosters/revisions)
                val isLecture = !task.isSundayActivity &&
                    !task.isBuffer &&
                    !task.isRevision &&
                    !task.isBooster &&
                    !task.isCustom &&
                    (task.type == TaskType.RECORDED || task.type == TaskType.LIVE) &&
                    !task.subject.contains("Revision", ignoreCase = true) &&
                    !task.subject.contains("Problem", ignoreCase = true) &&
                    !task.title.contains("Revision", ignoreCase = true) &&
                    !task.title.contains("Problem", ignoreCase = true)

                TaskStatusBadge(status = task.status, isBooster = task.isBooster, isLecture = isLecture)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Entire Title & Details row is clickable! (Fixes clicking on task not working)
            val isChecked = task.isCompleted
            val checkBgColor by animateColorAsState(
                targetValue = if (isChecked) colors.emerald else Color.Transparent,
                animationSpec = tween(250),
                label = "checkBgColor"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (isChecked) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onToggleComplete(false, currentNotes)
                        } else if (isRestricted) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onRestrictedClick?.invoke()
                        } else if (!task.isFuture && !task.isPast) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onToggleComplete(true, currentNotes)
                        } else {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayDetails = when {
                    task.details.isBlank() || task.details.lowercase().contains("lecture ${task.curNum} of") || task.details.lowercase() == task.title.lowercase() -> {
                        if (task.isAssignedForRecovery) "Sunday catch-up slot • +10 points on completion"
                        else "Core GATE CSE lecture • Slot ${task.slot} • +10 points"
                    }
                    else -> task.details
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (task.isCompleted) colors.textSecondary else colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayDetails,
                        fontSize = 12.sp,
                        color = colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Interactive Completion Toggle or Past/Future/Restricted Lock
                if (isChecked) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.emerald)
                            .border(BorderStroke(2.dp, colors.emerald), CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onToggleComplete(false, currentNotes)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed • Click to unselect",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (isRestricted) {
                    Surface(
                        shape = CircleShape,
                        color = colors.roseContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.5.dp, colors.rose),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onRestrictedClick?.invoke()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Restricted: Reschedule backlogs first",
                                tint = colors.rose,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else if (task.isFuture) {
                    Surface(
                        shape = CircleShape,
                        color = colors.cardBorder.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Future task locked",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else if (task.isPast) {
                    Surface(
                        shape = CircleShape,
                        color = colors.roseContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.5.dp, colors.rose.copy(alpha = 0.7f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Missed in past (Locked)",
                                tint = colors.rose,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.cardBorder.copy(alpha = 0.2f))
                            .border(BorderStroke(2.dp, colors.cardBorder.copy(alpha = 0.7f)), CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onToggleComplete(true, currentNotes)
                            },
                        contentAlignment = Alignment.Center
                    ) {}
                }
            }

            // Enter Marks Button on Sunday Test Card (Requirement 12)
            if (task.type == TaskType.SUNDAY_TEST && onEnterMarks != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onEnterMarks,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = "Enter Marks",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Enter Test Marks & Mistake Log",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Milestone Banner if present (only when milestone non-empty)
            if (task.milestone.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.emeraldContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Milestone",
                            tint = colors.emerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = task.milestone,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.emerald
                        )
                    }
                }
            }

            // Reschedule banner for Missed Backlogs or Recovery Tasks (Never for Revision, Problem Solving, or Boosters)
            val canReschedule = !task.isBooster &&
                !task.isRevision &&
                !task.isSundayActivity &&
                !task.isCustom &&
                !task.subject.contains("Revision", ignoreCase = true) &&
                !task.subject.contains("Problem", ignoreCase = true) &&
                !task.title.contains("Revision", ignoreCase = true) &&
                !task.title.contains("Problem", ignoreCase = true)

            if (canReschedule && (task.status == TaskStatus.MISSED || task.status == TaskStatus.RECOVERY_SCHEDULED || task.isAssignedForRecovery) && onReschedule != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isRecovery = task.isAssignedForRecovery || task.status == TaskStatus.RECOVERY_SCHEDULED
                val alertText = if (isRecovery && task.recoveryDate != null) {
                    if (task.displayDate == task.recoveryDate) "Catch-up lecture originally from ${task.originalDate}"
                    else "Rescheduled to: ${task.recoveryDate}"
                } else {
                    "Backlog: Tap to assign recovery date"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isRecovery) colors.amberContainer.copy(alpha = 0.4f) else colors.roseContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (isRecovery) colors.amber.copy(alpha = 0.5f) else colors.rose.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReschedule() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Reschedule",
                            tint = if (isRecovery) colors.amber else colors.rose,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = alertText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecovery) colors.amber else colors.rose,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = (if (isRecovery) colors.amber else colors.rose).copy(alpha = 0.25f),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = if (isRecovery) "Change" else "Reschedule",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecovery) colors.amber else colors.rose
                            )
                        }
                    }
                }
            } else if (task.isAssignedForRecovery && task.recoveryDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val recoveryAlertText = if (task.displayDate == task.recoveryDate) {
                    "Catch-up lecture originally from ${task.originalDate}"
                } else {
                    "Rescheduled to: ${task.recoveryDate}"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.amberContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Recovery",
                            tint = colors.amber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = recoveryAlertText,
                            fontSize = 11.sp,
                            color = colors.amber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Notes and Action Row
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.userNotes.isNotEmpty()) {
                    Text(
                        text = "Note: ${task.userNotes}",
                        fontSize = 11.sp,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showNotesDialog = true }
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Edit Note button
                IconButton(
                    onClick = { showNotesDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Edit Notes",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete button for custom tasks
                if (task.isCustom && onDeleteCustomTask != null) {
                    IconButton(
                        onClick = onDeleteCustomTask,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Task",
                            tint = colors.rose,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

    // Notes Dialog
    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = {
                Text(
                    text = "Study Notes: ${task.title}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = currentNotes,
                    onValueChange = { currentNotes = it },
                    placeholder = { Text("Add key formulas, doubts, or lecture timestamps...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleComplete(task.isCompleted, currentNotes)
                        showNotesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save Notes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.card,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Prominent restriction banner displayed when unrescheduled backlogs exist.
 * The app restricts recording today's work until missed tasks are scheduled.
 */
@Composable
fun BacklogLockCard(
    backlogCount: Int,
    onOpenScheduler: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GateTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                listOf(
                    colors.rose.copy(alpha = 0.85f),
                    colors.cardBorder,
                    colors.rose.copy(alpha = 0.4f)
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
                            colors.roseContainer.copy(alpha = 0.25f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = colors.rose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        CyberPillBadge(text = "BACKLOG RESTRICTION", accentColor = colors.rose)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "$backlogCount Unrescheduled Lecture(s)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "As per GATE preparation guidelines, all backlogs must be manually rescheduled before you can record today's tasks. Lectures will be executed in strict sequence.",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onOpenScheduler,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.rose),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reschedule Backlogs Now (Sequential)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Sequential Backlog Scheduler Dialog
 * Strictly walks through every pending backlog in sequence (curNum ASC),
 * while enforcing:
 * 1) Only 2 lectures per day maximum (Rule 1).
 * 2) 2 subjects cannot be the same on the same day (Rule 2).
 * 3) All previous schedules for other tasks remain completely intact.
 * 4) Provides custom date rescheduling option for every backlog item.
 */
@Composable
fun BacklogSequentialSchedulerDialog(
    backlogs: List<TaskItemUi>,
    candidateDates: List<String>,
    candidateSlots: List<CandidateDateSlot> = emptyList(),
    onDismiss: () -> Unit,
    onConfirmAll: (Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    val colors = GateTheme.colors
    val kolkataZone = remember { ZoneId.of("Asia/Kolkata") }
    val today = remember { LocalDate.now(kolkataZone) }

    val sortedBacklogs = remember(backlogs) {
        backlogs.sortedWith(compareBy<TaskItemUi> { it.subject }.thenBy { it.curNum })
    }

    // Map of taskId -> selected recovery date, pre-seeded respecting existing schedules & Rule 1 / Rule 2
    val assignedDates = remember(backlogs, candidateDates, candidateSlots) {
        mutableStateMapOf<String, String>().apply {
            val dateCounts = candidateSlots.associate { it.date to it.currentCount }.toMutableMap()
            val dateSubjects = candidateSlots.associate { it.date to it.existingSubjects.map { s -> s.lowercase().trim() }.toMutableSet() }.toMutableMap()

            val bySubject = sortedBacklogs.groupBy { it.subject }
            for ((subject, list) in bySubject) {
                val subjLower = subject.lowercase().trim()
                var startIdx = 0
                for (t in list) {
                    var foundDate: String? = null
                    for (i in startIdx until candidateDates.size) {
                        val d = candidateDates[i]
                        val count = dateCounts.getOrDefault(d, 0)
                        val subjects = dateSubjects.getOrPut(d) { mutableSetOf() }
                        // Rule 1: < 2 lectures. Rule 2: 2 subjects cannot be the same
                        if (count < 2 && !subjects.contains(subjLower)) {
                            foundDate = d
                            dateCounts[d] = count + 1
                            subjects.add(subjLower)
                            startIdx = i + 1 // Sequence guarantee: Next lecture of this subject must be on a strictly later date
                            break
                        }
                    }
                    val assigned = foundDate ?: candidateDates.lastOrNull().orEmpty()
                    put(t.id, assigned)
                }
            }
        }
    }

    // State for Custom Date Input Dialog
    var taskForCustomDate by remember { mutableStateOf<TaskItemUi?>(null) }
    var customDateInput by remember { mutableStateOf("") }
    var customDateError by remember { mutableStateOf<String?>(null) }
    var validationErrorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = colors.amber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Backlog Recovery Rescheduler",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${backlogs.size} lecture(s) • Max 2/day • Unique subjects",
                        fontSize = 11.sp,
                        color = colors.textMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                // Rule Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.emeraldContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.emerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Rules Enforced: 1) Max 2 lectures per day. 2) No 2 lectures of the same subject on the same day. Previous schedules remain intact.",
                            fontSize = 11.sp,
                            color = colors.emerald,
                            lineHeight = 15.sp
                        )
                    }
                }

                if (validationErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.roseContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, colors.rose),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = colors.rose, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = validationErrorMessage!!, fontSize = 11.sp, color = colors.rose, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedBacklogs, key = { it.id }) { task ->
                        val currentDate = assignedDates[task.id].orEmpty()
                        val isCustomAssigned = currentDate.isNotEmpty() && !candidateDates.contains(currentDate)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.cardElevated.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.cardBorder.copy(alpha = 0.7f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.skyContainer
                                    ) {
                                        Text(
                                            text = "[${task.subjectCode}]",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.sky
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = task.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Original Date: ${task.originalDate} • Scheduled: $currentDate",
                                    fontSize = 11.sp,
                                    color = if (currentDate.isNotEmpty()) colors.amber else colors.textMuted,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Assign Recovery Date:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                // Candidate chips + Custom Date option
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    candidateDates.take(3).forEach { date ->
                                        val isSelected = currentDate == date
                                        val countOnDate = (candidateSlots.find { it.date == date }?.currentCount ?: 0) +
                                            assignedDates.entries.count { it.key != task.id && it.value == date }
                                        val isSameSubj = (candidateSlots.find { it.date == date }?.existingSubjects?.any { it.equals(task.subject, ignoreCase = true) } == true) ||
                                            assignedDates.entries.any {
                                                it.key != task.id && it.value == date &&
                                                    sortedBacklogs.find { b -> b.id == it.key }?.subject?.equals(task.subject, ignoreCase = true) == true
                                            }
                                        val isAvailable = countOnDate < 2 && !isSameSubj

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) colors.primary else if (!isAvailable) colors.cardElevated.copy(alpha = 0.4f) else colors.cardElevated,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) colors.primary else if (!isAvailable) colors.cardBorder.copy(alpha = 0.3f) else colors.cardBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    if (isSelected) return@clickable
                                                    if (countOnDate >= 2) {
                                                        Toast.makeText(context, "Rule 1: $date already has 2 lectures.", Toast.LENGTH_SHORT).show()
                                                    } else if (isSameSubj) {
                                                        Toast.makeText(context, "Rule 2: A lecture for ${task.subject} is already on $date.", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        assignedDates[task.id] = date
                                                        validationErrorMessage = null
                                                    }
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = date.removePrefix("2026-").removePrefix("2027-"),
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.White else if (!isAvailable) colors.textMuted.copy(alpha = 0.6f) else colors.textPrimary
                                                )
                                            }
                                        }
                                    }

                                    // "+ Custom Date" chip
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCustomAssigned) colors.amber else colors.cardElevated,
                                        border = BorderStroke(1.dp, if (isCustomAssigned) colors.amber else colors.amber.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .clickable {
                                                taskForCustomDate = task
                                                customDateInput = if (isCustomAssigned) currentDate else ""
                                                customDateError = null
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarMonth,
                                                contentDescription = "Custom Date",
                                                tint = if (isCustomAssigned) Color.Black else colors.amber,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (isCustomAssigned) currentDate.removePrefix("2026-").removePrefix("2027-") else "+ Custom",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCustomAssigned) Color.Black else colors.amber,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Collective Validation before confirming
                    var hasError = false
                    val dateGroups = assignedDates.entries.groupBy { it.value }
                    for ((dateStr, entries) in dateGroups) {
                        if (dateStr.isBlank()) {
                            validationErrorMessage = "Please assign a valid date for all backlogs."
                            hasError = true
                            break
                        }
                        val existingCount = (candidateSlots.find { it.date == dateStr }?.currentCount ?: 0)
                        val totalCount = existingCount + entries.size
                        if (totalCount > 2) {
                            validationErrorMessage = "$dateStr would have $totalCount lectures. Maximum 2 allowed (Rule 1)."
                            hasError = true
                            break
                        }
                        val existingSubjs = candidateSlots.find { it.date == dateStr }?.existingSubjects?.map { it.lowercase().trim() }?.toMutableSet() ?: mutableSetOf()
                        for (entry in entries) {
                            val subj = sortedBacklogs.find { it.id == entry.key }?.subject?.lowercase()?.trim().orEmpty()
                            if (existingSubjs.contains(subj)) {
                                validationErrorMessage = "Multiple lectures for '$subj' on $dateStr (Rule 2)."
                                hasError = true
                                break
                            }
                            existingSubjs.add(subj)
                        }
                        if (hasError) break
                    }

                    if (!hasError) {
                        validationErrorMessage = null
                        onConfirmAll(assignedDates.toMap())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm All & Unlock", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = colors.textSecondary)
            }
        },
        containerColor = colors.card,
        shape = RoundedCornerShape(16.dp)
    )

    // Modal: Custom Date Picker Dialog
    if (taskForCustomDate != null) {
        val currentTask = taskForCustomDate!!
        AlertDialog(
            onDismissRequest = { taskForCustomDate = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, tint = colors.amber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Custom Recovery Date", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }
            },
            text = {
                Column {
                    Text(text = "[${currentTask.subjectCode}] ${currentTask.title}", fontSize = 12.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customDateInput,
                        onValueChange = {
                            customDateInput = it
                            customDateError = null
                        },
                        label = { Text("Date (YYYY-MM-DD)") },
                        placeholder = { Text("e.g. 2026-11-04") },
                        singleLine = true,
                        isError = customDateError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customDateError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = customDateError!!, fontSize = 11.sp, color = colors.rose, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Rule 1: Max 2 catch-up lectures per day.\n• Rule 2: 2 subjects cannot be the same on the same day.",
                        fontSize = 10.sp,
                        color = colors.textMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dateStr = customDateInput.trim()
                        val targetDate = try { LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd")) } catch (e: Exception) { null }
                        if (targetDate == null) {
                            customDateError = "Please enter a valid date in YYYY-MM-DD format."
                            return@Button
                        }
                        if (targetDate.isBefore(today)) {
                            customDateError = "Cannot reschedule to a past date."
                            return@Button
                        }
                        // Validate Rule 1
                        val existingCount = (candidateSlots.find { it.date == dateStr }?.currentCount ?: 0) +
                            assignedDates.entries.count { it.key != currentTask.id && it.value == dateStr }
                        if (existingCount >= 2) {
                            customDateError = "Rule 1: $dateStr already has 2 lectures (maximum reached)."
                            return@Button
                        }
                        // Validate Rule 2
                        val subjLower = currentTask.subject.lowercase().trim()
                        val existingSubjs = (candidateSlots.find { it.date == dateStr }?.existingSubjects?.map { it.lowercase().trim() } ?: emptyList()) +
                            assignedDates.entries.filter { it.key != currentTask.id && it.value == dateStr }.mapNotNull {
                                sortedBacklogs.find { b -> b.id == it.key }?.subject?.lowercase()?.trim()
                            }
                        if (existingSubjs.contains(subjLower)) {
                            customDateError = "Rule 2: A lecture for '${currentTask.subject}' is already scheduled on $dateStr."
                            return@Button
                        }

                        assignedDates[currentTask.id] = dateStr
                        taskForCustomDate = null
                        customDateError = null
                        validationErrorMessage = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Set Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskForCustomDate = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
fun TaskStatusBadge(
    status: TaskStatus,
    isBooster: Boolean = false,
    isLecture: Boolean = true
) {
    val colors = GateTheme.colors
    val (label, bg, fg) = when (status) {
        TaskStatus.COMPLETED_ON_TIME -> Triple("DONE", colors.emeraldContainer, colors.emerald)
        TaskStatus.COMPLETED_LATE -> Triple("LATE", colors.skyContainer, colors.sky)
        TaskStatus.DUE_TODAY -> Triple("TODAY", colors.primaryContainer, colors.primary)
        TaskStatus.MISSED -> {
            if (isLecture && !isBooster) {
                Triple("BACKLOG", colors.roseContainer, colors.rose)
            } else {
                Triple("MISSED", colors.cardBorder.copy(alpha = 0.35f), colors.textMuted)
            }
        }
        TaskStatus.RECOVERY_SCHEDULED -> Triple("RECOVERY", colors.amberContainer, colors.amber)
        TaskStatus.UPCOMING -> Triple("UPCOMING", colors.cardBorder.copy(alpha = 0.5f), colors.textMuted)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun HealthStatusBadge(health: PreparationHealth) {
    val colors = GateTheme.colors
    val (label, bg, fg) = when (health) {
        PreparationHealth.ON_TRACK -> Triple("ON TRACK", colors.emeraldContainer, colors.emerald)
        PreparationHealth.AT_RISK -> Triple("AT RISK", colors.amberContainer, colors.amber)
        PreparationHealth.BEHIND -> Triple("BEHIND", colors.roseContainer, colors.rose)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                letterSpacing = 0.6.sp
            )
        }
    }
}

/**
 * $5,000 Luxury Glassmorphic Cyber-Card Container.
 * Features a dual-layer cyber-slate gradient background, luminous metallic/neon ambient border,
 * smooth rounded corners, and optional click interaction.
 */
@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    borderHighlight: Color = GateTheme.colors.sky,
    borderSecondary: Color = GateTheme.colors.cardBorder,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = GateTheme.colors
    val clickableModifier = if (onClick != null) {
        modifier.clip(shape).clickable { onClick() }
    } else {
        modifier.clip(shape)
    }

    Surface(
        modifier = clickableModifier,
        color = Color.Transparent,
        shape = shape,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    borderHighlight.copy(alpha = 0.45f),
                    colors.cardBorder,
                    borderSecondary.copy(alpha = 0.25f)
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
                .padding(contentPadding)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun CyberPillBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = GateTheme.colors
    Surface(
        modifier = modifier.clip(RoundedCornerShape(18.dp)),
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    accentColor.copy(alpha = 0.45f),
                    colors.cardBorder,
                    accentColor.copy(alpha = 0.2f)
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
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressRing(
    progressPercent: Int,
    completedCount: Int,
    totalCount: Int,
    size: Dp = 92.dp,
    strokeWidth: Dp = 8.5.dp,
    modifier: Modifier = Modifier
) {
    val colors = GateTheme.colors
    val sweepAngle = (progressPercent / 100f) * 360f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            // Track
            drawArc(
                color = colors.cardBorder.copy(alpha = 0.6f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            // Progress
            if (sweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            colors.sky,
                            colors.emerald,
                            colors.primary,
                            colors.sky
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$progressPercent%",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (progressPercent == 100) colors.emerald else colors.textPrimary
            )
            Text(
                text = "$completedCount/$totalCount Done",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textMuted
            )
        }
    }
}

@Composable
fun RescheduleBacklogDialog(
    task: TaskItemUi,
    candidateDates: List<String>,
    candidateSlots: List<CandidateDateSlot> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = GateTheme.colors
    val kolkataZone = remember { ZoneId.of("Asia/Kolkata") }
    val today = remember { LocalDate.now(kolkataZone) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    var selectedDate by remember { mutableStateOf(task.recoveryDate ?: candidateDates.firstOrNull().orEmpty()) }
    var customDateText by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }

    val activeDate = if (isCustomSelected && customDateText.isNotBlank()) customDateText.trim() else selectedDate

    fun getValidationError(dateStr: String): String? {
        if (dateStr.isBlank()) return "Please select a date."
        val targetDate = try { LocalDate.parse(dateStr, dateFormatter) } catch (e: Exception) { return "Invalid date format. Use YYYY-MM-DD." }
        if (targetDate.isBefore(today)) {
            return "Cannot reschedule to a past date ($dateStr)."
        }
        val slot = candidateSlots.find { it.date == dateStr }
        val count = slot?.currentCount ?: 0
        if (count >= 2 && task.recoveryDate != dateStr) {
            return "Rule 1 Violation: $dateStr already has 2 recovery lectures."
        }
        val isSameSubj = slot?.existingSubjects?.any { it.equals(task.subject, ignoreCase = true) } == true
        if (isSameSubj && task.recoveryDate != dateStr) {
            return "Rule 2 Violation: A lecture for '${task.subject}' is already scheduled on $dateStr."
        }
        return null
    }

    val currentError = getValidationError(activeDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = colors.amber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reschedule Backlog",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Task brief
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "[${task.subjectCode}] ${task.title}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Original Date: ${task.originalDate}",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                        if (!task.recoveryDate.isNullOrEmpty()) {
                            Text(
                                text = "Currently assigned: ${task.recoveryDate}",
                                fontSize = 11.sp,
                                color = colors.amber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Rule Notice
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.emeraldContainer.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.emerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Rules: Max 2 lectures/day • 2 subjects cannot be same on same day. Other schedules stay intact.",
                            fontSize = 11.sp,
                            color = colors.emerald
                        )
                    }
                }

                if (currentError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.roseContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, colors.rose),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = colors.rose, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = currentError, fontSize = 11.sp, color = colors.rose, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Select Sunday or Buffer Day:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Candidate dates chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val displayCandidates = candidateDates.take(5)
                    for (date in displayCandidates) {
                        val isSelected = (!isCustomSelected && selectedDate == date)
                        val slot = candidateSlots.find { it.date == date }
                        val isFull = (slot?.currentCount ?: 0) >= 2 && task.recoveryDate != date
                        val isSameSubj = slot?.existingSubjects?.any { it.equals(task.subject, ignoreCase = true) } == true && task.recoveryDate != date
                        val isBlocked = isFull || isSameSubj

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) colors.primary.copy(alpha = 0.15f) else if (isBlocked) colors.surface.copy(alpha = 0.5f) else colors.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) colors.primary else if (isBlocked) colors.cardBorder.copy(alpha = 0.4f) else colors.cardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBlocked) {
                                    isCustomSelected = false
                                    selectedDate = date
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = date,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.primary else if (isBlocked) colors.textMuted else colors.textPrimary
                                    )
                                    if (isFull) {
                                        Text("Full (2/2 slots taken)", fontSize = 10.sp, color = colors.rose)
                                    } else if (isSameSubj) {
                                        Text("${task.subject} already scheduled", fontSize = 10.sp, color = colors.amber)
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom date input
                OutlinedTextField(
                    value = customDateText,
                    onValueChange = {
                        customDateText = it
                        isCustomSelected = true
                    },
                    label = { Text("Or custom date (YYYY-MM-DD)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. 2026-11-04", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    isError = isCustomSelected && currentError != null
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentError == null && activeDate.isNotBlank()) {
                        onConfirm(activeDate)
                    }
                },
                enabled = currentError == null && activeDate.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm Date", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.card,
        shape = RoundedCornerShape(16.dp)
    )
}
