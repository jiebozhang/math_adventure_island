package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = NavbarBlue,
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
    surface = Color.White,
    onSurface = TextDark,
    outline = BorderGray,
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
        content = content
    )
}
