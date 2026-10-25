package com.gatecse.companion.ui.screens.subjects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatecse.companion.data.ScheduleRepository
import com.gatecse.companion.model.SubjectUi
import com.gatecse.companion.ui.components.CyberCard
import com.gatecse.companion.ui.components.CyberPillBadge
import com.gatecse.companion.ui.theme.GateTheme
import kotlinx.coroutines.launch

@Composable
fun SubjectsScreen(
    repository: ScheduleRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val colors = GateTheme.colors
    var subjects by remember { mutableStateOf<List<SubjectUi>>(emptyList()) }
    var expandedSubject by remember { mutableStateOf<String?>(null) }

    fun loadSubjects() {
        scope.launch {
            subjects = repository.getAllSubjectsProgress()
        }
    }

    LaunchedEffect(Unit) {
        loadSubjects()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Screen Header & Overall Curriculum Summary
        item {
            Column {
                Text(
                    text = "SYLLABUS PROGRESSION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.sky,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Subject Master List",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Track your complete GATE CSE subject syllabus with real-time progress.",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )

                if (subjects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val totalLecs = subjects.sumOf { it.totalLectures }
                    val completedLecs = subjects.sumOf { it.completedLectures }
                    val overallPct = if (totalLecs > 0) ((completedLecs.toDouble() / totalLecs) * 100).toInt() else 0
                    val fullyDoneCount = subjects.count { it.isFullyCompleted }

                    CyberCard(
                        borderHighlight = colors.sky,
                        borderSecondary = colors.emerald,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CyberPillBadge(text = "GATE CSE SYLLABUS", accentColor = colors.sky)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "$completedLecs / $totalLecs Lectures",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.emerald
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "$overallPct%",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Overall Curriculum Completed",
                                    fontSize = 11.sp,
                                    color = colors.textMuted
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$fullyDoneCount of ${subjects.size} Subjects Done",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primary
                                )
                                Text(
                                    text = "${subjects.size - fullyDoneCount} currently in progress",
                                    fontSize = 11.sp,
                                    color = colors.textMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (overallPct / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = colors.emerald,
                            trackColor = colors.cardBorder.copy(alpha = 0.5f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        // Subject Items
        items(subjects, key = { "${it.code}_${it.name}" }) { subject ->
            SubjectProgressCard(
                subject = subject,
                isExpanded = expandedSubject == subject.code,
                onToggleExpand = {
                    expandedSubject = if (expandedSubject == subject.code) null else subject.code
                }
            )
        }
    }
}

@Composable
private fun SubjectProgressCard(
    subject: SubjectUi,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val colors = GateTheme.colors
    val animatedProgress by animateFloatAsState(
        targetValue = (subject.completionPercentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "subProgress"
    )

    CyberCard(
        borderHighlight = if (subject.isFullyCompleted) colors.emerald else colors.sky,
        borderSecondary = if (subject.isFullyCompleted) colors.emerald.copy(alpha = 0.4f) else colors.cardBorder,
        contentPadding = PaddingValues(16.dp),
        onClick = onToggleExpand
    ) {
        // Subject title, code badge, and completion percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CyberPillBadge(
                text = subject.code,
                accentColor = if (subject.isFullyCompleted) colors.emerald else colors.primary
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${subject.completedLectures} of ${subject.totalLectures} lectures done • ${subject.type.name}",
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${subject.completionPercentage}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (subject.isFullyCompleted) colors.emerald else colors.sky
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress Bar
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(3.5.dp)),
            color = if (subject.isFullyCompleted) colors.emerald else colors.primary,
            trackColor = colors.cardBorder.copy(alpha = 0.5f),
            strokeCap = StrokeCap.Round
        )

        // Milestone Banner appears ONLY AFTER completing the subject (100%)
        if (subject.isFullyCompleted) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.emeraldContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Subject Completed",
                        tint = colors.emerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = subject.completionMilestone.takeIf { !it.isNullOrEmpty() } ?: "Subject 100% Completed! Milestone Achieved 🎉",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.emerald
                    )
                }
            }
        }

        // Expanded Details
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailChip(label = "Pre-Done", value = "${subject.priorCompletedLectures}", modifier = Modifier.weight(1f))
                    DetailChip(label = "Remaining", value = "${subject.remainingLectures}", modifier = Modifier.weight(1f))
                    DetailChip(label = "Total Lecs", value = "${subject.totalLectures}", modifier = Modifier.weight(1f))
                    DetailChip(label = "Missed", value = "${subject.missedLectures}", modifier = Modifier.weight(1f))
                }

                if (!subject.nextLecture.isNullOrEmpty() && !subject.isFullyCompleted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.sky.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, colors.sky.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.sky)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Next Lecture: ${subject.nextLecture}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.sky
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailChip(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = GateTheme.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.cardElevated.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, colors.cardBorder.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        }
    }
}
