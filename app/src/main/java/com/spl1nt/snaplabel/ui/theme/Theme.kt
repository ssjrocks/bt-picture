package com.spl1nt.snaplabel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFFE85D4C)

private val LightColors = lightColorScheme(primary = Accent, secondary = Accent, tertiary = Accent)
private val DarkColors = darkColorScheme(primary = Accent, secondary = Accent, tertiary = Accent)

@Composable
fun SnapLabelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
