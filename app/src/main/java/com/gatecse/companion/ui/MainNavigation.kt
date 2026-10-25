package com.gatecse.companion.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gatecse.companion.data.CandidateDateSlot
import com.gatecse.companion.data.RescheduleValidationResult
import com.gatecse.companion.data.ScheduleRepository
import com.gatecse.companion.model.TaskItemUi
import com.gatecse.companion.model.ThemeMode
import com.gatecse.companion.ui.components.BacklogSequentialSchedulerDialog
import com.gatecse.companion.ui.screens.achievements.AchievementsScreen
import com.gatecse.companion.ui.screens.analytics.AnalyticsScreen
import com.gatecse.companion.ui.screens.calendar.CalendarScreen
import com.gatecse.companion.ui.screens.settings.SettingsScreen
import com.gatecse.companion.ui.screens.subjects.SubjectsScreen
import com.gatecse.companion.ui.screens.tests.TestsScreen
import com.gatecse.companion.ui.screens.today.TodayScreen
import com.gatecse.companion.ui.theme.GateTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Today : Screen("today", "Today", Icons.Default.Today)
    object Calendar : Screen("calendar", "Calendar", Icons.Default.CalendarMonth)
    object Subjects : Screen("subjects", "Subjects", Icons.AutoMirrored.Filled.MenuBook)
    object Analytics : Screen("analytics", "Analytics", Icons.Default.Analytics)
    object Tests : Screen("tests", "Tests & DPP", Icons.AutoMirrored.Filled.Assignment)
    object Achievements : Screen("achievements", "Badges", Icons.Default.EmojiEvents)
    object Settings : Screen("settings", "Settings", Icons.Default.Menu)
}

@Composable
fun MainNavigation(
    repository: ScheduleRepository,
    currentThemeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    navController: NavHostController = rememberNavController(),
    initialRoute: String = Screen.Today.route
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: initialRoute
    val colors = GateTheme.colors
    var todayRefreshTrigger by remember { mutableLongStateOf(0L) }

    val kolkataZone = remember { ZoneId.of("Asia/Kolkata") }
    var todayDate by remember { mutableStateOf(LocalDate.now(kolkataZone)) }
    val todayDateStr = remember(todayDate) { todayDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
    var unrescheduledBacklogs by remember { mutableStateOf<List<TaskItemUi>>(emptyList()) }
    var candidateRescheduleDates by remember { mutableStateOf<List<String>>(emptyList()) }
    var candidateSlots by remember { mutableStateOf<List<CandidateDateSlot>>(emptyList()) }
    var showGlobalSchedulerDialog by remember { mutableStateOf(false) }

    fun checkBacklogs() {
        scope.launch {
            repository.reconcileRecovery(todayDateStr)
            val backlogs = repository.getUnrescheduledBacklogs(todayDateStr)
            unrescheduledBacklogs = backlogs
            candidateRescheduleDates = repository.getCandidateRescheduleDates(todayDateStr)
            candidateSlots = repository.getCandidateSlotsInfo(todayDateStr)
            showGlobalSchedulerDialog = backlogs.isNotEmpty()
        }
    }

    // Lifecycle observer to handle date transition at 12 AM
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentNow = LocalDate.now(kolkataZone)
                if (currentNow != todayDate) {
                    todayDate = currentNow
                }
                checkBacklogs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(todayDateStr, todayRefreshTrigger) {
        checkBacklogs()
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = colors.card,
                tonalElevation = 8.dp,
                modifier = Modifier.border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                colors.cardBorder.copy(alpha = 0.8f),
                                colors.cardBorder.copy(alpha = 0.2f)
                            )
                        )
                    )
                )
            ) {
                listOf(Screen.Today, Screen.Calendar, Screen.Subjects, Screen.Analytics, Screen.Tests, Screen.Settings).forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (screen == Screen.Today) {
                                todayRefreshTrigger = System.currentTimeMillis()
                                if (currentRoute != Screen.Today.route) {
                                    val popped = navController.popBackStack(Screen.Today.route, inclusive = false)
                                    if (!popped) {
                                        navController.navigate(Screen.Today.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            } else {
                                // Strictly lock out any other activity if unrescheduled backlogs exist
                                if (unrescheduledBacklogs.isNotEmpty()) {
                                    showGlobalSchedulerDialog = true
                                    Toast.makeText(
                                        context,
                                        "⚠️ All activities locked: You have ${unrescheduledBacklogs.size} pending backlogs to reschedule first.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@NavigationBarItem
                                }
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 10.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.primary,
                            selectedTextColor = colors.primary,
                            unselectedIconColor = colors.textMuted,
                            unselectedTextColor = colors.textMuted,
                            indicatorColor = colors.primaryContainer.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = initialRoute
            ) {
                composable(Screen.Today.route) {
                    TodayScreen(
                        repository = repository,
                        onNavigateToCalendar = {
                            if (unrescheduledBacklogs.isEmpty()) {
                                navController.navigate(Screen.Calendar.route)
                            } else {
                                showGlobalSchedulerDialog = true
                            }
                        },
                        onNavigateToAnalytics = {
                            if (unrescheduledBacklogs.isEmpty()) {
                                navController.navigate(Screen.Analytics.route)
                            } else {
                                showGlobalSchedulerDialog = true
                            }
                        },
                        onNavigateToAchievements = {
                            if (unrescheduledBacklogs.isEmpty()) {
                                navController.navigate(Screen.Achievements.route)
                            } else {
                                showGlobalSchedulerDialog = true
                            }
                        },
                        refreshTrigger = todayRefreshTrigger
                    )
                }

                composable(Screen.Calendar.route) {
                    CalendarScreen(repository = repository)
                }

                composable(Screen.Subjects.route) {
                    SubjectsScreen(repository = repository)
                }

                composable(Screen.Analytics.route) {
                    AnalyticsScreen(
                        repository = repository,
                        onNavigateToToday = {
                            todayRefreshTrigger = System.currentTimeMillis()
                            val popped = navController.popBackStack(Screen.Today.route, inclusive = false)
                            if (!popped) {
                                navController.navigate(Screen.Today.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onNavigateToCalendar = {
                            if (unrescheduledBacklogs.isEmpty()) {
                                navController.navigate(Screen.Calendar.route)
                            } else {
                                showGlobalSchedulerDialog = true
                            }
                        }
                    )
                }

                composable(Screen.Tests.route) {
                    TestsScreen(repository = repository)
                }

                composable(Screen.Achievements.route) {
                    AchievementsScreen(repository = repository)
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        repository = repository,
                        currentThemeMode = currentThemeMode,
                        onThemeModeChanged = onThemeModeChanged
                    )
                }
            }

            // Global Non-Dismissible Backlog Rescheduler Dialog (Requirement: Restrict ALL other activity until backlogs are rescheduled)
            if (unrescheduledBacklogs.isNotEmpty()) {
                BacklogSequentialSchedulerDialog(
                    backlogs = unrescheduledBacklogs,
                    candidateDates = candidateRescheduleDates,
                    candidateSlots = candidateSlots,
                    onDismiss = {
                        if (unrescheduledBacklogs.isEmpty()) {
                            showGlobalSchedulerDialog = false
                        }
                    },
                    onConfirmAll = { assignments ->
                        scope.launch {
                            val result = repository.updateBatchRecoveryDates(assignments, todayDateStr)
                            when (result) {
                                is RescheduleValidationResult.Success -> {
                                    unrescheduledBacklogs = emptyList()
                                    showGlobalSchedulerDialog = false
                                    todayRefreshTrigger = System.currentTimeMillis()
                                    checkBacklogs()
                                    Toast.makeText(context, "All backlogs scheduled! Previous schedule intact.", Toast.LENGTH_LONG).show()
                                }
                                is RescheduleValidationResult.Error -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
