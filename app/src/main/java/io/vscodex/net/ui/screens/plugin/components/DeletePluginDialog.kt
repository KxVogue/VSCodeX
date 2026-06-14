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

package io.vscodex.net.ui.screens.plugin.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.vscodex.net.resources.R
import io.vscodex.net.resources.R.string

@Composable
fun DeletePluginDialog(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(string.delete_plugin)) },
        text = { Text(stringResource(string.delete_plugin_msg)) },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(stringResource(string.confirm)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(string.cancel)) }
        }
    )
}