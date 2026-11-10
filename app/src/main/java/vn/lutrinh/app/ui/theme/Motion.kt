package vn.lutrinh.app.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Motion tokens (docs/08 §6a "Motion", R37). Camera lengths live in `map/CameraMath` (pure, shared
 * with iOS); these are the screen transitions. Compose scales them with the system animation
 * setting (0 = they end at once); the map camera checks [reducedMotion] itself.
 */
object Motion {
    /** Card on the trip map → step detail (and back): the shared bounds grow/shrink. */
    const val EXPAND_MS = 400

    /** The detail's content fades in once the bounds have started to grow. */
    const val CONTENT_FADE_MS = 200
    const val CONTENT_FADE_DELAY_MS = 150

    /** "Ngày N/M": the day number slides and fades to the next one. */
    const val DAY_FLIP_MS = 260

    /** Screen → screen (navigation, R39): the new screen fades in while sliding [SCREEN_SLIDE_DP] along the way in. */
    const val SCREEN_MS = 300
    const val SCREEN_SLIDE_DP = 24

    /** Cubic ease in-out: every move starts and ends softly (the map camera eases in and out too). */
    val EaseInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
}

/** System animations are off ("Animator duration scale" = 0, Remove animations): move without animating. */
fun reducedMotion(): Boolean = !ValueAnimator.areAnimatorsEnabled()
