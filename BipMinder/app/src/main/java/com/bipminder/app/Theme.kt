package com.bipminder.app

import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*

@Immutable
class BipColors(
    val bg: Color, val card: Color, val text: Color, val muted: Color, val accent: Color,
    val onAccent: Color, val chip: Color, val panel: Color, val line: Color, val dark: Boolean
)

val LightBip = BipColors(
    bg = Color(0xFFB8BDF2), card = Color(0xFFFFFFFF), text = Color(0xFF242326), muted = Color(0xFF55535F),
    accent = Color(0xFF6269C5), onAccent = Color.White, chip = Color(0x99FFFFFF),
    panel = Color(0xFFCDD0F7), line = Color(0x1F242326), dark = false
)
val DarkBip = BipColors(
    bg = Color(0xFF17172A), card = Color(0xFF24243B), text = Color(0xFFF1F0F8), muted = Color(0xFFA6A4BA),
    accent = Color(0xFF7C83DE), onAccent = Color.White, chip = Color(0xFF2B2B45),
    panel = Color(0xFF2D2D4A), line = Color(0x24FFFFFF), dark = true
)

val LocalBip = staticCompositionLocalOf { LightBip }
val LocalInter = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }

val C: BipColors
    @Composable @ReadOnlyComposable get() = LocalBip.current

object Pri {
    val high = Color(0xFFE5484D)
    val med = Color(0xFFF2A33A)
    val low = Color(0xFF46A86B)
    fun color(p: Int): Color = when (p) { 2 -> high; 1 -> med; else -> low }
    fun label(p: Int): String = when (p) { 2 -> "High"; 1 -> "Medium"; else -> "Low" }
}

@OptIn(ExperimentalTextApi::class)
fun loadInter(ctx: Context): FontFamily = try {
    if (ctx.assets.list("")?.contains("inter.ttf") == true) {
        FontFamily(listOf(300, 400, 500, 600, 700).map { w ->
            Font("inter.ttf", ctx.assets, FontWeight(w), FontStyle.Normal,
                FontVariation.Settings(FontVariation.weight(w)))
        })
    } else FontFamily.SansSerif
} catch (e: Throwable) { FontFamily.SansSerif }

@Composable
fun BipTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkBip else LightBip
    val ctx = LocalContext.current
    val fam = remember { loadInter(ctx) }
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
        surface = c.card, onSurface = c.text, surfaceVariant = c.panel, onSurfaceVariant = c.muted,
        outline = c.line, surfaceContainerHigh = c.card, surfaceContainerHighest = c.panel,
        surfaceContainer = c.card, surfaceContainerLow = c.card
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
        surface = c.card, onSurface = c.text, surfaceVariant = c.panel, onSurfaceVariant = c.muted,
        outline = c.line, surfaceContainerHigh = c.card, surfaceContainerHighest = c.panel,
        surfaceContainer = c.card, surfaceContainerLow = c.card
    )
    CompositionLocalProvider(LocalBip provides c, LocalInter provides fam) {
        MaterialTheme(colorScheme = scheme) {
            ProvideTextStyle(TextStyle(fontFamily = fam, color = c.text)) { content() }
        }
    }
}
