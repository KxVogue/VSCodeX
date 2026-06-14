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

package io.vscodex.net.ui.screens.editor.components.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.vscodex.net.core.components.Tooltip
import io.vscodex.net.resources.R

@Composable
fun Heading(
    modifier: Modifier = Modifier,
    title: String,
    isMaximized: Boolean = false,
    onToggleMaximize: () -> Unit = {},
    onCloseDrawerRequest: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .padding(5.dp)
                .padding(start = 5.dp, bottom = 0.dp)
                .weight(1f),
        ) {
            Text(
                text = stringResource(R.string.workspace),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // Maximize / Minimize toggle
        Tooltip(if (isMaximized) "Minimize workspace" else "Maximize workspace") {
            IconButton(
                onClick  = onToggleMaximize,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = if (isMaximized)
                        Icons.Rounded.CloseFullscreen
                    else
                        Icons.Rounded.OpenInFull,
                    contentDescription = if (isMaximized) "Minimize workspace" else "Maximize workspace",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Tooltip(stringResource(R.string.close_drawer)) {
            IconButton(
                onClick  = onCloseDrawerRequest,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Rounded.MenuOpen,
                    contentDescription = stringResource(R.string.close_drawer)
                )
            }
        }
    }
}
