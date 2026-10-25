package com.gatecse.companion.ui.screens.tests

import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gatecse.companion.data.ScheduleRepository
import com.gatecse.companion.model.AssessmentResultEntity
import com.gatecse.companion.model.DppRecordEntity
import com.gatecse.companion.model.SubjectRegistry
import com.gatecse.companion.ui.components.CyberCard
import com.gatecse.companion.ui.components.CyberPillBadge
import com.gatecse.companion.ui.components.MetricStatCard
import com.gatecse.companion.ui.theme.GateTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TestsScreen(
    repository: ScheduleRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = GateTheme.colors
    val kolkataZone = remember { ZoneId.of("Asia/Kolkata") }
    var todayDate by remember { mutableStateOf(LocalDate.now(kolkataZone)) }
    val todayStr = remember(todayDate) { todayDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentNow = LocalDate.now(kolkataZone)
                if (currentNow != todayDate) {
                    todayDate = currentNow
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Tests & PYQ, 1: DPP

    val assessments by repository.getAllAssessmentsFlow().collectAsState(initial = emptyList())
    val dppList by repository.getAllDppFlow().collectAsState(initial = emptyList())

    var showAddTestDialog by remember { mutableStateOf(false) }
    var showAddDppDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ASSESSMENTS & PRACTICE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.sky,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (selectedTabIndex == 0) "Tests & PYQ Mocks" else "Daily Practice Problems",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (selectedTabIndex == 0)
                                "Log Sunday tests, subject scores, and mistake notebooks."
                            else
                                "Track daily questions solved, accuracy %, and problem logs.",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    Button(
                        onClick = {
                            if (selectedTabIndex == 0) showAddTestDialog = true else showAddDppDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Log New Entry",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedTabIndex == 0) "Log Test" else "Log DPP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tab Switcher
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = colors.sky,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = colors.sky,
                                height = 3.dp
                            )
                        },
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = {
                                Text(
                                    "Tests & PYQ Mocks (${assessments.size})",
                                    fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (selectedTabIndex == 0) colors.sky else colors.textSecondary
                                )
                            }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = {
                                Text(
                                    "Daily DPP (${dppList.size})",
                                    fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (selectedTabIndex == 1) colors.sky else colors.textSecondary
                                )
                            }
                        )
                    }
                }
            }

            if (selectedTabIndex == 0) {
                // ==================== TAB 0: TESTS & PYQ MOCKS ====================
                item {
                    val totalTests = assessments.size
                    val avgPercentage = if (totalTests > 0) (assessments.sumOf { it.percentage } / totalTests).toInt() else 0
                    val highestPercentage = if (totalTests > 0) assessments.maxOf { it.percentage }.toInt() else 0
                    val mistakesLogged = assessments.count { it.mistakesAnalysis.isNotBlank() }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            MetricStatCard(
                                title = "TESTS TAKEN",
                                value = "$totalTests",
                                subtitle = "Logged mocks",
                                accentColor = colors.sky,
                                modifier = Modifier.width(135.dp)
                            )
                        }
                        item {
                            MetricStatCard(
                                title = "AVERAGE SCORE",
                                value = "$avgPercentage%",
                                subtitle = "Across all tests",
                                accentColor = colors.emerald,
                                modifier = Modifier.width(135.dp)
                            )
                        }
                        item {
                            MetricStatCard(
                                title = "PEAK ACCURACY",
                                value = "$highestPercentage%",
                                subtitle = "Best performance",
                                accentColor = Color(0xFFA855F7),
                                modifier = Modifier.width(135.dp)
                            )
                        }
                        item {
                            MetricStatCard(
                                title = "MISTAKE ENTRIES",
                                value = "$mistakesLogged",
                                subtitle = "Notebook logs",
                                accentColor = colors.amber,
                                modifier = Modifier.width(135.dp)
                            )
                        }
                    }
                }

                // Graphical Test Score Analytics
                item {
                    TestScoreGraphicalChart(assessments = assessments)
                }

                // List of Tests
                if (assessments.isEmpty()) {
                    item {
                        CyberCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentPadding = PaddingValues(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CyberPillBadge(text = "ASSESSMENT ARCHIVE EMPTY", accentColor = colors.sky)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No Tests Logged Yet",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Click '+ Log Test' or complete a Sunday Weekly Test to record your marks.",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(assessments, key = { it.id }) { assessment ->
                        AssessmentCard(
                            assessment = assessment,
                            onDelete = {
                                scope.launch {
                                    repository.deleteAssessment(assessment.id)
                                    Toast.makeText(context, "Test record deleted.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            } else {
                // ==================== TAB 1: DAILY PRACTICE PROBLEMS (DPP) ====================
                item {
                    val totalDpps = dppList.size
                    val totalQuestionsAttempted = dppList.sumOf { it.totalQuestions }
                    val totalQuestionsCorrect = dppList.sumOf { it.questionsCorrect }
                    val overallAccuracy = if (totalQuestionsAttempted > 0) {
                        ((totalQuestionsCorrect.toDouble() / totalQuestionsAttempted) * 100).toInt()
                    } else 0

                    val solvedToday = dppList.any { it.date == todayStr }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            MetricStatCard(
                                title = "TOTAL DPPS",
                                value = "$totalDpps",
                                subtitle = "Practice sets",
                                accentColor = colors.sky,
                                modifier = Modifier.width(135.dp)
                            )
                        }
                        item {
                            MetricStatCard(
                                title = "SOLVED ACCURACY",
                                value = "$overallAccuracy%",
                                subtitle = "$totalQuestionsCorrect / $totalQuestionsAttempted qns",
                                accentColor = colors.emerald,
                                modifier = Modifier.width(145.dp)
                            )
                        }
                        item {
                            MetricStatCard(
                                title = "TODAY'S STATUS",
                                value = if (solvedToday) "DONE" else "PENDING",
                                subtitle = if (solvedToday) "DPP logged 🔥" else "Solve today's DPP",
                                accentColor = if (solvedToday) colors.emerald else colors.amber,
                                modifier = Modifier.width(140.dp)
                            )
                        }
                    }
                }

                // Graphical DPP Analytics
                item {
                    DppPerformanceGraphicalChart(dppList = dppList)
                }

                // List of DPP Records
                if (dppList.isEmpty()) {
                    item {
                        CyberCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentPadding = PaddingValues(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CyberPillBadge(text = "DPP ARCHIVE EMPTY", accentColor = colors.emerald)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No Daily Practice Problems Logged",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Click '+ Log DPP' to track your daily question-solving accuracy & mistakes.",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(dppList, key = { it.id }) { dpp ->
                        DppRecordCard(
                            dpp = dpp,
                            onDelete = {
                                scope.launch {
                                    repository.deleteDpp(dpp.id)
                                    Toast.makeText(context, "DPP record deleted.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Add Test Dialog
        if (showAddTestDialog) {
            AddTestDialog(
                onDismiss = { showAddTestDialog = false },
                onSave = { entity ->
                    scope.launch {
                        repository.recordAssessment(entity)
                        showAddTestDialog = false
                        Toast.makeText(context, "Test scorecard recorded!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Add DPP Dialog
        if (showAddDppDialog) {
            AddDppDialog(
                todayDateStr = todayStr,
                onDismiss = { showAddDppDialog = false },
                onSave = { dppEntity ->
                    scope.launch {
                        repository.recordDpp(dppEntity)
                        showAddDppDialog = false
                        Toast.makeText(context, "DPP practice recorded!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

// ==================== GRAPHICAL ANALYTICS COMPONENTS ====================

@Composable
private fun TestScoreGraphicalChart(assessments: List<AssessmentResultEntity>) {
    val colors = GateTheme.colors
    val density = LocalDensity.current.density
    val chronList = remember(assessments) { assessments.sortedBy { it.testDate } }

    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        borderHighlight = colors.sky,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoGraph,
                    contentDescription = "Test Trajectory",
                    tint = colors.sky,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TEST SCORE ACCURACY TRAJECTORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.sky,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "${chronList.size} tests recorded",
                fontSize = 10.sp,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (chronList.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(colors.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (chronList.isEmpty()) "Take at least 2 tests to render score trajectory graph" else "1 test recorded (${chronList[0].percentage.toInt()}%). Take another to draw trend.",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val w = size.width
                val h = size.height
                val leftGutter = 35f
                val bottomGutter = 25f
                val plotW = w - leftGutter - 15f
                val plotH = h - bottomGutter - 15f

                val textPaint = Paint().apply {
                    color = android.graphics.Color.argb(180, 148, 163, 184)
                    textSize = 9f * density
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                // Y Grid lines (0%, 50%, 100%)
                val ySteps = listOf(0, 50, 100)
                for (step in ySteps) {
                    val yPos = 15f + plotH - (step / 100f) * plotH
                    drawContext.canvas.nativeCanvas.drawText("$step%", 6f, yPos + 6f, textPaint)
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(leftGutter, yPos),
                        end = Offset(w - 10f, yPos),
                        strokeWidth = 1f
                    )
                }

                // Compute points
                val stepX = plotW / (chronList.size - 1)
                val points = chronList.mapIndexed { idx, test ->
                    val px = leftGutter + idx * stepX
                    val py = 15f + plotH - (test.percentage.toFloat() / 100f).coerceIn(0f, 1f) * plotH
                    Offset(px, py)
                }

                // Paths
                val strokePath = Path()
                val fillPath = Path()

                strokePath.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(points.first().x, 15f + plotH)
                fillPath.lineTo(points.first().x, points.first().y)

                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx1 = p0.x + (p1.x - p0.x) / 2f
                    val cy1 = p0.y
                    val cx2 = p0.x + (p1.x - p0.x) / 2f
                    val cy2 = p1.y
                    strokePath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                    fillPath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                }

                fillPath.lineTo(points.last().x, 15f + plotH)
                fillPath.close()

                // Glow fill
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.35f),
                            Color(0xFF0EA5E9).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        startY = 15f,
                        endY = 15f + plotH
                    )
                )

                // Curve stroke
                drawPath(
                    path = strokePath,
                    color = Color(0xFF38BDF8),
                    style = Stroke(width = 2.5f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Points & Labels
                points.forEachIndexed { idx, pt ->
                    drawCircle(color = Color(0xFF38BDF8), radius = 4f * density, center = pt)
                    drawCircle(color = Color(0xFF0F172A), radius = 2f * density, center = pt)

                    val label = chronList[idx].testDate.substring(5) // MM-DD
                    val textW = textPaint.measureText(label)
                    val textX = (pt.x - textW / 2f).coerceIn(leftGutter, w - textW - 4f)
                    drawContext.canvas.nativeCanvas.drawText(label, textX, h - 6f, textPaint)
                }
            }
        }
    }
}

@Composable
private fun DppPerformanceGraphicalChart(dppList: List<DppRecordEntity>) {
    val colors = GateTheme.colors
    val density = LocalDensity.current.density
    val chronDpp = remember(dppList) { dppList.sortedBy { it.date }.takeLast(10) }

    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        borderHighlight = colors.emerald,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "DPP Accuracy Trend",
                    tint = colors.emerald,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DPP ACCURACY & QUESTIONS SOLVED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.emerald,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "Last ${chronDpp.size} sessions",
                fontSize = 10.sp,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (chronDpp.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(colors.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (chronDpp.isEmpty()) "Log at least 2 DPP sessions to render practice analytics" else "1 DPP logged (${chronDpp[0].accuracyPercentage.toInt()}%). Log more to view trend.",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val w = size.width
                val h = size.height
                val leftGutter = 35f
                val bottomGutter = 25f
                val plotW = w - leftGutter - 15f
                val plotH = h - bottomGutter - 15f

                val textPaint = Paint().apply {
                    color = android.graphics.Color.argb(180, 148, 163, 184)
                    textSize = 9f * density
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                // Y-Grid
                val ySteps = listOf(0, 50, 100)
                for (step in ySteps) {
                    val yPos = 15f + plotH - (step / 100f) * plotH
                    drawContext.canvas.nativeCanvas.drawText("$step%", 6f, yPos + 6f, textPaint)
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(leftGutter, yPos),
                        end = Offset(w - 10f, yPos),
                        strokeWidth = 1f
                    )
                }

                val stepX = plotW / (chronDpp.size - 1)
                val points = chronDpp.mapIndexed { idx, dpp ->
                    val px = leftGutter + idx * stepX
                    val py = 15f + plotH - (dpp.accuracyPercentage.toFloat() / 100f).coerceIn(0f, 1f) * plotH
                    Offset(px, py)
                }

                // Emerald Smooth Bezier curve
                val strokePath = Path()
                val fillPath = Path()

                strokePath.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(points.first().x, 15f + plotH)
                fillPath.lineTo(points.first().x, points.first().y)

                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx1 = p0.x + (p1.x - p0.x) / 2f
                    val cy1 = p0.y
                    val cx2 = p0.x + (p1.x - p0.x) / 2f
                    val cy2 = p1.y
                    strokePath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                    fillPath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                }

                fillPath.lineTo(points.last().x, 15f + plotH)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF10B981).copy(alpha = 0.35f),
                            Color(0xFF059669).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        startY = 15f,
                        endY = 15f + plotH
                    )
                )

                drawPath(
                    path = strokePath,
                    color = Color(0xFF10B981),
                    style = Stroke(width = 2.5f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                points.forEachIndexed { idx, pt ->
                    val dpp = chronDpp[idx]
                    drawCircle(color = Color(0xFF10B981), radius = 4f * density, center = pt)
                    drawCircle(color = Color(0xFF0F172A), radius = 2f * density, center = pt)

                    val label = dpp.subjectCode
                    val textW = textPaint.measureText(label)
                    val textX = (pt.x - textW / 2f).coerceIn(leftGutter, w - textW - 4f)
                    drawContext.canvas.nativeCanvas.drawText(label, textX, h - 6f, textPaint)
                }
            }
        }
    }
}

// ==================== CARDS & DIALOGS ====================

@Composable
private fun AssessmentCard(
    assessment: AssessmentResultEntity,
    onDelete: () -> Unit
) {
    val colors = GateTheme.colors
    val isGoodScore = assessment.percentage >= 70

    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        borderHighlight = if (isGoodScore) colors.emerald else colors.amber,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CyberPillBadge(
                    text = assessment.subjectName,
                    accentColor = colors.sky
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = assessment.testDate,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${String.format("%.1f", assessment.marksObtained)} / ${String.format("%.0f", assessment.maxMarks)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Test Record",
                        tint = colors.rose.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = assessment.testName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Text(
                text = "${String.format("%.1f", assessment.percentage)}%",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isGoodScore) colors.emerald else colors.amber
            )
        }

        if (assessment.mistakesAnalysis.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.rose.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, colors.rose.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Mistake Analysis: ${assessment.mistakesAnalysis}",
                    fontSize = 11.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun DppRecordCard(
    dpp: DppRecordEntity,
    onDelete: () -> Unit
) {
    val colors = GateTheme.colors
    val isGoodScore = dpp.accuracyPercentage >= 75

    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        borderHighlight = if (isGoodScore) colors.emerald else colors.amber,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CyberPillBadge(
                    text = dpp.subjectCode,
                    accentColor = colors.emerald
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dpp.subjectName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${dpp.questionsCorrect} / ${dpp.totalQuestions} correct",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete DPP Record",
                        tint = colors.rose.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Date: ${dpp.date} ${if (dpp.topic.isNotBlank()) "• ${dpp.topic}" else ""}",
                fontSize = 11.sp,
                color = colors.textSecondary
            )

            Text(
                text = "${String.format("%.1f", dpp.accuracyPercentage)}% Accuracy",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isGoodScore) colors.emerald else colors.amber
            )
        }

        if (dpp.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.surface.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Notes: ${dpp.notes}",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun AddTestDialog(
    onDismiss: () -> Unit,
    onSave: (AssessmentResultEntity) -> Unit
) {
    val colors = GateTheme.colors
    var subjectCode by remember { mutableStateOf("DL") }
    var testName by remember { mutableStateOf("") }
    var marksObtained by remember { mutableStateOf("") }
    var maxMarks by remember { mutableStateOf("50") }
    var topic by remember { mutableStateOf("") }
    var mistakesAnalysis by remember { mutableStateOf("") }

    val commonSubjects = remember { listOf("DL", "CN", "DBMS", "C", "OS", "COA", "GA", "EM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Test / Mock Score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = subjectCode,
                    onValueChange = { subjectCode = it.uppercase() },
                    label = { Text("Subject Code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

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
                    label = { Text("Test Title (e.g. Weekly Test 02)") },
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
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic / Chapter (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = mistakesAnalysis,
                    onValueChange = { mistakesAnalysis = it },
                    label = { Text("Mistake Analysis") },
                    placeholder = { Text("What went wrong? Silly error, concept gap...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
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
                    val resolvedSubj = SubjectRegistry.getSubjectInfoByCode(cleanCode)?.name ?: cleanCode

                    onSave(
                        AssessmentResultEntity(
                            testDate = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            testName = testName.ifBlank { "Weekly Mock ($cleanCode)" },
                            subjectName = resolvedSubj,
                            marksObtained = score,
                            maxMarks = max,
                            percentage = pct,
                            topic = topic,
                            mistakesAnalysis = mistakesAnalysis
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("Save Test")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun AddDppDialog(
    todayDateStr: String,
    onDismiss: () -> Unit,
    onSave: (DppRecordEntity) -> Unit
) {
    val colors = GateTheme.colors
    var subjectCode by remember { mutableStateOf("CN") }
    var totalQuestions by remember { mutableStateOf("10") }
    var questionsCorrect by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val commonSubjects = remember { listOf("CN", "DL", "DBMS", "C", "OS", "COA", "GA", "EM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Daily Practice Problem (DPP)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = subjectCode,
                    onValueChange = { subjectCode = it.uppercase() },
                    label = { Text("Subject Code (e.g. CN, DL, DBMS)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

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
                            color = if (isSelected) colors.emerald.copy(alpha = 0.25f) else colors.card,
                            border = BorderStroke(1.dp, if (isSelected) colors.emerald else colors.cardBorder),
                            modifier = Modifier.clickable { subjectCode = code }
                        ) {
                            Text(
                                text = code,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) colors.emerald else colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = questionsCorrect,
                        onValueChange = { questionsCorrect = it },
                        label = { Text("No. Correct") },
                        placeholder = { Text("e.g. 8") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = totalQuestions,
                        onValueChange = { totalQuestions = it },
                        label = { Text("Total Qns") },
                        placeholder = { Text("10") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic / Focus Area") },
                    placeholder = { Text("e.g. Subnetting & CIDR") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Mistakes (Optional)") },
                    placeholder = { Text("Formulas missed, concepts to review...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val correct = questionsCorrect.toIntOrNull() ?: 0
                    val total = totalQuestions.toIntOrNull() ?: 10
                    val acc = if (total > 0) (correct.toDouble() / total) * 100.0 else 0.0
                    val cleanCode = subjectCode.trim().uppercase().ifBlank { "DPP" }
                    val resolvedSubj = SubjectRegistry.getSubjectInfoByCode(cleanCode)?.name ?: cleanCode

                    onSave(
                        DppRecordEntity(
                            date = todayDateStr,
                            subjectCode = cleanCode,
                            subjectName = resolvedSubj,
                            topic = topic,
                            totalQuestions = total,
                            questionsCorrect = correct,
                            accuracyPercentage = acc,
                            notes = notes
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.emerald)
            ) {
                Text("Save DPP")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}
