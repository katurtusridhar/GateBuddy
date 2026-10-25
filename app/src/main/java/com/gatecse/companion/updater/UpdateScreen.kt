package com.gatecse.companion.updater

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gatecse.companion.ui.theme.GateTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onNavigateBack: () -> Unit,
    viewModel: UpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = GateTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val confirmationRelease by viewModel.selectedReleaseForConfirmation.collectAsState()

    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingInstallApk by remember { mutableStateOf<File?>(null) }
    var pendingRelease by remember { mutableStateOf<ReleaseEntry?>(null) }
    var pendingSettingsIntent by remember { mutableStateOf<Intent?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (viewModel.installer.canRequestPackageInstalls()) {
            pendingInstallApk?.let { apk ->
                val result = viewModel.installer.prepareInstallIntent(apk)
                if (result is InstallIntentResult.ReadyToLaunch) {
                    pendingRelease?.let { viewModel.onInstallStarted(it) }
                    context.startActivity(result.installIntent)
                }
            }
        } else {
            Toast.makeText(context, "Install permission is required to update the app.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Updates & Version History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.checkForUpdates() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface
                )
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Currently Installed App Info
            item {
                InstalledAppCard(
                    packageName = context.packageName,
                    versionName = viewModel.installedVersionName,
                    versionCode = viewModel.installedVersionCode,
                    channel = viewModel.activeChannel
                )
            }

            // Card 2: Update Status / Action Card
            item {
                UpdateStatusCard(
                    uiState = uiState,
                    onCheckUpdates = { viewModel.checkForUpdates() },
                    onDownload = { release -> viewModel.startDownload(release) },
                    onCancelDownload = { viewModel.cancelDownload() },
                    onInstall = { release, apkFile ->
                        val result = viewModel.installer.prepareInstallIntent(apkFile)
                        when (result) {
                            is InstallIntentResult.ReadyToLaunch -> {
                                viewModel.onInstallStarted(release)
                                context.startActivity(result.installIntent)
                            }
                            is InstallIntentResult.PermissionRequired -> {
                                pendingInstallApk = apkFile
                                pendingRelease = release
                                pendingSettingsIntent = result.settingsIntent
                                showPermissionDialog = true
                            }
                            is InstallIntentResult.Failure -> {
                                Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            // Section 3: Version History List
            item {
                Text(
                    text = "Release Catalog & History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val releases = catalog?.releases ?: emptyList()
            if (releases.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.card),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "No prior releases cataloged for this channel.",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(releases) { release ->
                    val actionState = viewModel.evaluateReleaseAction(release)
                    VersionManagerReleaseItem(
                        release = release,
                        actionState = actionState,
                        installedVersionCode = viewModel.installedVersionCode,
                        onRequestInstall = { r -> viewModel.requestReleaseInstallation(r) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showPermissionDialog && pendingSettingsIntent != null) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = colors.primary) },
            title = { Text("Permission Required", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "To install updates without Google Play Store, Android requires granting permission to install unknown apps from this source.\n\nTap 'Settings' and toggle 'Allow from this source', then return to complete installation."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        permissionLauncher.launch(pendingSettingsIntent!!)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    confirmationRelease?.let { release ->
        val actionState = viewModel.evaluateReleaseAction(release)
        VersionConfirmationDialog(
            release = release,
            actionState = actionState,
            currentBuild = viewModel.installedVersionCode,
            onConfirm = { viewModel.confirmReleaseInstallation() },
            onDismiss = { viewModel.dismissConfirmation() }
        )
    }
}

@Composable
fun InstalledAppCard(
    packageName: String,
    versionName: String,
    versionCode: Long,
    channel: String
) {
    val colors = GateTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GATEBuddy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (channel == "debug") colors.amber.copy(alpha = 0.2f) else colors.primary.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, if (channel == "debug") colors.amber else colors.primary)
                ) {
                    Text(
                        text = channel.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (channel == "debug") colors.amber else colors.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Installed: Version $versionName (Build $versionCode)",
                fontSize = 14.sp,
                color = colors.textSecondary
            )
            Text(
                text = "Package: $packageName",
                fontSize = 12.sp,
                color = colors.textSecondary.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun UpdateStatusCard(
    uiState: UpdateUiState,
    onCheckUpdates: () -> Unit,
    onDownload: (ReleaseEntry) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (ReleaseEntry, File) -> Unit
) {
    val colors = GateTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardElevated),
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (uiState) {
                is UpdateUiState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Checking for updates...",
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                    }
                }

                is UpdateUiState.UpToDate -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.emerald,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "App is up to date",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Version ${uiState.installedVersionName} is the latest release.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onCheckUpdates,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Check Again")
                    }
                }

                is UpdateUiState.UpdateAvailable -> {
                    val rel = uiState.release
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Update Available: v${rel.versionName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Build ${rel.versionCode} • ${(rel.apkSizeBytes / (1024 * 1024))} MB",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    if (rel.changelog.summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = rel.changelog.summary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                    }

                    if (rel.changelog.highlights.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        rel.changelog.highlights.forEach { highlight ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("• ", color = colors.primary, fontWeight = FontWeight.Bold)
                                Text(highlight, fontSize = 12.sp, color = colors.textSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onDownload(rel) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download & Update")
                    }
                }

                is UpdateUiState.Downloading -> {
                    val p = uiState.progress
                    Text(
                        text = "Downloading v${uiState.release.versionName}...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { p.percentage / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = colors.primary,
                        trackColor = colors.card
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(p.bytesDownloaded / (1024 * 1024))} MB / ${(p.totalBytes / (1024 * 1024))} MB",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "${p.percentage}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel Download")
                    }
                }

                is UpdateUiState.Verifying -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Verifying package signature & checksum...",
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                    }
                }

                is UpdateUiState.ReadyToInstall -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.emerald,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Update Ready to Install",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "v${uiState.release.versionName} verified and ready.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { onInstall(uiState.release, uiState.apkFile) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.emerald)
                    ) {
                        Text("Install Now")
                    }
                }

                is UpdateUiState.Installing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Waiting for installation confirmation...",
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                    }
                }

                is UpdateUiState.Error -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = colors.amber,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Update Check Failed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = uiState.message,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                    if (uiState.canRetry) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onCheckUpdates,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }

                is UpdateUiState.Idle -> {
                    OutlinedButton(
                        onClick = onCheckUpdates,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Check for Updates")
                    }
                }
            }
        }
    }
}

@Composable
fun VersionManagerReleaseItem(
    release: ReleaseEntry,
    actionState: ReleaseActionState,
    installedVersionCode: Long,
    onRequestInstall: (ReleaseEntry) -> Unit
) {
    val colors = GateTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(
            1.dp,
            when (actionState) {
                is ReleaseActionState.Current -> colors.primary.copy(alpha = 0.6f)
                is ReleaseActionState.CanRestore -> colors.amber.copy(alpha = 0.5f)
                is ReleaseActionState.CanUpdate -> colors.emerald.copy(alpha = 0.5f)
                else -> colors.cardBorder
            }
        )
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(14.dp)
        ) {
            // Header Row: Version and Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "v${release.behaviorVersion.ifEmpty { release.installVersionName }}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Build ${release.installVersionCode}",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                    if (release.releaseType.equals("rollback", ignoreCase = true)) {
                        Text(
                            text = "Restores v${release.restoresBehaviorVersion ?: release.behaviorVersion} behavior",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.amber
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (actionState) {
                        is ReleaseActionState.Current -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.primary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "CURRENT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        is ReleaseActionState.CanRestore -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.amber.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "ROLLBACK",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.amber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        is ReleaseActionState.CanUpdate -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.emerald.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "NEWER",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.emerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        is ReleaseActionState.HistoricalBuildRequired -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.surface
                            ) {
                                Text(
                                    text = "HISTORICAL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        is ReleaseActionState.Incompatible -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.rose.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "INCOMPATIBLE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.rose,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colors.textSecondary
                    )
                }
            }

            // Action Button / Status
            Spacer(modifier = Modifier.height(10.dp))
            when (actionState) {
                is ReleaseActionState.Current -> {
                    Text(
                        text = "• Currently installed and active on this device.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                is ReleaseActionState.CanUpdate -> {
                    Button(
                        onClick = { onRequestInstall(release) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.emerald),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update to v${release.behaviorVersion} (Build ${release.installVersionCode})", fontWeight = FontWeight.Bold)
                    }
                }
                is ReleaseActionState.CanRestore -> {
                    Button(
                        onClick = { onRequestInstall(release) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.amber),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore v${actionState.targetBehaviorVersion} (Build ${release.installVersionCode})", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                is ReleaseActionState.HistoricalBuildRequired -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Historical Build ${release.installVersionCode}. Direct downgrade is blocked by Android. Use a signed Restore build to run this version.",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                is ReleaseActionState.Incompatible -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.rose.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = colors.rose, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Unavailable — Compatibility Check Failed: ${actionState.reason}",
                            fontSize = 11.sp,
                            color = colors.rose
                        )
                    }
                }
            }

            // Expandable details
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (release.changelog.summary.isNotBlank()) {
                        Text(
                            text = release.changelog.summary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    release.changelog.highlights.forEach { h ->
                        Row(modifier = Modifier.padding(vertical = 1.dp)) {
                            Text("• ", color = colors.primary, fontSize = 11.sp)
                            Text(h, fontSize = 11.sp, color = colors.textSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Release Date: ${release.releaseDate} | Type: ${release.releaseType.uppercase()}",
                        fontSize = 10.sp,
                        color = colors.textSecondary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Database Version: ${release.database.databaseVersion} (Min: ${release.database.minSupportedDatabaseVersion}) | Min Android SDK: ${release.compatibility.minSdk}",
                        fontSize = 10.sp,
                        color = colors.textSecondary.copy(alpha = 0.8f)
                    )
                    val sizeMb = "%.2f".format(java.util.Locale.US, release.apkSizeBytes / (1024.0 * 1024.0))
                    Text(
                        text = "APK Size: $sizeMb MB | Verification: ${release.verificationStatus.uppercase()}",
                        fontSize = 10.sp,
                        color = colors.textSecondary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun VersionConfirmationDialog(
    release: ReleaseEntry,
    actionState: ReleaseActionState,
    currentBuild: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = GateTheme.colors
    val isRollback = release.releaseType.equals("rollback", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isRollback) Icons.Default.Refresh else Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = if (isRollback) colors.amber else colors.emerald
            )
        },
        title = {
            Text(
                text = if (isRollback) "Confirm Behavior Rollback" else "Confirm Forward Update",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isRollback) {
                        "You are requesting to restore behavior version v${release.restoresBehaviorVersion ?: release.behaviorVersion}."
                    } else {
                        "You are requesting to update to behavior version v${release.behaviorVersion}."
                    },
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Target Package: Build ${release.installVersionCode} (${release.installVersionName})",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "Current Package: Build $currentBuild",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "Android Installation: Forward upgrade (${release.installVersionCode} > $currentBuild), accepted by PackageInstaller.",
                            fontSize = 12.sp,
                            color = colors.emerald,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.emerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Database Safety: 100% Data Preserved",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.emerald
                            )
                        }
                        Text(
                            text = "Compatible with Room Database Schema Version 3. All 8 entities (schedule tasks, DPP records, progress, achievements, settings) will be retained with zero data loss.",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                if (release.changelog.summary.isNotBlank()) {
                    Text(
                        text = "Summary: ${release.changelog.summary}",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRollback) colors.amber else colors.emerald
                )
            ) {
                Text(
                    text = if (isRollback) "Proceed with Restore" else "Proceed with Update",
                    fontWeight = FontWeight.Bold,
                    color = if (isRollback) Color.Black else Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
