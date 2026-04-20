package com.example.sleeptimer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SleepPrimary,
    onPrimary = Color.White,
    primaryContainer = SleepDarkSurfaceVariant,
    secondary = SleepSecondary,
    onSecondary = Color.White,
    tertiary = SleepAccentPink,
    background = SleepDarkBackground,
    onBackground = SleepTextPrimary,
    surface = SleepDarkSurface,
    onSurface = SleepTextPrimary,
    surfaceVariant = SleepDarkSurfaceVariant,
    onSurfaceVariant = SleepTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = SleepLightPrimary,
    onPrimary = Color.White,
    secondary = SleepLightSecondary,
    background = SleepLightBackground,
    onBackground = SleepLightText,
    surface = SleepLightSurface,
    onSurface = SleepLightText,
    surfaceVariant = SleepLightSurfaceVariant,
    onSurfaceVariant = SleepLightTextSecondary
)

@Composable
fun SleeptimerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}