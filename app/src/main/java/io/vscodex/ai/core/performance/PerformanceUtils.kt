/*
 * This file is part of VSCodeX.
 * Composition-stable helpers that prevent common Compose performance pitfalls.
 */
package io.vscodex.ai.core.performance

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max

@Stable class StableList<T>(val list: List<T>) : List<T> by list
@Stable class StableMap<K, V>(val map: Map<K, V>) : Map<K, V> by map

@Composable
fun <T> rememberStableList(vararg keys: Any?, factory: () -> List<T>): StableList<T> =
    remember(*keys) { StableList(factory()) }

/**
 * Returns a State<T> that only updates [delayMs] ms after [value] stops changing.
 * Use on TextField queries to avoid per-keystroke recompositions.
 */
@Composable
fun <T> deferredState(value: T, delayMs: Long = 150L): State<T> {
    val deferred = remember { mutableStateOf(value) }
    LaunchedEffect(value) { delay(delayMs); deferred.value = value }
    return deferred
}

/** Conditional modifier — no lambda allocation when condition is false. */
inline fun Modifier.thenIf(condition: Boolean, block: Modifier.() -> Modifier): Modifier =
    if (condition) this.block() else this

/**
 * Expands hit area to [minSize]×[minSize] without changing visual size.
 * Satisfies WCAG 2.5.5 (48dp minimum touch target).
 */
fun Modifier.minimumTouchTarget(minSize: Dp = 48.dp): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val minPx = minSize.roundToPx()
    val w = max(placeable.width, minPx)
    val h = max(placeable.height, minPx)
    layout(w, h) { placeable.placeRelative((w - placeable.width) / 2, (h - placeable.height) / 2) }
}
