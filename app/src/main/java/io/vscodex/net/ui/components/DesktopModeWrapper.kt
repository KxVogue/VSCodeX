/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * VSCodeX is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with VSCodeX.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package io.vscodex.net.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.vscodex.net.core.settings.Settings.General.rememberDesktopMode

/**
 * Wraps [content] in a scaled-down density when Desktop Mode is enabled,
 * making the UI render more content like a desktop viewport.
 * Only affects the composables inside — the rest of the app is untouched.
 *
 * Scale factor 0.6 matches roughly what Chrome's "Request Desktop Site" does
 * on a standard phone screen (~420dp → ~700dp effective width).
 */
@Composable
fun DesktopModeWrapper(content: @Composable () -> Unit) {
    val desktopModeEnabled by rememberDesktopMode()
    val currentDensity = LocalDensity.current

    val density = if (desktopModeEnabled) {
        Density(
            density    = currentDensity.density * 0.6f,
            fontScale  = currentDensity.fontScale * 0.6f
        )
    } else {
        currentDensity
    }

    CompositionLocalProvider(LocalDensity provides density) {
        content()
    }
}
