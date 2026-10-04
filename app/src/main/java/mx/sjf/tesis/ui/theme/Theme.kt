package mx.sjf.tesis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val LocalIsDark = staticCompositionLocalOf { true }

val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = NavyBg,
    primaryContainer = NavySurfaceVariant,
    onPrimaryContainer = GoldLight,
    background = NavyBg,
    onBackground = Color(0xFFE9EEF5),
    surface = NavySurface,
    onSurface = Color(0xFFE9EEF5),
    surfaceVariant = NavyChip,
    onSurfaceVariant = Color(0xFFA9BBD0),
    outline = NavyBorder,
    outlineVariant = PremiumDividerDark,
    error = ErrorRed,
    onError = Color.White,
    secondary = InfoBlue,
    onSecondary = NavyBg,
    secondaryContainer = NavySurfaceVariant,
    onSecondaryContainer = Color(0xFFB8D5F5)
)

val LightColors = lightColorScheme(
    primary = GoldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBEBC5),
    onPrimaryContainer = Color(0xFF4D3B0E),
    background = LightBg,
    onBackground = OnSurfaceLight,
    surface = LightSurface,
    onSurface = OnSurfaceLight,
    surfaceVariant = LightChip,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = LightBorder,
    outlineVariant = PremiumDividerLight,
    error = Color(0xFFDC2626),
    onError = Color.White,
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE9FB),
    onSecondaryContainer = Color(0xFF0E2A4F)
)

/** Tema de la app con manejo explícito de modo claro/oscuro y escala de fuente. */
@Composable
fun SjfTheme(
    darkTheme: Boolean = true,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colors,
            typography = appTypography(fontScale),
            content = content
        )
    }
}

@Composable
fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun semanticColor(c: Color): Color = if (isDark()) c else androidx.compose.ui.graphics.lerp(c, Color.Black, 0.45f)
