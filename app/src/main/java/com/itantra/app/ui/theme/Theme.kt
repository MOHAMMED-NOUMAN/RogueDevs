package com.itantra.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * iTantra is light-only: every screen paints its own light background, so the Material
 * scheme (dialogs, text fields, switches, menus) is fixed to the app's greens instead of
 * following dark mode or the wallpaper's dynamic colours.
 */
private val ItantraColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = SoftLightGreen,
    onPrimaryContainer = DeepDarkGreen,
    secondary = DeepDarkGreen,
    onSecondary = Color.White,
    background = OffWhite,
    onBackground = DeepDarkGreen,
    surface = Color.White,
    onSurface = DeepDarkGreen,
    surfaceVariant = OffWhite,
    onSurfaceVariant = MutedGreenText,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = GrayBorder,
    outlineVariant = GrayBorder,
    error = SosRed,
)

@Composable
fun ItantraTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ItantraColorScheme,
        typography = Typography,
        content = content,
    )
}
