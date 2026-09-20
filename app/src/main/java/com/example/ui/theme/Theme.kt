package com.example.ui.theme

import android.os.Build
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
    primary = IosSystemBlue,
    onPrimary = Color.White,
    primaryContainer = IosSurfaceVariantDark,
    onPrimaryContainer = Color.White,
    secondary = IosSystemIndigo,
    onSecondary = Color.White,
    secondaryContainer = IosSurfaceVariantDark,
    onSecondaryContainer = Color.White,
    tertiary = IosSystemGreen,
    onTertiary = Color.White,
    background = IosBackgroundDark,
    onBackground = IosTextPrimaryDark,
    surface = IosSurfaceDark,
    onSurface = IosTextPrimaryDark,
    surfaceVariant = IosSurfaceVariantDark,
    onSurfaceVariant = IosTextSecondaryDark,
    outline = IosBorderDark,
    error = IosSystemRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = IosSystemBlue,
    onPrimary = Color.White,
    primaryContainer = IosSystemBlueContainer,
    onPrimaryContainer = IosSystemBlue,
    secondary = IosSystemIndigo,
    onSecondary = Color.White,
    secondaryContainer = IosSurfaceVariantLight,
    onSecondaryContainer = IosTextPrimary,
    tertiary = IosSystemGreen,
    onTertiary = Color.White,
    background = IosBackgroundLight,
    onBackground = IosTextPrimary,
    surface = IosSurfaceLight,
    onSurface = IosTextPrimary,
    surfaceVariant = IosSurfaceVariantLight,
    onSurfaceVariant = IosTextSecondary,
    outline = IosBorderLight,
    error = IosSystemRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Light mode is the default on launch as required
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

