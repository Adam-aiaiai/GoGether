package com.gogether.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF6836C9), onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE3FF), onPrimaryContainer = Color(0xFF32145E),
    secondary = Color(0xFF8D470C), secondaryContainer = Color(0xFFFFDDBE),
    onSecondaryContainer = Color(0xFF321600),
    background = Color(0xFFFCFAFF), surface = Color(0xFFFCFAFF),
    surfaceContainer = Color(0xFFF2EDF8), surfaceContainerLow = Color(0xFFF7F3FB),
    onSurface = Color(0xFF241C30), onSurfaceVariant = Color(0xFF62586C),
    outlineVariant = Color(0xFFDDD4E7),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFD2BBFF), primaryContainer = Color(0xFF4D2399),
    secondary = Color(0xFFFFB873), secondaryContainer = Color(0xFF683600),
)

@Composable
fun GogetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(
            headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
        ), content = content,
    )
}
