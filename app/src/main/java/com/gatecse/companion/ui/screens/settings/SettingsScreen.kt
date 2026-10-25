package com.gatecse.companion.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatecse.companion.data.ScheduleRepository
import com.gatecse.companion.model.ThemeMode
import com.gatecse.companion.model.UserSettingsEntity
import com.gatecse.companion.sync.SyncApiClient
import com.gatecse.companion.ui.components.CyberCard
import com.gatecse.companion.ui.components.CyberPillBadge
import com.gatecse.companion.ui.theme.GateTheme
import com.gatecse.companion.widget.WidgetUpdateHelper
import com.gatecse.companion.worker.NotificationHelper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: ScheduleRepository,
    currentThemeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onNavigateToUpdates: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = GateTheme.colors
    val userSettings by repository.getUserSettingsFlow().collectAsState(initial = null)

    var showExamDatePicker by remember { mutableStateOf(false) }
    var showImportReportDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonText by remember { mutableStateOf("") }
    var showSyncAccountDialog by remember { mutableStateOf(false) }
    var syncUsernameInput by remember { mutableStateOf(SyncApiClient.getSavedUsername(context)) }
    var isSyncConfigured by remember { mutableStateOf(SyncApiClient.hasCredentials(context)) }

    val settings = userSettings ?: UserSettingsEntity()

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
                CyberPillBadge(text = "APPLICATION SETTINGS", accentColor = colors.sky)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Settings & Data",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Manage your exam parameters, recovery capacity, appearance, and data backups.",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        // 1. Target Exam Countdown Configuration
        item {
            CyberCard(
                borderHighlight = colors.sky,
                borderSecondary = colors.primary,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CyberPillBadge(text = "TARGET EXAMINATION", accentColor = colors.sky)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Configured Exam Date: ${settings.targetExamDate ?: "Not Set"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showExamDatePicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (settings.targetExamDate != null) "Change Target Date" else "Configure Exam Date",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Recovery Capacity Setting
        item {
            CyberCard(
                borderHighlight = colors.emerald,
                borderSecondary = colors.cardBorder,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CyberPillBadge(text = "RECOVERY CAPACITY", accentColor = colors.emerald)
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.emerald.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${settings.dailyRecoveryLimit} tasks/day",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.emerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Daily Recovery Workload Limit",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Maximum additional recovery lectures assigned to buffer days or Sundays.",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))
                var sliderVal by remember(settings.dailyRecoveryLimit) {
                    mutableFloatStateOf(settings.dailyRecoveryLimit.toFloat())
                }
                Slider(
                    value = sliderVal,
                    onValueChange = { sliderVal = it },
                    onValueChangeFinished = {
                        scope.launch {
                            repository.saveUserSettings(settings.copy(dailyRecoveryLimit = sliderVal.toInt()))
                        }
                    },
                    valueRange = 1f..4f,
                    steps = 2
                )
            }
        }

        // 3. Theme Mode Selection
        item {
            CyberCard(
                borderHighlight = colors.sky,
                borderSecondary = colors.cardBorder,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CyberPillBadge(text = "APPEARANCE THEME", accentColor = colors.sky)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Select Interface Mode",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionButton(
                        title = "Dark",
                        isSelected = currentThemeMode == ThemeMode.DARK,
                        onClick = {
                            onThemeModeChanged(ThemeMode.DARK)
                            scope.launch { repository.saveUserSettings(settings.copy(themeMode = "DARK")) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionButton(
                        title = "Light",
                        isSelected = currentThemeMode == ThemeMode.LIGHT,
                        onClick = {
                            onThemeModeChanged(ThemeMode.LIGHT)
                            scope.launch { repository.saveUserSettings(settings.copy(themeMode = "LIGHT")) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionButton(
                        title = "System",
                        isSelected = currentThemeMode == ThemeMode.SYSTEM,
                        onClick = {
                            onThemeModeChanged(ThemeMode.SYSTEM)
                            scope.launch { repository.saveUserSettings(settings.copy(themeMode = "SYSTEM")) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Study Slot Reminders
        item {
            CyberCard(
                borderHighlight = colors.primary,
                borderSecondary = colors.sky,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CyberPillBadge(text = "STUDY CADENCE ALERTS", accentColor = colors.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Daily Study Reminders",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "5 AM daily mission briefing & 4-hourly motivation",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = { isEnabled ->
                            scope.launch {
                                repository.saveUserSettings(settings.copy(reminderEnabled = isEnabled))
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.primary)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        scope.launch {
                            NotificationHelper.sendCadenceNotification(context, force = true)
                            Toast.makeText(context, "Motivational reminder posted! Check notifications drawer.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Send Motivational Reminder Now",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 5. Schedule Import Validation Report Viewer
        item {
            CyberCard(
                borderHighlight = colors.emerald,
                borderSecondary = colors.cardBorder,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CyberPillBadge(text = "BASELINE VALIDATION", accentColor = colors.emerald)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Schedule Import Verification Certificate",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Source: GATE_CSE_Daily_Study_Calendar_2026_2027.pdf (Pages 1–16)",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showImportReportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.cardElevated),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("View Validation Certificate", fontSize = 12.sp, color = colors.textPrimary)
                }
            }
        }

        // 6. Data Backup & Restore
        item {
            CyberCard(
                borderHighlight = colors.amber,
                borderSecondary = colors.cardBorder,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CyberPillBadge(text = "DATA BACKUP & RECOVERY", accentColor = colors.amber)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Data Backup & Portability",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Export your task completion timestamps, notes, and assessment history to JSON or restore from a backup.",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val jsonExport = repository.exportDataToJson()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("GATE_Companion_Backup", jsonExport))
                                Toast.makeText(context, "Backup copied to clipboard!", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Export", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showRestoreDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.cardElevated),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Upload, contentDescription = "Restore", modifier = Modifier.size(16.dp), tint = colors.textPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore", fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 7. Manual Home-screen Widget Refresh
        item {
            CyberCard(
                borderHighlight = colors.sky,
                contentPadding = PaddingValues(14.dp),
                onClick = {
                    scope.launch {
                        WidgetUpdateHelper.updateAllWidgets(context)
                        Toast.makeText(context, "Widgets refreshed with latest data", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.Widgets, contentDescription = "Refresh", tint = colors.sky, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Home-Screen Widgets", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }
            }
        }

        // 8. Companion Website Sync Account
        item {
            CyberCard(
                borderSecondary = colors.cardBorder,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CyberPillBadge(text = "WEBSITE SYNCHRONIZATION", accentColor = colors.sky)
                    val statusText = if (isSyncConfigured) "CONNECTED" else "NOT CONFIGURED"
                    val statusColor = if (isSyncConfigured) colors.emerald else colors.amber
                    CyberPillBadge(text = statusText, accentColor = statusColor)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Companion Website Account",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isSyncConfigured) "Account: ${if (syncUsernameInput.isNotBlank()) syncUsernameInput else "Configured"} — Android progress uploads securely to gate-buddy.vercel.app." else "Configure your website username and password to authenticate progress sync.",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showSyncAccountDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.cardElevated),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = "Account", modifier = Modifier.size(16.dp), tint = colors.sky)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSyncConfigured) "Credentials" else "Configure", fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    }

                    if (isSyncConfigured) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val res = repository.syncWithCloud()
                                    if (res.success) {
                                        Toast.makeText(context, "Cloud sync completed successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Sync attempt: ${res.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = "Sync Now", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 9. Software Updates & Version History
        item {
            CyberCard(
                borderHighlight = colors.primary,
                contentPadding = PaddingValues(14.dp),
                onClick = onNavigateToUpdates
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = "Updates", tint = colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check for Updates & Version History", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }
            }
        }
    }

    // Dialog: Exam Date Picker
    if (showExamDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = Instant.now().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showExamDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val selectedDate = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            val formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            scope.launch {
                                repository.saveUserSettings(settings.copy(targetExamDate = formattedDate))
                                WidgetUpdateHelper.updateAllWidgets(context)
                            }
                        }
                        showExamDatePicker = false
                    }
                ) {
                    Text("Save Date", color = colors.sky)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExamDatePicker = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Dialog: Schedule Import Report
    if (showImportReportDialog) {
        AlertDialog(
            onDismissRequest = { showImportReportDialog = false },
            title = { Text("Schedule Import Validation Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✓ Baseline Status: 100% VERIFIED", fontWeight = FontWeight.Bold, color = colors.emerald)
                    Text("• Source Document: GATE_CSE_Daily_Study_Calendar_2026_2027.pdf")
                    Text("• Total Calendar Days: 345 continuous days")
                    Text("• Range: Oct 19, 2026 – Sep 28, 2027")
                    Text("• Total Scheduled Tasks: 690 tasks (2 slots/day)")
                    Text("• Total Sundays: 49 Sundays (reserved for tests & PYQs)")
                    Text("• Total Buffer Days: 12 designated catch-up days")
                    Text("• Core Subjects Covered: 21 subjects")
                    Text("• Sequence Gap / Numbering Errors: 0")
                    Text("• Baseline Preservation: Immutable original dates retained")
                }
            },
            confirmButton = {
                Button(
                    onClick = { showImportReportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Dialog: Restore Data
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Data from Backup") },
            text = {
                Column {
                    Text("Paste your exported JSON backup text below:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreJsonText,
                        onValueChange = { restoreJsonText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        placeholder = { Text("{\n  \"version\": 1,\n  \"progress\": [...]\n}") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val success = repository.restoreDataFromJson(restoreJsonText)
                            if (success) {
                                Toast.makeText(context, "Data restored successfully!", Toast.LENGTH_LONG).show()
                                WidgetUpdateHelper.updateAllWidgets(context)
                            } else {
                                Toast.makeText(context, "Failed to restore: Invalid JSON structure", Toast.LENGTH_LONG).show()
                            }
                            showRestoreDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Dialog: Companion Sync Credentials
    if (showSyncAccountDialog) {
        var userDialogText by remember { mutableStateOf(if (syncUsernameInput.isNotBlank()) syncUsernameInput else "katurtusridhar") }
        var passDialogText by remember { mutableStateOf("") }
        var inputError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSyncAccountDialog = false },
            title = {
                Text("Companion Sync Credentials", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Column {
                    Text(
                        "Enter your website username and password to authenticate Android-to-Website progress synchronization. Credentials are stored securely in private device preferences.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = userDialogText,
                        onValueChange = { userDialogText = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passDialogText,
                        onValueChange = { passDialogText = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (inputError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(inputError ?: "", fontSize = 11.sp, color = colors.rose)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanUser = userDialogText.trim()
                        val cleanPass = passDialogText.trim()
                        if (cleanUser.isBlank() || cleanPass.isBlank()) {
                            inputError = "Username and password cannot be empty."
                            return@Button
                        }
                        SyncApiClient.saveCredentials(context, cleanUser, cleanPass)
                        syncUsernameInput = cleanUser
                        isSyncConfigured = true
                        showSyncAccountDialog = false
                        Toast.makeText(context, "Credentials saved securely.", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            val res = repository.syncWithCloud()
                            if (res.success) {
                                Toast.makeText(context, "Initial sync verified!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncAccountDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun ThemeOptionButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GateTheme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) colors.primary else colors.cardElevated.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (isSelected) colors.primary else colors.cardBorder.copy(alpha = 0.6f)
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else colors.textPrimary
                )
            }
        }
    }
}
