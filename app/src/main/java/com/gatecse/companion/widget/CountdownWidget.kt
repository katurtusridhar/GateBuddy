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

class CountdownWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = GateCompanionDatabase.getInstance(context)
        val settings = database.scheduleDao().getUserSettings()
        val targetDateStr = settings?.targetExamDate ?: "2027-02-06"
        val examName = settings?.targetExamName ?: "GATE CSE 2027"

        val daysRemaining: Long = try {
            val targetDate = LocalDate.parse(targetDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
            ChronoUnit.DAYS.between(today, targetDate).coerceAtLeast(0)
        } catch (e: Exception) {
            108L
        }

        provideContent {
            GlanceTheme {
                CountdownWidgetContent(examName, daysRemaining)
            }
        }
    }

    @Composable
    private fun CountdownWidgetContent(examName: String, daysRemaining: Long) {
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
                // 1. Header: IIT Bombay Emblem + Sridhar + Target Tag
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
                            text = "IIT-B COUNTDOWN",
                            style = TextStyle(
                                color = colorProvider(Color(0xFF81, 0x8C, 0xF8)),
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
                            text = "AIR < 100",
                            style = TextStyle(
                                color = colorProvider(Color(0xFFF5, 0x9E, 0x0B)),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // 2. Hero Days Counter Card
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(12.dp)
                        .background(Color(0xFF14, 0x1E, 0x33))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$daysRemaining",
                            style = TextStyle(
                                color = colorProvider(Color(0xFF38, 0xBD, 0xF8)),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "DAYS UNTIL $examName",
                            style = TextStyle(
                                color = colorProvider(Color(0xFF94, 0xA3, 0xB8)),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // 3. Motivational Prompt
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(10.dp)
                        .background(Color(0xFF1E, 0x29, 0x3B))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "“Consistency beats intensity, Sridhar. Execute today!”",
                        style = TextStyle(
                            color = colorProvider(Color(0xFFE2, 0xE8, 0xF0)),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 2
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // 4. Footer
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
                        text = "GATE 2027 🏆",
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

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}
