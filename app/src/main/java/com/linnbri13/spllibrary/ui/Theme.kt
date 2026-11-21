package com.linnbri13.spllibrary.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SplLightColors = lightColorScheme(
    primary = Color(0xFF0B5D6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5EEF5),
    onPrimaryContainer = Color(0xFF002026),
    secondary = Color(0xFF3E636B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E8ED),
    onSecondaryContainer = Color(0xFF0E2B31),
    tertiary = Color(0xFF2E6B4E),
    onTertiary = Color.White,
    background = Color(0xFFF6F9FA),
    onBackground = Color(0xFF171D1E),
    surface = Color.White,
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFE7EFF1),
    onSurfaceVariant = Color(0xFF425054)
)

private val SplDarkColors = darkColorScheme(
    primary = Color(0xFF66D3E5),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF004E5C),
    onPrimaryContainer = Color(0xFFC5EEF5),
    secondary = Color(0xFFB3CBD1),
    onSecondary = Color(0xFF1E353A),
    secondaryContainer = Color(0xFF344B51),
    onSecondaryContainer = Color(0xFFD3E8ED),
    tertiary = Color(0xFF7ED9A9),
    onTertiary = Color(0xFF003821),
    background = Color(0xFF0F1516),
    onBackground = Color(0xFFE0E5E7),
    surface = Color(0xFF161D1F),
    onSurface = Color(0xFFE0E5E7),
    surfaceVariant = Color(0xFF222D30),
    onSurfaceVariant = Color(0xFFB7C5C9)
)

@Composable
fun SplLibraryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SplDarkColors else SplLightColors,
        content = content
    )
}
