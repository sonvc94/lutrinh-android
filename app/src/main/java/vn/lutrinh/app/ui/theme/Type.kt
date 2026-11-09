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
 * Be Vietnam Pro (SIL OFL, licence in assets/licenses/) is bundled, never downloaded: the app is
 * local-only. It sets all text, titles and body alike (docs/08-design.md "Type").
 */
val TextFont = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

/**
 * Barlow Condensed (SIL OFL, licence in assets/licenses/) for numbers that stand alone (counts,
 * km, times): Be Vietnam Pro has no tabular figures, Barlow has `tnum`, and a condensed face reads
 * like a map readout. Never for words (its stacked Vietnamese marks get tight at small sizes).
 */
val NumeralFont = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
)

/** A number set as a readout: Barlow Condensed with tabular lining figures. */
fun TextStyle.numerals(): TextStyle = copy(fontFamily = NumeralFont, fontFeatureSettings = "tnum")

/**
 * One modular scale, ratio 1.2 from a 16 sp body: 12 · 14 · 16 · 19 · 23 · 28 · 33 · 40 sp
 * (13.3 is raised to the 14 sp body floor; nothing under 12 sp). Weights: 400 body, 500 labels,
 * 600 titles.
 */
private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = TextFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val AppTypography = Typography(
    displayLarge = style(40, 48, FontWeight.SemiBold, -0.01),
    displayMedium = style(33, 40, FontWeight.SemiBold, -0.01),
    displaySmall = style(28, 34, FontWeight.SemiBold),
    headlineLarge = style(33, 40, FontWeight.SemiBold, -0.01),
    headlineMedium = style(28, 34, FontWeight.SemiBold),
    headlineSmall = style(23, 30, FontWeight.SemiBold),
    titleLarge = style(23, 30, FontWeight.Medium),
    titleMedium = style(19, 26, FontWeight.SemiBold),
    titleSmall = style(16, 22, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal, 0.01),
    labelLarge = style(14, 20, FontWeight.Medium, 0.01),
    labelMedium = style(12, 16, FontWeight.Medium, 0.02),
    labelSmall = style(12, 16, FontWeight.Medium, 0.02),
)
