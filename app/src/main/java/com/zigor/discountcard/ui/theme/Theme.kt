package com.zigor.discountcard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF081C45),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF24104A),
    tertiary = Color(0xFF0D9488),
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF14161C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFE9EAF0),
    onSurfaceVariant = Color(0xFF494B55),
    outline = Color(0xFFBFC2CC),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FC0FF),
    onPrimary = Color(0xFF002F69),
    primaryContainer = Color(0xFF1B4598),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFD3BBFF),
    onSecondary = Color(0xFF3B1E6B),
    tertiary = Color(0xFF66D7C8),
    background = Color(0xFF101217),
    onBackground = Color(0xFFE4E5EA),
    surface = Color(0xFF181A20),
    onSurface = Color(0xFFE4E5EA),
    surfaceVariant = Color(0xFF2A2D36),
    onSurfaceVariant = Color(0xFFC3C5CF),
    outline = Color(0xFF555863),
    error = Color(0xFFFFB4AB),
)

@Composable
fun DiscountCardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Контрастный цвет текста поверх цветной плашки карты. */
fun Color.contrastingContent(): Color = if (luminance() > 0.55f) Color(0xFF14161C) else Color.White
