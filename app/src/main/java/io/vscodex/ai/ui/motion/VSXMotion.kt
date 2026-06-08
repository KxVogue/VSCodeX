/*
 * This file is part of VSCodeX.
 * Centralised animation specs — all transitions reference constants from here.
 * Durations: Micro 50ms · Short 100ms · Medium 200ms · Long 300ms
 */
package io.vscodex.ai.ui.motion

import androidx.compose.animation.*
import androidx.compose.animation.core.*

object VSXMotion {
    val springMicro  = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    val springFast   = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val springMedium = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)

    val springFastColor = spring<androidx.compose.ui.graphics.Color>(
        dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    val tweenShort  = tween<Float>(durationMillis = 100, easing = FastOutSlowInEasing)
    val tweenMedium = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    val tweenLong   = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

    val drawerEnter: EnterTransition =
        slideInHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(200))
    val drawerExit: ExitTransition =
        slideOutHorizontally(animationSpec = tween(150, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(150))

    val contentEnter: EnterTransition = fadeIn(animationSpec = tween(150))
    val contentExit:  ExitTransition  = fadeOut(animationSpec = tween(100))
}
