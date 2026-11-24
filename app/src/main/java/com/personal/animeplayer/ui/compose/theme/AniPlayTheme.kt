package com.personal.animeplayer.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AniPlayColors {
    val Primary = Color(0xFF0084FF)
    val OnPrimary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF00A3FF)
    val OnSecondary = Color(0xFF000000)
    val AppleBlue = Color(0xFF0A84FF)
    val SkyBlue = Color(0xFF38BDF8)

    val Background = Color(0xFF000000)
    val Surface = Color(0xFF000000)
    val SurfaceVariant = Color(0xFF1B1C1E)
    val SurfaceContainerHigh = Color(0xFF25262A)
    val CardSelectedBg = Color(0xFF162A4D)

    val OnSurface = Color(0xFFF0F2F5)
    val OnSurfaceMuted = Color(0xFF8A94A6)
    val TextSecondary = Color(0xFF94A3B8)
    val TextSubtle = Color(0xFF64748B)

    val Danger = Color(0xFFFF5252)
    val DangerIos = Color(0xFFFF453A)
    val DangerBg = Color(0xFF2C1518)

    val ThumbBg = Color(0xFF101216)
    val RefreshContainer = Color(0xFF1A2035)
}

private val AniPlayDarkColorScheme = darkColorScheme(
    primary = AniPlayColors.Primary,
    onPrimary = AniPlayColors.OnPrimary,
    secondary = AniPlayColors.Secondary,
    onSecondary = AniPlayColors.OnSecondary,
    background = AniPlayColors.Background,
    onBackground = AniPlayColors.OnSurface,
    surface = AniPlayColors.Surface,
    onSurface = AniPlayColors.OnSurface,
    surfaceVariant = AniPlayColors.SurfaceVariant,
    onSurfaceVariant = AniPlayColors.OnSurfaceMuted,
    surfaceContainer = AniPlayColors.SurfaceVariant,
    surfaceContainerHigh = AniPlayColors.SurfaceContainerHigh,
    error = AniPlayColors.Danger,
    onError = Color.White
)

@Composable
fun AniPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AniPlayDarkColorScheme,
        content = content
    )
}
