package com.jpcottin.skylens.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Indigo80, secondary = Cyan80, tertiary = Amber80,
    background = NightBackground, surface = NightSurface, onBackground = Indigo90, onSurface = Indigo90,
)
private val LightColorScheme = lightColorScheme(primary = Indigo40, secondary = Cyan40, tertiary = Amber40)

/** Red-on-black scheme that keeps the eyes dark-adapted at the telescope. */
private val NightVisionScheme = darkColorScheme(
    primary = NightVisionRed, onPrimary = NightVisionBackground,
    secondary = NightVisionRed, onSecondary = NightVisionBackground,
    tertiary = NightVisionRed, onTertiary = NightVisionBackground,
    background = NightVisionBackground, onBackground = NightVisionRed,
    surface = NightVisionBackground, onSurface = NightVisionRed,
    surfaceVariant = Color(0xFF1A0505), onSurfaceVariant = NightVisionDimRed,
    primaryContainer = Color(0xFF330A08), onPrimaryContainer = NightVisionRed,
    secondaryContainer = Color(0xFF330A08), onSecondaryContainer = NightVisionRed,
    outline = NightVisionDimRed, error = NightVisionRed,
)

/**
 * Material 3 theme for the phone UI. Dark by default (it is an astronomy app); dynamic colour
 * follows the wallpaper when enabled; [nightMode] switches to the red-on-black scheme.
 */
@Composable
fun SkyLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    nightMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        nightMode -> NightVisionScheme
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
