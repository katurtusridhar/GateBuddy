package com.gatecse.companion.ui.screens.achievements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatecse.companion.data.ScheduleRepository
import com.gatecse.companion.model.AchievementEntity
import com.gatecse.companion.ui.components.CyberCard
import com.gatecse.companion.ui.components.CyberPillBadge
import com.gatecse.companion.ui.components.MetricStatCard
import com.gatecse.companion.ui.theme.GateTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AchievementsScreen(
    repository: ScheduleRepository,
    modifier: Modifier = Modifier
) {
    val colors = GateTheme.colors
    val achievements by repository.getAllAchievementsFlow().collectAsState(initial = emptyList())

    val unlockedCount = achievements.count { it.isUnlocked }
    val totalCount = achievements.size

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Screen Header
        item {
            Column {
                CyberPillBadge(text = "REPUTATION & MILESTONES", accentColor = colors.sky)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Milestones & Badges",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Badges are awarded strictly upon verified execution of tasks and assessments.",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        // Metrics Overview
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    title = "Unlocked Badges",
                    value = "$unlockedCount / $totalCount",
                    subtitle = "Mastery progress",
                    accentColor = colors.amber,
                    modifier = Modifier.weight(1f)
                )

                MetricStatCard(
                    title = "Completion",
                    value = if (totalCount > 0) "${((unlockedCount.toDouble() / totalCount.toDouble()) * 100).toInt()}%" else "0%",
                    subtitle = "Achievement tier",
                    accentColor = colors.sky,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Badge Items
        items(achievements, key = { it.code }) { achievement ->
            AchievementCard(achievement = achievement)
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementEntity) {
    val colors = GateTheme.colors
    val isUnlocked = achievement.isUnlocked
    val formattedDate = if (isUnlocked && achievement.unlockedAtTimestamp != null) {
        Instant.ofEpochMilli(achievement.unlockedAtTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } else {
        null
    }

    CyberCard(
        borderHighlight = if (isUnlocked) colors.amber else colors.cardBorder,
        borderSecondary = if (isUnlocked) colors.amber.copy(alpha = 0.35f) else colors.cardBorder.copy(alpha = 0.2f),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isUnlocked) colors.amberContainer.copy(alpha = 0.5f) else colors.cardBorder.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, if (isUnlocked) colors.amber.copy(alpha = 0.6f) else colors.cardBorder),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isUnlocked) Icons.Default.WorkspacePremium else Icons.Default.Lock,
                        contentDescription = achievement.title,
                        tint = if (isUnlocked) colors.amber else colors.textMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = achievement.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) colors.textPrimary else colors.textMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.cardBorder.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, colors.cardBorder.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = achievement.category,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = achievement.description,
                    fontSize = 12.sp,
                    color = if (isUnlocked) colors.textSecondary else colors.textMuted
                )

                if (formattedDate != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(colors.emerald)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Unlocked: $formattedDate",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.emerald
                        )
                    }
                }
            }
        }
    }
}
