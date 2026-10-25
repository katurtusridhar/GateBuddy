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
import com.gatecse.companion.data.GateCompanionDatabase
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class UpcomingTestWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = GateCompanionDatabase.getInstance(context)
        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val allTasks = database.scheduleDao().getAllScheduleTasks()
        val upcomingSundayTest = allTasks.firstOrNull {
            it.isSundayActivity && it.slot == 1 && !LocalDate.parse(it.date, DateTimeFormatter.ofPattern("yyyy-MM-dd")).isBefore(today)
        }

        val testDateStr = upcomingSundayTest?.date ?: today.plusDays(4).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val testDate = try {
            LocalDate.parse(testDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (e: Exception) {
            today.plusDays(4)
        }
        val daysRemaining = ChronoUnit.DAYS.between(today, testDate).coerceAtLeast(0)
        val formattedDate = testDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        val testTitle = upcomingSundayTest?.title ?: "Sunday Weekly Test & Revision"

        provideContent {
            GlanceTheme {
                UpcomingTestWidgetContent(
                    formattedDate = formattedDate,
                    testTitle = testTitle,
                    daysRemaining = daysRemaining
                )
            }
        }
    }

    @Composable
    private fun UpcomingTestWidgetContent(
        formattedDate: String,
        testTitle: String,
        daysRemaining: Long
    ) {
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
                // 1. Header: IIT Bombay Emblem + Sridhar + Test Date
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
                            text = "TEST TARGET",
                            style = TextStyle(
                                color = colorProvider(Color(0xFFF5, 0x9E, 0x0B)),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(6.dp)
                            .background(Color(0xFF1E, 0x29, 0x3B))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formattedDate,
                            style = TextStyle(
                                color = colorProvider(Color(0xFF38, 0xBD, 0xF8)),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // 2. Hero Countdown Tile
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(12.dp)
                        .background(Color(0xFF14, 0x1E, 0x33))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "NEXT WEEKLY TEST",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF81, 0x8C, 0xF8)),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = testTitle,
                                style = TextStyle(
                                    color = colorProvider(Color(0xFFF8, 0xFA, 0xFC)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = "Syllabus: Digital Logic • Networks",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF94, 0xA3, 0xB8)),
                                    fontSize = 8.sp
                                ),
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // Days Pill
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(10.dp)
                                .background(Color(0xFF78, 0x35, 0x0F))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$daysRemaining",
                                    style = TextStyle(
                                        color = colorProvider(Color(0xFFFB, 0xBF, 0x24)),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "DAYS",
                                    style = TextStyle(
                                        color = colorProvider(Color(0xFFFE, 0xF3, 0xC7)),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // 3. Action Tile
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(10.dp)
                        .background(Color(0xFF14, 0x1E, 0x33))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📝",
                            style = TextStyle(fontSize = 11.sp)
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "Log Test Marks & Mistake Notes",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF38, 0xBD, 0xF8)),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Tap to open test series tracker",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF64, 0x74, 0x8B)),
                                    fontSize = 7.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // 4. Footer: Target
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

class UpcomingTestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingTestWidget()
}
