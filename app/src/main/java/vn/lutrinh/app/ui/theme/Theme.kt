package vn.lutrinh.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * "Sổ bản đồ" (field atlas) tokens, docs/08-design.md. Every colour of the app lives here (and the
 * basemap repaint in map/basemap); the oklch value each was set from and the photo it was sampled
 * from are in docs/08. One accent only: chàm (Hmong indigo). Everything else is neutral.
 */
object Palette {
    /** Limestone paper: karst mist over Tam Sơn (Quản Bạ), oklch(0.975 0.003 220). */
    val Paper = Color(0xFFF5F7F8)
    /** Raised paper (cards, sheets), oklch(0.993 0.002 220). */
    val PaperRaised = Color(0xFFFBFDFE)
    /** Limestone ramp for containers, oklch(0.960 → 0.880, C ≤ 0.008, H 230). */
    val LimestoneLow = Color(0xFFEFF2F4)
    val Limestone = Color(0xFFE9EEF0)
    val LimestoneHigh = Color(0xFFE2E7EA)
    val LimestoneHighest = Color(0xFFDBE1E4)
    val LimestoneDim = Color(0xFFD2D9DC)

    /** Ink: the darkest chàm of the indigo hemp cloth, oklch(0.230 0.014 240). Text and GPS tracks. */
    val Ink = Color(0xFF171E23)
    /** Secondary text, oklch(0.450 0.014 240). */
    val InkMuted = Color(0xFF4F565C)
    /** Field borders and icons that need 3:1, oklch(0.600 0.012 240). */
    val Outline = Color(0xFF7A8187)
    /** Hairline dividers and card edges (decorative, not for text), oklch(0.870 0.008 235). */
    val Hairline = Color(0xFFCFD5D9)

    /** Chàm, the one accent: routes, primary actions, links, oklch(0.420 0.095 258). */
    val Cham = Color(0xFF2A4D80)
    /** Text on a chàm wash, oklch(0.300 0.080 258). */
    val ChamDeep = Color(0xFF112D55)
    /** A barely tinted paper for chosen/primary containers, oklch(0.930 0.018 258). */
    val ChamWash = Color(0xFFE1E9F4)
    /** Selected leg/pin on maps only (small area), oklch(0.520 0.160 262). */
    val ChamBright = Color(0xFF3263C3)

    /** Error, oklch(0.50 0.17 27) (the M3 error red; a system meaning, not decoration). */
    val Error = Color(0xFFB3261E)

    /**
     * "Bản đồ của tôi" (R14) needs 8 trip colours. Data colours, only on that map, sampled from the
     * same photos (docs/08): chàm, terraced rice (Du Già), ripe rice, Nho Quế jade, karst slate,
     * light chàm, earth of Mã Pí Lèng, young rice. Each ≥ 3:1 on the map paper.
     */
    val TripColors = listOf(
        Cham,
        Color(0xFF405F39),
        Color(0xFFA47726),
        Color(0xFF307672),
        Color(0xFF637883),
        Color(0xFF5D7AB0),
        Color(0xFF79543F),
        Color(0xFF608A57),
    )

    /** Recorded GPS tracks (R16): ink, "where I really went", never a planned-route colour. */
    val Track = Ink

    /** Map background while tiles load and behind the vector basemap, oklch(0.955 0.005 220). */
    val MapPaper = Color(0xFFEDF1F2)
    /** Map credit text (R34), on a translucent white label. */
    val MapCredit = InkMuted
}

/** "#RRGGBB" of a token, for the map engine and bitmap canvases (map/, share/). */
fun Color.rgbHex(): String {
    val rgb = toArgb() and 0xFFFFFF
    return "#" + rgb.toString(16).uppercase().padStart(6, '0')
}

private val Colors = lightColorScheme(
    primary = Palette.Cham,
    onPrimary = Palette.PaperRaised,
    primaryContainer = Palette.ChamWash,
    onPrimaryContainer = Palette.ChamDeep,
    inversePrimary = Palette.ChamWash,
    secondary = Palette.Ink,
    onSecondary = Palette.PaperRaised,
    secondaryContainer = Palette.LimestoneHigh,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Ink,
    onTertiary = Palette.PaperRaised,
    tertiaryContainer = Palette.LimestoneHigh,
    onTertiaryContainer = Palette.Ink,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.LimestoneHigh,
    onSurfaceVariant = Palette.InkMuted,
    surfaceTint = Palette.Paper,
    surfaceContainerLowest = Palette.PaperRaised,
    surfaceContainerLow = Palette.LimestoneLow,
    surfaceContainer = Palette.Limestone,
    surfaceContainerHigh = Palette.LimestoneHigh,
    surfaceContainerHighest = Palette.LimestoneHighest,
    surfaceDim = Palette.LimestoneDim,
    surfaceBright = Palette.PaperRaised,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Paper,
    outline = Palette.Outline,
    outlineVariant = Palette.Hairline,
    error = Palette.Error,
    onError = Palette.PaperRaised,
)

/** Two radii only: 4 dp for small things (chips, thumbnails, badges), 12 dp for containers. */
object Radii {
    val Small = 4.dp
    val Large = 12.dp
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.Small),
    small = RoundedCornerShape(Radii.Small),
    medium = RoundedCornerShape(Radii.Large),
    large = RoundedCornerShape(Radii.Large),
    extraLarge = RoundedCornerShape(Radii.Large),
)

/** Containers are separated by a hairline, not a shadow (docs/08). */
val hairline: BorderStroke
    @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

@Composable
fun LuTrinhTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, shapes = AppShapes, content = content)
}
