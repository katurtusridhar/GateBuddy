package com.gatecse.companion.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.gatecse.companion.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = Color.White,
    primaryContainer = ElectricIndigoContainer,
    onPrimaryContainer = Color.White,
    secondary = SkyBlue,
    onSecondary = DarkBg,
    secondaryContainer = SkyBlueContainer,
    onSecondaryContainer = Color.White,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkNavySurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSlateCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkSlateCardBorder,
    error = RoseError,
    onError = Color.White,
    errorContainer = RoseContainer
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = LightIndigoContainer,
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = LightSkyContainer,
    onSecondaryContainer = Color(0xFF075985),
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurfaceColor,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSlateCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightSlateCardBorder,
    error = RoseError,
    onError = Color.White,
    errorContainer = LightRoseContainer
)

@Composable
fun GateCompanionTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val appColors = if (darkTheme) {
        AppColors(
            isDark = true,
            background = DarkBg,
            surface = DarkNavySurface,
            card = DarkSlateCard,
            cardElevated = DarkSlateCardElevated,
            cardBorder = DarkSlateCardBorder,
            textPrimary = DarkTextPrimary,
            textSecondary = DarkTextSecondary,
            textMuted = DarkTextMuted,
            primary = ElectricIndigo,
            primaryContainer = ElectricIndigoContainer,
            sky = SkyBlue,
            skyContainer = SkyBlueContainer,
            emerald = EmeraldSuccess,
            emeraldContainer = EmeraldContainer,
            amber = AmberWarning,
            amberContainer = AmberContainer,
            rose = RoseError,
            roseContainer = RoseContainer
        )
    } else {
        AppColors(
            isDark = false,
            background = LightBg,
            surface = LightSurfaceColor,
            card = LightSlateCard,
            cardElevated = LightSlateCardElevated,
            cardBorder = LightSlateCardBorder,
            textPrimary = LightTextPrimary,
            textSecondary = LightTextSecondary,
            textMuted = LightTextMuted,
            primary = Color(0xFF4F46E5),
            primaryContainer = LightIndigoContainer,
            sky = Color(0xFF0284C7),
            skyContainer = LightSkyContainer,
            emerald = Color(0xFF059669),
            emeraldContainer = LightEmeraldContainer,
            amber = Color(0xFFD97706),
            amberContainer = LightAmberContainer,
            rose = Color(0xFFE11D48),
            roseContainer = LightRoseContainer
        )
    }

    // Keep global aliases in sync
    SlateCard = appColors.card
    SlateCardBorder = appColors.cardBorder
    TextPrimary = appColors.textPrimary
    TextSecondary = appColors.textSecondary
    TextMuted = appColors.textMuted

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
