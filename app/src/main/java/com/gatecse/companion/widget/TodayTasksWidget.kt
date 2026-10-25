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
import com.gatecse.companion.data.ScheduleRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TodayTasksWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = GateCompanionDatabase.getInstance(context)
        val repository = ScheduleRepository(database)
        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val displayDate = today.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        val tasks = repository.getTasksForDate(todayStr, todayStr)
        val isSunday = today.dayOfWeek.name == "SUNDAY"

        val slot1Task = tasks.firstOrNull { it.slot == 1 }
        val slot2Task = tasks.firstOrNull { it.slot == 2 }

        val s1Completed = slot1Task?.isCompleted == true
        val s2Completed = slot2Task?.isCompleted == true

        provideContent {
            GlanceTheme {
                TodayTasksWidgetContent(
                    dateStr = displayDate,
                    isSunday = isSunday,
                    slot1Title = slot1Task?.title ?: if (isSunday) "Weekly Active Recall Test" else "Rest / Revision Slot",
                    slot1Done = s1Completed,
                    slot2Title = slot2Task?.title ?: if (isSunday) "Sunday PYQ & Revision" else "Rest / Revision Slot",
                    slot2Done = s2Completed
                )
            }
        }
    }

    @Composable
    private fun TodayTasksWidgetContent(
        dateStr: String,
        isSunday: Boolean,
        slot1Title: String,
        slot1Done: Boolean,
        slot2Title: String,
        slot2Done: Boolean
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(Color(0xFF09, 0x0E, 0x1A))
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // 1. Header: IIT Bombay Emblem + Sridhar + Date
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
                            text = "TODAY'S MISSION",
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
                            text = dateStr,
                            style = TextStyle(
                                color = colorProvider(Color(0xFF38, 0xBD, 0xF8)),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // 2. Slot 1 Card
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(10.dp)
                        .background(Color(0xFF14, 0x1E, 0x33))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(6.dp)
                                .background(if (isSunday) Color(0xFF78, 0x35, 0x0F) else Color(0xFF0C, 0x4A, 0x6E))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isSunday) "TEST" else "S1",
                                style = TextStyle(
                                    color = colorProvider(if (isSunday) Color(0xFFFB, 0xBF, 0x24) else Color(0xFF38, 0xBD, 0xF8)),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = slot1Title,
                            style = TextStyle(
                                color = colorProvider(if (slot1Done) Color(0xFF94, 0xA3, 0xB8) else Color(0xFFF8, 0xFA, 0xFC)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(6.dp)
                                .background(if (slot1Done) Color(0xFF06, 0x4E, 0x3B) else Color(0xFF1E, 0x29, 0x3B))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (slot1Done) "DONE" else "TODO",
                                style = TextStyle(
                                    color = colorProvider(if (slot1Done) Color(0xFF34, 0xD3, 0x99) else Color(0xFF94, 0xA3, 0xB8)),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // 3. Slot 2 Card
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(10.dp)
                        .background(Color(0xFF14, 0x1E, 0x33))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(6.dp)
                                .background(if (isSunday) Color(0xFF31, 0x2E, 0x81) else Color(0xFF31, 0x2E, 0x81))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isSunday) "PYQ" else "S2",
                                style = TextStyle(
                                    color = colorProvider(Color(0xFF81, 0x8C, 0xF8)),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = slot2Title,
                            style = TextStyle(
                                color = colorProvider(if (slot2Done) Color(0xFF94, 0xA3, 0xB8) else Color(0xFFF8, 0xFA, 0xFC)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(6.dp)
                                .background(if (slot2Done) Color(0xFF06, 0x4E, 0x3B) else Color(0xFF1E, 0x29, 0x3B))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (slot2Done) "DONE" else "TODO",
                                style = TextStyle(
                                    color = colorProvider(if (slot2Done) Color(0xFF34, 0xD3, 0x99) else Color(0xFF94, 0xA3, 0xB8)),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // 4. Boosters Row
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(8.dp)
                        .background(Color(0xFF1E, 0x29, 0x3B))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ Daily Boosters: Rev (+5) • PYQ (+5)",
                            style = TextStyle(
                                color = colorProvider(Color(0xFF94, 0xA3, 0xB8)),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // 5. Footer: Target
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

class TodayTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()
}
