package com.gogether.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gogether.app.core.ui.AppUi
import com.gogether.app.core.ui.LocalPageActionWidth

private val LightColors = lightColorScheme(
    primary = AppUi.Primary, onPrimary = Color.White,
    primaryContainer = AppUi.Lilac, onPrimaryContainer = AppUi.Primary,
    secondary = AppUi.Primary, secondaryContainer = AppUi.SelectedTag,
    onSecondaryContainer = AppUi.SelectedTagText,
    tertiary = AppUi.Primary, onTertiary = Color.White,
    surfaceTint = AppUi.Primary, inversePrimary = AppUi.Primary,
    background = Color.White, surface = Color.White,
    surfaceContainer = Color(0xFFF4F4F6), surfaceContainerLow = Color(0xFFF7F8FA),
    onSurface = Color(0xFF26272B), onSurfaceVariant = Color(0xFF646870),
    outline = Color(0xFF92969E), outlineVariant = Color(0xFFE3E5E8),
)
private val DarkColors = darkColorScheme(
    primary = AppUi.Primary, onPrimary = Color.White,
    primaryContainer = Color(0xFF302D35), onPrimaryContainer = AppUi.Lilac,
    secondary = AppUi.Primary, secondaryContainer = AppUi.SelectedTag,
    onSecondaryContainer = AppUi.SelectedTagText,
    tertiary = AppUi.Primary, onTertiary = Color.White,
    surfaceTint = AppUi.Primary, inversePrimary = AppUi.Primary,
)

@Composable
fun GogetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(extraSmall = AppUi.FieldShape, small = AppUi.TagShape,
            medium = AppUi.CardShape, large = AppUi.CardShape),
        typography = Typography(
            headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
            headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CompositionLocalProvider(LocalPageActionWidth provides maxWidth * 0.8f) {
                content()
            }
        }
    }
}
