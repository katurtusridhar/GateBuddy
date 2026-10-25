package com.gatecse.companion.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.gatecse.companion.MainActivity
import com.gatecse.companion.R
import com.gatecse.companion.analytics.AnalyticsEngine
import com.gatecse.companion.data.GateCompanionDatabase
import com.gatecse.companion.data.ScheduleRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PrepProgressWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = GateCompanionDatabase.getInstance(context)
        val repository = ScheduleRepository(database)
        val todayStr = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val allTasks = database.scheduleDao().getAllScheduleTasks()
        val allProgress = database.scheduleDao().getAllProgress()
        val activeRecoveries = database.scheduleDao().getActiveRecoveries()

        val metrics = AnalyticsEngine.calculateAdherenceAndForecast(
            currentDateStr = todayStr,
            allTasks = allTasks,
            allProgress = allProgress,
            activeRecoveries = activeRecoveries
        )

        // Today's scheduled focus from single source of truth
        val todayTasks = repository.getTasksForDate(todayStr, todayStr)
        val s1 = todayTasks.firstOrNull { it.slot == 1 }
        val s2 = todayTasks.firstOrNull { it.slot == 2 }
        val todayFocus = when {
            metrics.currentBacklog > 0 -> "⚠️ ${metrics.currentBacklog} backlogs need Sunday catch-up"
            s1 != null && s2 != null -> "${s1.title.take(17)} • ${s2.title.take(17)}"
            s1 != null -> s1.title
            else -> "Daily Revision & Problem Solving"
        }

        provideContent {
            GlanceTheme {
                PrepProgressWidgetContent(
                    adherencePercent = metrics.onTimeAdherencePercent,
                    streakDays = metrics.currentStreakDays,
                    backlogCount = metrics.currentBacklog,
                    avgPoints = metrics.averageDailyPoints,
                    statusText = metrics.health.name.replace("_", " "),
                    todayFocus = todayFocus
                )
            }
        }
    }

    @Composable
    private fun PrepProgressWidgetContent(
        adherencePercent: Int,
        streakDays: Int,
        backlogCount: Int,
        avgPoints: Double,
        statusText: String,
        todayFocus: String
    ) {
        val statusBg = when (statusText) {
            "ON TRACK" -> Color(0xFF06, 0x4E, 0x3B)
            "AT RISK" -> Color(0xFF78, 0x35, 0x0F)
            else -> Color(0xFF4C, 0x05, 0x19)
        }
        val statusFg = when (statusText) {
            "ON TRACK" -> Color(0xFF34, 0xD3, 0x99)
            "AT RISK" -> Color(0xFFFB, 0xBF, 0x24)
            else -> Color(0xFFFB, 0x71, 0x85)
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(Color(0xFF09, 0x0E, 0x1A))
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                // 1. Header: IIT Bombay Logo + Sridhar + Status Badge
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.app_logo),
                        contentDescription = "IIT Bombay Emblem",
                        modifier = GlanceModifier.size(24.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "SRIDHAR",
                            style = TextStyle(
                                color = colorProvider(Color(0xFFF8, 0xFA, 0xFC)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = "PREP HEALTH",
                            style = TextStyle(
                                color = colorProvider(Color(0xFF38, 0xBD, 0xF8)),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(6.dp)
                            .background(statusBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = TextStyle(
                                color = colorProvider(statusFg),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // 2. Metric Badges: 3 Rounded Tiles (Adherence, Streak, Backlog)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Adherence Tile
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .cornerRadius(10.dp)
                            .background(Color(0xFF14, 0x1E, 0x33))
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$adherencePercent%",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF38, 0xBD, 0xF8)),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "ADHERENCE",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF94, 0xA3, 0xB8)),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.width(5.dp))

                    // Streak Tile
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .cornerRadius(10.dp)
                            .background(Color(0xFF14, 0x1E, 0x33))
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${streakDays}d",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFFF5, 0x9E, 0x0B)),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "STREAK",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF94, 0xA3, 0xB8)),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.width(5.dp))

                    // Backlog Tile
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .cornerRadius(10.dp)
                            .background(Color(0xFF14, 0x1E, 0x33))
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$backlogCount",
                                style = TextStyle(
                                    color = colorProvider(if (backlogCount == 0) Color(0xFF10, 0xB9, 0x81) else Color(0xFFFB, 0x71, 0x85)),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "BACKLOG",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF94, 0xA3, 0xB8)),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // 3. Middle Focus Box: Today's Target & Pace
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(10.dp)
                        .background(Color(0xFF14, 0x1E, 0x33))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TODAY'S FOCUS",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF81, 0x8C, 0xF8)),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = GlanceModifier.defaultWeight())
                            Text(
                                text = "${String.format("%.0f", avgPoints)} pts/day",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF94, 0xA3, 0xB8)),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Text(
                            text = todayFocus,
                            style = TextStyle(
                                color = colorProvider(if (backlogCount > 0) Color(0xFFFD, 0xA4, 0xAF) else Color(0xFFE2, 0xE8, 0xF0)),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // 4. Footer: Target IIT Bombay & AIR < 100
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TARGET: IIT BOMBAY CSE",
                        style = TextStyle(
                            color = colorProvider(Color(0xFF64, 0x74, 0x8B)),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "AIR < 100 🏆",
                        style = TextStyle(
                            color = colorProvider(Color(0xFFF5, 0x9E, 0x0B)),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

class PrepProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrepProgressWidget()
}
