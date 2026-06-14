/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package io.vscodex.ai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.vscodex.ai.app.noLocalProvidedFor
import io.vscodex.ai.ui.extensions.harmonizeWithPrimary
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.math.min

val LocalToastHostState =
    compositionLocalOf<ToastHostState> { noLocalProvidedFor("LocalToastHostState") }

@Composable
fun rememberToastHostState() = remember { ToastHostState() }

@Composable
fun ToastHost(
    modifier: Modifier = Modifier,
    hostState: ToastHostState = LocalToastHostState.current,
    alignment: Alignment = Alignment.BottomCenter,
    toast: @Composable (ToastData) -> Unit = { Toast(it) }
) {
    val currentToastData = hostState.currentToastData
    val accessibilityManager = LocalAccessibilityManager.current
    LaunchedEffect(currentToastData) {
        if (currentToastData != null) {
            val duration = currentToastData.visuals.duration.toMillis(accessibilityManager)
            delay(duration)
            currentToastData.dismiss()
        }
    }

    AnimatedContent(
        targetState = currentToastData,
        transitionSpec = { ToastDefaults.transition },
        label = "toast_host"
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.align(alignment)) {
                it?.let { toast(it) }
            }
        }
    }
}

@Composable
fun Toast(
    toastData: ToastData,
    modifier: Modifier = Modifier,
    shape: Shape = ToastDefaults.shape,
    containerColor: Color = ToastDefaults.color,
    contentColor: Color = ToastDefaults.contentColor,
) {
    val configuration = LocalConfiguration.current
    val sizeMin = min(configuration.screenWidthDp, configuration.screenHeightDp).dp

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor   = contentColor
        ),
        modifier = if (modifier != Modifier) modifier else
            Modifier
                .heightIn(min = 44.dp)
                .widthIn(min = 0.dp, max = sizeMin * 0.72f)
                .padding(
                    bottom = sizeMin * 0.19f,
                    top    = 24.dp,
                    start  = 16.dp,
                    end    = 16.dp
                )
                .imePadding()
                .systemBarsPadding()
                .zIndex(10000f),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            toastData.visuals.icon?.let {
                Icon(it, null, tint = contentColor, modifier = Modifier.size(16.dp))
            }
            Text(
                style     = MaterialTheme.typography.bodySmall,
                text      = toastData.visuals.message,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Stable
class ToastHostState {
    private val mutex = Mutex()

    var currentToastData by mutableStateOf<ToastData?>(null)
        private set

    @OptIn(ExperimentalMaterial3Api::class)
    suspend fun showToast(
        message: String,
        icon: ImageVector? = null,
        duration: ToastDuration = ToastDuration.Short
    ) = showToast(ToastVisualsImpl(message, icon, duration))

    @ExperimentalMaterial3Api
    suspend fun showToast(visuals: ToastVisuals) = mutex.withLock {
        try {
            suspendCancellableCoroutine { continuation ->
                currentToastData = ToastDataImpl(visuals, continuation)
            }
        } finally {
            currentToastData = null
        }
    }

    private class ToastVisualsImpl(
        override val message: String,
        override val icon: ImageVector? = null,
        override val duration: ToastDuration
    ) : ToastVisuals {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as ToastVisualsImpl
            return message == other.message && icon == other.icon && duration == other.duration
        }

        override fun hashCode(): Int {
            var result = message.hashCode()
            result = 31 * result + icon.hashCode()
            result = 31 * result + duration.hashCode()
            return result
        }
    }

    private class ToastDataImpl(
        override val visuals: ToastVisuals,
        private val continuation: CancellableContinuation<Unit>
    ) : ToastData {
        override fun dismiss() {
            if (continuation.isActive) continuation.resume(Unit)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as ToastDataImpl
            return visuals == other.visuals && continuation == other.continuation
        }

        override fun hashCode(): Int {
            var result = visuals.hashCode()
            result = 31 * result + continuation.hashCode()
            return result
        }
    }
}

@Stable
interface ToastData {
    val visuals: ToastVisuals
    fun dismiss()
}

@Stable
interface ToastVisuals {
    val message: String
    val icon: ImageVector?
    val duration: ToastDuration
}

enum class ToastDuration { Short, Long }

object ToastDefaults {
    val transition: ContentTransform
        get() = (
            fadeIn(tween(200)) +
            scaleIn(tween(300), transformOrigin = TransformOrigin(0.5f, 1f)) +
            slideInVertically(tween(300)) { it / 3 }
        ).togetherWith(
            fadeOut(tween(180)) +
            slideOutVertically(tween(280)) { it / 3 } +
            scaleOut(tween(420), transformOrigin = TransformOrigin(0.5f, 1f))
        )

    val contentColor: Color
        @Composable get() = MaterialTheme.colorScheme.inverseOnSurface.harmonizeWithPrimary()
    val color: Color
        @Composable get() = MaterialTheme.colorScheme.inverseSurface.harmonizeWithPrimary()
    val shape: Shape
        @Composable get() = MaterialTheme.shapes.extraLarge
}

private fun ToastDuration.toMillis(accessibilityManager: AccessibilityManager?): Long {
    val original = when (this) {
        ToastDuration.Long  -> 6500L
        ToastDuration.Short -> 3000L
    }
    return accessibilityManager?.calculateRecommendedTimeoutMillis(
        original,
        containsIcons = false,
        containsText  = true
    ) ?: original
}
