package com.example.sigaratakip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OceanColorScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = OceanOnPrimary,
    primaryContainer = OceanPrimaryDeep,
    onPrimaryContainer = OceanOnSurface,
    secondary = OceanSecondary,
    onSecondary = OceanOnPrimary,
    tertiary = OceanTertiary,
    onTertiary = OceanOnPrimary,
    background = OceanBackground,
    onBackground = OceanOnSurface,
    surface = OceanSurface,
    onSurface = OceanOnSurface,
    surfaceVariant = OceanSurfaceVariant,
    onSurfaceVariant = OceanOnSurfaceMuted,
    error = DangerRed,
    onError = OceanOnPrimary
)

@Composable
fun SigaraTakipTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OceanColorScheme,
        typography = Typography,
        content = content
    )
}
