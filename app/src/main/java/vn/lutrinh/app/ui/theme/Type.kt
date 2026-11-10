package vn.lutrinh.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import vn.lutrinh.app.R

/**
 * Noto Sans (SIL OFL, licence in assets/licenses/) for every word, bundled, never downloaded (the
 * app is local-only). The specimen sets text in Noto Sans SC; its Latin + Vietnamese member is Noto
 * Sans, subset to Latin/Vietnamese (docs/08 §3). Weights as in the specimen: 400, 500, 700.
 */
val TextFont = FontFamily(
    Font(R.font.noto_sans_regular, FontWeight.Normal),
    Font(R.font.noto_sans_medium, FontWeight.Medium),
    Font(R.font.noto_sans_bold, FontWeight.Bold),
)

/**
 * Barlow Condensed (SIL OFL, licence in assets/licenses/) for numbers that stand alone (km, times,
 * counts, the specimen's `.n` readouts): tabular lining figures, condensed like a GPS readout.
 * Never for words (its stacked Vietnamese marks get tight at small sizes).
 */
val NumeralFont = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

/** A number set as a readout (specimen `.n`): Barlow Condensed 700, tabular figures, +0.01 em. */
fun TextStyle.numerals(): TextStyle =
    copy(fontFamily = NumeralFont, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", letterSpacing = 0.01.em)

/**
 * The specimen's scale (px at 360 px width = dp): 40 readout · 28 screen title · 22 sheet and dialog title (titleLarge = headlineSmall) ·
 * 17 card title / callout · 15 status · 14 meta and chips · 13 labels (700, +0.06 em) · 12 floor.
 * Weights: 400 body, 500 fields, 700 titles, labels and chips (the specimen has no 600).
 */
private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = TextFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val AppTypography = Typography(
    displayLarge = style(40, 40, FontWeight.Bold),
    displayMedium = style(34, 40, FontWeight.Bold),
    displaySmall = style(30, 36, FontWeight.Bold),
    headlineLarge = style(32, 40, FontWeight.Bold),
    headlineMedium = style(28, 36, FontWeight.Bold),
    headlineSmall = style(22, 30, FontWeight.Bold), // dialog title: M3 AlertDialog reads this slot (R38),
    titleLarge = style(22, 30, FontWeight.Bold),
    titleMedium = style(17, 24, FontWeight.Bold),
    titleSmall = style(15, 22, FontWeight.Bold),
    bodyLarge = style(17, 26, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal),
    bodySmall = style(13, 19, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Bold),
    labelMedium = style(13, 18, FontWeight.Bold, 0.06),
    labelSmall = style(12, 16, FontWeight.Bold, 0.04),
)
