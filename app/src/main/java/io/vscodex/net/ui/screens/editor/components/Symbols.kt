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

package io.vscodex.net.ui.screens.editor.components

import android.view.View
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itsvks.monaco.MonacoEditor
import io.vscodex.net.core.settings.Settings.Editor.rememberSymbols
import io.vscodex.net.ui.extensions.blend
import io.vscodex.net.ui.screens.editor.components.view.CodeEditorView

@Composable
fun Symbols(
    editor: View,
    modifier: Modifier = Modifier
) {
    val symbol by rememberSymbols()
    val symbolItems = symbol.toList()

    LazyRow(
        modifier = modifier.heightIn(min = 50.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(symbolItems) { item ->
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(
                        onClick = {
                            if (editor is MonacoEditor) {
                                editor.dispatchKey(item.toString())
                            } else if (editor is CodeEditorView) {
                                val cursor = editor.editor.cursor
                                editor.editor.text.insert(
                                    cursor.rightLine,
                                    cursor.rightColumn,
                                    item.toString()
                                )
                            }
                        },
                        role = Role.Button
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.blend(
                            color = Color.Transparent,
                            fraction = 0.6f
                        ),
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.toString()
                )
            }
        }
    }
}
