package io.github.openquesttuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// Toujours sombre : plus confortable dans le casque, et cohérent avec l'interface d'Horizon OS.
private val OqtColors = darkColorScheme(
    primary = Color(0xFF7FD4FF),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF0B3D5C),
    onPrimaryContainer = Color(0xFFC6E7FF),
    secondary = Color(0xFFB6C9D8),
    onSecondary = Color(0xFF21323E),
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF4A2800),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E3E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE0E3E8),
    surfaceVariant = Color(0xFF1A2027),
    onSurfaceVariant = Color(0xFFC0C7CF),
    surfaceContainer = Color(0xFF1A2027),
    surfaceContainerHigh = Color(0xFF232A31),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val base = Typography()

// Corps de texte agrandi de 2sp : un panneau VR se lit de plus loin qu'un écran de téléphone.
private val OqtTypography = base.copy(
    bodyLarge = base.bodyLarge.bigger(),
    bodyMedium = base.bodyMedium.bigger(),
)

private fun TextStyle.bigger(): TextStyle = copy(fontSize = (fontSize.value + 2).sp)

@Composable
fun OqtTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OqtColors, typography = OqtTypography, content = content)
}
