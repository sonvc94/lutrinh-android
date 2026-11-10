package vn.lutrinh.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import vn.lutrinh.app.ui.theme.Motion

/** The app's shared-transition scope (AppNavigation wraps the NavHost in it), or null in previews/tests. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The navigation entry's enter/exit scope (set per destination that takes part), or null. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * R37: the trip-map card of step [stepId] and the step detail screen share their bounds: opening
 * a card grows it into the screen (Motion.EXPAND_MS, ease in-out), the detail's content fades in
 * after Motion.CONTENT_FADE_DELAY_MS; Back shrinks it into the card again. [shape] clips the
 * moving bounds. No-op outside a shared-transition scope.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.stepSharedBounds(stepId: String, shape: Shape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val nav = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@stepSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState("step-card-$stepId"),
            animatedVisibilityScope = nav,
            enter = fadeIn(tween(Motion.CONTENT_FADE_MS, Motion.CONTENT_FADE_DELAY_MS, Motion.EaseInOut)),
            exit = fadeOut(tween(Motion.CONTENT_FADE_MS, easing = Motion.EaseInOut)),
            boundsTransform = { _, _ -> tween(Motion.EXPAND_MS, easing = Motion.EaseInOut) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/**
 * True while this navigation entry is entering or leaving. A live map (a GL surface) cannot be
 * drawn inside the moving shared bounds and shows as a dark box, so screens show a plain
 * placeholder instead of their map until this is false (R37).
 */
@Composable
fun navTransitionRunning(): Boolean {
    val transition = LocalNavAnimatedScope.current?.transition ?: return false
    return transition.currentState != transition.targetState || transition.targetState != EnterExitState.Visible
}
