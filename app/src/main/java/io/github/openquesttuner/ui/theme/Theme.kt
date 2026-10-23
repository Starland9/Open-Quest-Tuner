package io.github.openquesttuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Toujours sombre : plus confortable dans le casque, et cohérent avec l'interface d'Horizon OS.
private val OqtColors = darkColorScheme(
    primary = Color(0xFF4285FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B3D5C),
    onPrimaryContainer = Color(0xFFC6E7FF),
    secondary = Color(0xFFAA82FF),
    onSecondary = Color(0xFF21323E),
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF4A2800),
    background = Color(0xFF0B1520),
    onBackground = Color(0xFFE0E3E8),
    surface = Color(0xFF0B1220),
    onSurface = Color(0xFFE0E3E8),
    surfaceVariant = Color(0xFF141B23),
    onSurfaceVariant = Color(0xFFC0C7CF),
    surfaceContainer = Color(0xFF101C29),
    surfaceContainerHigh = Color(0xFF1C2631),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val base = Typography()

// Compact desktop/Quest typography: hierarchy without phone-sized oversized controls.
private val OqtTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontSize = 32.sp, lineHeight = 38.sp),
    displayMedium = base.displayMedium.copy(fontSize = 28.sp, lineHeight = 34.sp),
    displaySmall = base.displaySmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
    headlineLarge = base.headlineLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium = base.headlineMedium.copy(fontSize = 21.sp, lineHeight = 26.sp),
    headlineSmall = base.headlineSmall.copy(fontSize = 18.sp, lineHeight = 22.sp),
    titleLarge = base.titleLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
    titleMedium = base.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
    titleSmall = base.titleSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
    bodySmall = base.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
    labelLarge = base.labelLarge.copy(fontSize = 11.sp, lineHeight = 14.sp),
    labelMedium = base.labelMedium.copy(fontSize = 10.sp, lineHeight = 12.sp),
    labelSmall = base.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OqtTheme(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 40.dp) {
    MaterialTheme(colorScheme = OqtColors, typography = OqtTypography, shapes = Shapes(small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(10.dp)), content = content)
    }
}
