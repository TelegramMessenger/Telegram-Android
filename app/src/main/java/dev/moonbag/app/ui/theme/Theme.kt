package dev.moonbag.app.ui.theme

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

// The app's own wallet look: near-black with blue accents. Used unless "dynamic colours" is switched on.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF0B2F66),
    secondary = Color(0xFF4F6384),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF4F6FA),
    surface = Color(0xFFF4F6FA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEEF1F7),
    surfaceContainer = Color(0xFFE8ECF4),
    surfaceContainerHigh = Color(0xFFE0E5EF),
    surfaceContainerHighest = Color(0xFFD7DDEA),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF00264D),
    primaryContainer = Color(0xFF18283D),
    onPrimaryContainer = Color(0xFF9CCBFF),
    secondary = Color(0xFF9DB4D6),
    secondaryContainer = Color(0xFF1F2A3A),
    onSecondaryContainer = Color(0xFFD3E3FF),
    tertiary = Color(0xFFFFB955),
    background = Color(0xFF0E0E0F),
    onBackground = Color(0xFFF1F1F3),
    surface = Color(0xFF0E0E0F),
    onSurface = Color(0xFFF1F1F3),
    surfaceVariant = Color(0xFF232326),
    onSurfaceVariant = Color(0xFF9A9AA3),
    surfaceContainerLowest = Color(0xFF080808),
    surfaceContainerLow = Color(0xFF151516),
    surfaceContainer = Color(0xFF1A1A1C),
    surfaceContainerHigh = Color(0xFF242427),
    surfaceContainerHighest = Color(0xFF2E2E32),
    outline = Color(0xFF5C5C66),
    outlineVariant = Color(0xFF2E2E34),
)

@Composable
fun MoonbagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You: colours follow the wallpaper on Android 12+.
    dynamicColor: Boolean = false,
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

/** Green and red for price moves. They stay fixed so "up" and "down" never depend on the wallpaper. */
object MarketColors {
    @Composable
    fun up(): Color = if (isSystemInDarkTheme()) Color(0xFF3DDC97) else Color(0xFF0B8F5A)

    @Composable
    fun down(): Color = if (isSystemInDarkTheme()) Color(0xFFFF6B7A) else Color(0xFFC62840)

    @Composable
    fun forChange(change: Double): Color = if (change >= 0) up() else down()
}
