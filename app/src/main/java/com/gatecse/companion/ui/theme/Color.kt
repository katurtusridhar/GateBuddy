package com.gatecse.companion.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ==========================================
// LUXURY APP PALETTE (Dark & Light)
// Engineered for $5,000 top-tier fintech/edtech aesthetic
// ==========================================

// Dark Palette — Deep Obsidian & Stellar Slate
val DarkBg = Color(0xFF090D16)
val DarkNavySurface = Color(0xFF0F172A)
val DarkSlateCard = Color(0xFF151F32)
val DarkSlateCardElevated = Color(0xFF1E2B45)
val DarkSlateCardBorder = Color(0xFF26354F)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkTextMuted = Color(0xFF64748B)

// Light Palette — Crisp Ceramic & Studio Porcelain
val LightBg = Color(0xFFF6F8FC)
val LightSurfaceColor = Color(0xFFFFFFFF)
val LightSlateCard = Color(0xFFFFFFFF)
val LightSlateCardElevated = Color(0xFFF8FAFD)
val LightSlateCardBorder = Color(0xFFE2E8F0)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextMuted = Color(0xFF94A3B8)

// Vibrant Brand Accents (Carefully calibrated contrast ratios)
val ElectricIndigo = Color(0xFF6366F1)
val ElectricIndigoContainer = Color(0xFF1E1B4B)
val LightIndigoContainer = Color(0xFFEEF2FF)

val SkyBlue = Color(0xFF0EA5E9)
val SkyBlueContainer = Color(0xFF082F49)
val LightSkyContainer = Color(0xFFE0F2FE)

val EmeraldSuccess = Color(0xFF10B981)
val EmeraldContainer = Color(0xFF064E3B)
val LightEmeraldContainer = Color(0xFFECFDF5)

val AmberWarning = Color(0xFFF59E0B)
val AmberContainer = Color(0xFF451A03)
val LightAmberContainer = Color(0xFFFFFBEB)

val RoseError = Color(0xFFF43F5E)
val RoseContainer = Color(0xFF4C0519)
val LightRoseContainer = Color(0xFFFFF1F2)

// Global aliases for backward compatibility
val DeepOnyx = DarkBg
val DeepNavySurface = DarkNavySurface
var SlateCard = DarkSlateCard
var SlateCardBorder = DarkSlateCardBorder
var TextPrimary = DarkTextPrimary
var TextSecondary = DarkTextSecondary
var TextMuted = DarkTextMuted

val LightBackground = LightBg
val LightSurface = LightSurfaceColor
val LightCard = LightSlateCard
val LightCardBorder = LightSlateCardBorder

data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val card: Color,
    val cardElevated: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryContainer: Color,
    val sky: Color,
    val skyContainer: Color,
    val emerald: Color,
    val emeraldContainer: Color,
    val amber: Color,
    val amberContainer: Color,
    val rose: Color,
    val roseContainer: Color
)

val LocalAppColors = staticCompositionLocalOf {
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
}

object GateTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
