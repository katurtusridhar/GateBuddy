package com.gatecse.companion.ui.screens.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatecse.companion.data.ScheduleRepository
import com.gatecse.companion.model.DayScorePoint
import com.gatecse.companion.model.PreparationAdherenceUi
import com.gatecse.companion.model.SubjectUi
import com.gatecse.companion.ui.components.CyberCard
import com.gatecse.companion.ui.components.CyberPillBadge
import com.gatecse.companion.ui.components.HealthStatusBadge
import com.gatecse.companion.ui.components.MetricStatCard
import com.gatecse.companion.ui.theme.GateTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AnalyticsScreen(
    repository: ScheduleRepository,
    onNavigateToToday: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = GateTheme.colors
    val kolkataZone = remember { ZoneId.of("Asia/Kolkata") }
    var todayDate by remember { mutableStateOf(LocalDate.now(kolkataZone)) }
    val todayDateStr = remember(todayDate) { todayDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
    var metrics by remember { mutableStateOf<PreparationAdherenceUi?>(null) }
    var subjects by remember { mutableStateOf<List<SubjectUi>>(emptyList()) }

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

    LaunchedEffect(todayDateStr) {
        metrics = repository.getPreparationAdherence(todayDateStr)
        subjects = repository.getAllSubjectsProgress()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        item {
            Column {
                Text(
                    text = "SRIDHAR'S ANALYTICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.sky,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Preparation Scorecard",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Candidate: Sridhar • Target: IIT Bombay CSE (AIR < 100)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary
                )
            }
        }

        if (metrics != null) {
            val m = metrics!!

            // 1. Preparation Health Overview Card
            item {
                CyberCard(
                    borderHighlight = colors.sky,
                    borderSecondary = colors.emerald,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CyberPillBadge(text = "PREPARATION HEALTH", accentColor = colors.sky)
                        Spacer(modifier = Modifier.weight(1f))
                        HealthStatusBadge(health = m.health)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = m.healthReason,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { (m.onTimeAdherencePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = colors.primary,
                        trackColor = colors.cardBorder.copy(alpha = 0.5f),
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "On-time completion adherence",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${m.onTimeAdherencePercent}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
            }

            // 2. Interactive Daily Points Canvas Chart (Requirement 8)
            item {
                DailyPointsChartCard(
                    scores = m.recentDaysScores,
                    targetPoints = 30,
                    todayDateStr = todayDateStr,
                    onNavigateToToday = onNavigateToToday,
                    onNavigateToCalendar = onNavigateToCalendar
                )
            }

            // 3. Point System Rules & Breakdown Card (Requirement 5)
            item {
                CyberCard(
                    borderHighlight = colors.amber,
                    borderSecondary = colors.cardBorder,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CyberPillBadge(text = "POINT SYSTEM ARCHITECTURE", accentColor = colors.amber)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PointRuleBadge(label = "Lecture", value = "+10 pts", color = colors.emerald)
                        PointRuleBadge(label = "Sunday Test", value = "max +20", color = colors.amber)
                        PointRuleBadge(label = "Revision/PYQ", value = "+5 pts", color = colors.primary)
                        PointRuleBadge(label = "Missed", value = "-2 / -4", color = colors.rose)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Rolling 7-day average points are tracked (Target: 30 pts/day). Sunday tests award up to +20 pts based on score percentage with a -4 pts penalty if missed. Regular missed lectures deduct -2 pts.",
                        fontSize = 11.sp,
                        color = colors.textMuted,
                        lineHeight = 16.sp
                    )
                }
            }

            // 4. Metric Grid: Average Daily Points, Streak, Backlog, Total Points
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "7-Day Avg Score",
                        value = "${String.format("%.1f", m.averageDailyPoints)}",
                        subtitle = "Rolling daily average",
                        accentColor = colors.sky,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Total Points",
                        value = "${m.totalPointsAllTime}",
                        subtitle = "Cumulative preparation",
                        accentColor = colors.amber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "7-Day Adherence",
                        value = "${m.sevenDayAdherencePercent}%",
                        subtitle = "Recent week",
                        accentColor = colors.emerald,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Current Backlog",
                        value = "${m.currentBacklog}",
                        subtitle = if (m.currentBacklog == 0) "Zero debt" else "Awaiting recovery",
                        accentColor = if (m.currentBacklog == 0) colors.emerald else colors.rose,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 5. Intelligent Completion Forecast Card
            item {
                CyberCard(
                    borderHighlight = colors.sky,
                    borderSecondary = colors.primary,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CyberPillBadge(text = "SYLLABUS FORECAST", accentColor = colors.sky)
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.cardBorder.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, colors.cardBorder.copy(alpha = 0.8f))
                        ) {
                            Text(
                                text = m.forecastConfidence,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Forecast Completion",
                                fontSize = 11.sp,
                                color = colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = m.forecastCompletionDate ?: "Pending",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.sky
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Planned Calendar End",
                                fontSize = 11.sp,
                                color = colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = m.plannedEndDate,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = m.forecastExplanation,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            // 6. Action Recommendations
            if (m.recommendedActions.isNotEmpty()) {
                item {
                    CyberCard(
                        borderHighlight = colors.amber,
                        borderSecondary = colors.rose,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CyberPillBadge(text = "RECOMMENDED RECOVERY ACTIONS", accentColor = colors.amber)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        m.recommendedActions.forEach { action ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.sky)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = action,
                                    fontSize = 12.sp,
                                    color = colors.textPrimary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyPointsChartCard(
    scores: List<DayScorePoint>,
    targetPoints: Int = 30,
    todayDateStr: String = "",
    onNavigateToToday: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {}
) {
    val colors = GateTheme.colors
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    CyberCard(
        borderHighlight = colors.sky,
        borderSecondary = colors.cardBorder,
        contentPadding = PaddingValues(16.dp)
    ) {
        // Header matching website AreaChart exactly
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Points History (Last 14 Days)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Earned points across lectures, boosters & custom tasks",
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
            }
            Text(
                text = "Target: ${targetPoints} pts/day",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.sky
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Selected Tooltip pill if tapped
        if (selectedIndex != null && selectedIndex in scores.indices) {
            val sel = scores[selectedIndex!!]
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "${sel.date}: ${sel.points} pts",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Curved Cubic Bezier Area Chart Canvas (Exact replica of website screenshot)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .pointerInput(scores) {
                    detectTapGestures { offset ->
                        val leftGutter = 60f
                        val plotW = size.width - leftGutter - 20f
                        if (offset.x >= leftGutter && scores.size > 1) {
                            val step = plotW / (scores.size - 1)
                            val idx = ((offset.x - leftGutter + step / 2f) / step).toInt().coerceIn(0, scores.lastIndex)
                            selectedIndex = idx
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val leftGutter = 60f
                val bottomGutter = 45f
                val plotW = w - leftGutter - 20f
                val plotH = h - bottomGutter - 20f

                val maxPts = (scores.maxOfOrNull { it.points } ?: 0).coerceAtLeast(8)
                val maxVal = if (maxPts % 2 != 0) (maxPts + 1).toFloat() else maxPts.toFloat()

                // Draw Y-Axis ticks and horizontal grid lines (0, 2, 4, 6, 8)
                val ySteps = 4
                val textPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#64748B")
                    textSize = 24f
                    typeface = Typeface.DEFAULT
                    isAntiAlias = true
                }

                for (s in 0..ySteps) {
                    val tickVal = (maxVal / ySteps * s).toInt()
                    val yPos = 15f + plotH - (s.toFloat() / ySteps) * plotH

                    drawContext.canvas.nativeCanvas.drawText(
                        "$tickVal",
                        15f,
                        yPos + 8f,
                        textPaint
                    )

                    drawLine(
                        color = Color(0xFF1E293B).copy(alpha = 0.5f),
                        start = Offset(leftGutter, yPos),
                        end = Offset(w - 10f, yPos),
                        strokeWidth = 1f
                    )
                }

                // Y-Axis line
                drawLine(
                    color = Color(0xFF334155),
                    start = Offset(leftGutter, 15f),
                    end = Offset(leftGutter, 15f + plotH),
                    strokeWidth = 1.5f
                )

                if (scores.size >= 2) {
                    val stepX = plotW / (scores.size - 1)
                    val points = scores.mapIndexed { idx, item ->
                        val px = leftGutter + idx * stepX
                        val py = 15f + plotH - (item.points.toFloat() / maxVal).coerceIn(0f, 1f) * plotH
                        Offset(px, py)
                    }

                    // Smooth Monotone Cubic Bezier Curve
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

                    // Glowing Area Gradient underneath curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.40f),
                                Color(0xFF0EA5E9).copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            startY = 15f,
                            endY = 15f + plotH
                        )
                    )

                    // Main Curve Stroke (type="monotone" #38BDF8 strokeWidth=2.5)
                    drawPath(
                        path = strokePath,
                        color = Color(0xFF38BDF8),
                        style = Stroke(
                            width = 2.5f * density,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw circles at points
                    points.forEachIndexed { idx, pt ->
                        val isSel = selectedIndex == idx
                        val isPeak = scores[idx].points == maxPts && maxPts > 0
                        if (isSel || isPeak) {
                            drawCircle(
                                color = Color(0xFF38BDF8),
                                radius = if (isSel) 6f * density else 4f * density,
                                center = pt
                            )
                            drawCircle(
                                color = Color(0xFF0F172A),
                                radius = if (isSel) 3f * density else 2f * density,
                                center = pt
                            )
                        }
                    }

                    // Draw X-axis date labels at bottom
                    val labelIndices = if (scores.size > 6) {
                        listOf(0, scores.size / 3, (scores.size * 2) / 3, scores.lastIndex)
                    } else {
                        scores.indices.toList()
                    }

                    for (idx in labelIndices) {
                        val pt = points[idx]
                        val dateText = scores[idx].date
                        val textW = textPaint.measureText(dateText)
                        val textX = (pt.x - textW / 2f).coerceIn(leftGutter, w - textW - 5f)
                        drawContext.canvas.nativeCanvas.drawText(
                            dateText,
                            textX,
                            h - 8f,
                            textPaint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PointRuleBadge(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GateTheme.colors.textMuted)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
