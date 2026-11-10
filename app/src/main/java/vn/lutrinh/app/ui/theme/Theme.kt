package vn.lutrinh.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Topo outdoor" tokens (docs/08-design.md), taken from the huashu-design specimen
 * `assets/style-specimens/app/06-topo-outdoor.html` (MIT, © alchaincyf). Every colour of the app
 * lives here (and the basemap repaint in map/basemap); docs/08 §2 lists the specimen value each
 * token was taken from and §8 the contrast of every text pair. Light only (docs/08 §10).
 */
object Palette {
    /** Topo paper: the map and every screen background (specimen `.ph`/map rect `#EFEBDA`). */
    val Parchment = Color(0xFFEFEBDA)
    /** Cards, panels, sheets, dialogs, chips on the map (specimen `.pan`/`.card`/`.chip` `#FBFAF2`). */
    val Card = Color(0xFFFBFAF2)
    /** A card a step down (filled fields, pressed rows); not in the specimen, between Card and Parchment. */
    val CardDim = Color(0xFFF6F3E6)
    /** Thumbnail wells, image placeholders, tracks of bars (specimen `.mini` `#E8E3CE`). Ink text only. */
    val Sand = Color(0xFFE8E3CE)
    /** Grid and divider lines (specimen profile grid `#E3DEC8`). Decorative. */
    val Grid = Color(0xFFE3DEC8)
    /** The 2 dp solid ledge under a card (specimen `.card` `box-shadow: 0 2px 0 #D9D4BE`). Decorative. */
    val Ledge = Color(0xFFD9D4BE)

    /** Body text (specimen `body` `#2A2A22`). */
    val Ink = Color(0xFF2A2A22)
    /** Labels and secondary text (specimen `.lbl`/`.m` `#6B6A58`): on Parchment, CardDim and Card only. */
    val InkMuted = Color(0xFF6B6A58)
    /** Field borders and icons that must reach 3:1 (derived: InkMuted lightened; 3.24 on Parchment). */
    val Outline = Color(0xFF86826B)

    /** Forest: the dark chip / callout of the specimen (`#1F2A24`). Filled buttons, FAB, selection, end pin. */
    val Forest = Color(0xFF1F2A24)

    /** Signal orange: the route and the elevation line (specimen `#E4572E`). Lines and fills only, never text. */
    val Signal = Color(0xFFE4572E)
    /** Signal as text (links, accent numbers): darkened to 4.8:1 on Parchment (derived). */
    val SignalDeep = Color(0xFFB83A16)
    /** Signal at the specimen's 22-25 % fill over Card, flattened: chosen chips, accent wells. */
    val SignalWash = Color(0xFFFBE3D9)

    /** Trail green: the start pin (specimen start circle `#2D7A46`). */
    val Trail = Color(0xFF2D7A46)
    /** Waypoint blue: step pins and the my-location dot (specimen waypoint circle `#2B6CB0`). */
    val Waypoint = Color(0xFF2B6CB0)
    /** Contour brown: contour motifs and province borders (specimen contours `#C09A68`). Decorative. */
    val Contour = Color(0xFFC09A68)
    /** Woods and parks on the map (specimen `#D8E4C4`). */
    val Vegetation = Color(0xFFD8E4C4)
    /** Water on the map (specimen `#BFDDE6`). */
    val Water = Color(0xFFBFDDE6)

    /** Badges (specimen `.dif`): easy / moderate / hard pairs, background + text. */
    val EasyWash = Color(0xFFCFE8CF)
    val EasyInk = Color(0xFF1F5A2A)
    val ModerateWash = Color(0xFFFDE6B8)
    val ModerateInk = Color(0xFF7A4A00)
    val HardWash = Color(0xFFF6C9C0)
    val HardInk = Color(0xFF8A2410)

    /** Error (the M3 error red; a system meaning). Its container is the specimen's "hard" badge pair. */
    val Error = Color(0xFFB3261E)

    /**
     * "Bản đồ của tôi" (R14) needs 8 trip colours. Data colours, only on that map, from the
     * specimen's own inks (signal, waypoint, trail, forest, the badge inks) plus a contour brown,
     * a lake teal and a heather; each ≥ 3:1 on the map paper (docs/08 §8).
     */
    val TripColors = listOf(
        Signal,
        Waypoint,
        Trail,
        Forest,
        ModerateInk,
        Color(0xFF8C6A3C),
        Color(0xFF2F7480),
        Color(0xFF6E4E9A),
    )

    /** Recorded GPS tracks (R16): forest, "where I really went", never the planned-route orange. */
    val Track = Forest

    /** Map background while tiles load and behind the vector basemap. */
    val MapPaper = Parchment
    /** Map labels (5.9:1 on the map paper) and their halo. */
    val MapLabel = Color(0xFF5A5948)
    val MapHalo = Card
    /** Map credit text (R34), on a translucent white label. */
    val MapCredit = InkMuted
}

/** "#RRGGBB" of a token, for the map engine and bitmap canvases (map/, share/). */
fun Color.rgbHex(): String {
    val rgb = toArgb() and 0xFFFFFF
    return "#" + rgb.toString(16).uppercase().padStart(6, '0')
}

private val Colors = lightColorScheme(
    primary = Palette.Forest,
    onPrimary = Palette.Card,
    primaryContainer = Palette.Sand,
    onPrimaryContainer = Palette.Forest,
    inversePrimary = Palette.Sand,
    secondary = Palette.SignalDeep,
    onSecondary = Palette.Card,
    secondaryContainer = Palette.Sand,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Trail,
    onTertiary = Palette.Card,
    tertiaryContainer = Palette.EasyWash,
    onTertiaryContainer = Palette.EasyInk,
    background = Palette.Parchment,
    onBackground = Palette.Ink,
    surface = Palette.Parchment,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Sand,
    onSurfaceVariant = Palette.InkMuted,
    surfaceTint = Palette.Parchment,
    surfaceContainerLowest = Palette.Card,
    surfaceContainerLow = Palette.Card,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.Card,
    surfaceContainerHighest = Palette.CardDim,
    surfaceDim = Palette.Sand,
    surfaceBright = Palette.Card,
    inverseSurface = Palette.Forest,
    inverseOnSurface = Palette.Card,
    outline = Palette.Outline,
    outlineVariant = Palette.Grid,
    error = Palette.Error,
    onError = Palette.Card,
    errorContainer = Palette.HardWash,
    onErrorContainer = Palette.HardInk,
    scrim = Palette.Forest,
)

/**
 * The specimen's four radii: 8 badges, 12 chips/thumbnails/buttons/fields, 18 cards, 22 panels
 * (sheets, dialogs, floating map panels, callouts). Nothing is a full pill.
 */
object Radii {
    val Badge = 8.dp
    val Chip = 12.dp
    val Card = 18.dp
    val Panel = 22.dp
}

/**
 * Space scale (docs/08 §4, R39): every padding / gap in `ui/` is one of these (0 = none). Screen edge
 * and card padding are [L]; gap between cards is [M]; text-to-icon gap is [S]. `DesignGuardTest`
 * (`spacingOnTheSpaceScale`) fails on any other dp literal in padding / spacedBy / Spacer.
 */
object Space {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}

/** Icon sizes (docs/08 §5, R39): 18 in a button or chip, 22 in a bar / map button, 24 alone, 40 the mark. */
object IconSize {
    val S = 18.dp
    val M = 22.dp
    val L = 24.dp
    val Mark = 40.dp
}

/**
 * The specimen's two soft shadows, only for things that float over the map (docs/08 §4):
 * chips and map buttons (`0 4px 10px`), panels and the FAB (`0 8px 24px`). On paper, cards
 * have a [ledge] instead and nothing else casts a shadow.
 */
object Lift {
    val Chip = 4.dp
    val Panel = 8.dp
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.Chip),
    small = RoundedCornerShape(Radii.Chip),
    medium = RoundedCornerShape(Radii.Card),
    large = RoundedCornerShape(Radii.Panel),
    extraLarge = RoundedCornerShape(Radii.Panel),
)

/** A 1 dp grid line (dividers inside a card, map buttons on imagery). Not a card edge: cards have a [ledge]. */
val hairline: BorderStroke
    @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/**
 * The specimen's card edge (`box-shadow: 0 2px 0 #D9D4BE`): a solid 2 dp Ledge band under the
 * card (corner [radius]), no blur. Put it before `clip`/`background` in the chain.
 */
fun Modifier.ledge(radius: Dp = Radii.Card, depth: Dp = 2.dp): Modifier = drawBehind {
    val r = radius.toPx()
    drawRoundRect(Palette.Ledge, topLeft = Offset(0f, depth.toPx()), size = Size(size.width, size.height), cornerRadius = CornerRadius(r, r))
}

@Composable
fun LuTrinhTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, shapes = AppShapes, content = content)
}
