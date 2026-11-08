package vn.lutrinh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Own palette: terrace green (Hà Giang), limestone grey (Ninh Bình karst), ripe-rice gold, clay. */
object Palette {
    val TerraceGreen = Color(0xFF1F5C45)
    val TerraceGreenLight = Color(0xFFCDE8D9)
    val Clay = Color(0xFFB85C2E)
    val ClayLight = Color(0xFFFFDBCB)
    val RiceGold = Color(0xFFE0A526)
    val RiceGoldLight = Color(0xFFFFE7B0)
    val Cream = Color(0xFFFBF8F2)
    val Limestone = Color(0xFFE9E6DE)
    val Ink = Color(0xFF1C2421)
}

private val Colors = lightColorScheme(
    primary = Palette.TerraceGreen,
    onPrimary = Color.White,
    primaryContainer = Palette.TerraceGreenLight,
    onPrimaryContainer = Color(0xFF0B2E21),
    secondary = Palette.Clay,
    onSecondary = Color.White,
    secondaryContainer = Palette.ClayLight,
    onSecondaryContainer = Color(0xFF3A1606),
    tertiary = Palette.RiceGold,
    onTertiary = Color(0xFF2E2100),
    tertiaryContainer = Palette.RiceGoldLight,
    onTertiaryContainer = Color(0xFF261A00),
    background = Palette.Cream,
    onBackground = Palette.Ink,
    surface = Palette.Cream,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Limestone,
    onSurfaceVariant = Color(0xFF4A524E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F2EA),
    surfaceContainer = Color(0xFFF1EDE4),
    surfaceContainerHigh = Color(0xFFEBE7DE),
    outline = Color(0xFF7A837E),
    outlineVariant = Color(0xFFC9CCC6),
    error = Color(0xFFB3261E),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun LuTrinhTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, shapes = AppShapes, content = content)
}
