package vn.lutrinh.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import vn.lutrinh.app.R

/**
 * Be Vietnam Pro (SIL OFL, licence in assets/licenses/) is bundled, never downloaded: the app is
 * local-only. Used for titles only (display, headline, title styles); body and label styles stay
 * on the system font.
 */
val TitleFont = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

private val Base = Typography()

val AppTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = TitleFont),
    displayMedium = Base.displayMedium.copy(fontFamily = TitleFont),
    displaySmall = Base.displaySmall.copy(fontFamily = TitleFont),
    headlineLarge = Base.headlineLarge.copy(fontFamily = TitleFont),
    headlineMedium = Base.headlineMedium.copy(fontFamily = TitleFont),
    headlineSmall = Base.headlineSmall.copy(fontFamily = TitleFont),
    titleLarge = Base.titleLarge.copy(fontFamily = TitleFont),
    titleMedium = Base.titleMedium.copy(fontFamily = TitleFont),
    titleSmall = Base.titleSmall.copy(fontFamily = TitleFont),
)
