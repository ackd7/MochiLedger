package com.mochiledger.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochiledger.shared.model.AppAppearanceConfig
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

data class MochiAccentPalette(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

val MochiAccentPresets = listOf(
    MochiAccentPalette(
        name = "墨蓝 · Indigo",
        primary = Color(0xFF4F6EF7),
        secondary = Color(0xFF7B61FF),
        heroStart = Color(0xFF3E5CE6),
        heroEnd = Color(0xFF7B61FF),
    ),
    MochiAccentPalette(
        name = "抹茶 · Matcha",
        primary = Color(0xFF16A34A),
        secondary = Color(0xFF0D9488),
        heroStart = Color(0xFF15803D),
        heroEnd = Color(0xFF0F766E),
    ),
    MochiAccentPalette(
        name = "樱桃 · Sakura",
        primary = Color(0xFFE11D48),
        secondary = Color(0xFFEC4899),
        heroStart = Color(0xFFBE123C),
        heroEnd = Color(0xFFDB2777),
    ),
    MochiAccentPalette(
        name = "琥珀 · Amber",
        primary = Color(0xFFD97706),
        secondary = Color(0xFFEA580C),
        heroStart = Color(0xFFB45309),
        heroEnd = Color(0xFFEA580C),
    ),
)

val LocalMochiAccent = compositionLocalOf { MochiAccentPresets[0] }
val LocalMochiDark = compositionLocalOf { false }

object MochiColors {
    val Expense = Color(0xFFEF4444)
    val Income = Color(0xFF10B981)
    val Transfer = Color(0xFF3B82F6)
    val WeChatGreen = Color(0xFF07C160)
    val AlipayBlue = Color(0xFF1677FF)
    val WarningAmber = Color(0xFFF59E0B)
}

@Composable
fun MochiLedgerTheme(
    appearance: AppAppearanceConfig,
    content: @Composable () -> Unit,
) {
    val accent = MochiAccentPresets.getOrElse(appearance.accentPreset) { MochiAccentPresets[0] }
    val isDark = when (appearance.colorMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val controller = remember(appearance.colorMode, accent.primary) {
        val mode = when (appearance.colorMode) {
            1 -> ColorSchemeMode.MonetLight
            2 -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetSystem
        }
        ThemeController(mode, keyColor = accent.primary)
    }

    CompositionLocalProvider(
        LocalMochiAccent provides accent,
        LocalMochiDark provides isDark,
        LocalSquircleEnabled provides appearance.enableSquircle,
    ) {
        MiuixTheme(controller = controller) {
            content()
        }
    }
}

fun mochiShape(radius: Dp = 20.dp): Shape = RoundedCornerShape(radius)

@Composable
fun mochiHeroBrush(): Brush {
    val accent = LocalMochiAccent.current
    return Brush.linearGradient(listOf(accent.heroStart, accent.heroEnd))
}
