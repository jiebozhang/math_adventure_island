package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = CardBlue,
    onPrimaryContainer = TextDark,
    secondary = AccentOrange,
    onSecondary = Color.White,
    secondaryContainer = CardYellow,
    onSecondaryContainer = TextDark,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    background = BgColor,
    onBackground = TextDark,
    surface = SurfaceWhite,
    onSurface = TextDark,
    surfaceVariant = CardGray,
    onSurfaceVariant = TextSecondary,
    outline = BorderGray,
    outlineVariant = DividerLight,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
