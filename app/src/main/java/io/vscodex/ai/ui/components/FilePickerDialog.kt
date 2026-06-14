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

package io.vscodex.ai.ui.components

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * A fully in-app file picker dialog that navigates the real filesystem.
 * This avoids the Android SAF "Open with" app-chooser dialog entirely.
 *
 * @param onFilePicked  Called with the chosen [File] when the user taps a file.
 * @param onDismiss     Called when the dialog is dismissed without a selection.
 * @param initialDir    Starting directory. Defaults to external storage root.
 */
@Composable
fun FilePickerDialog(
    onFilePicked: (File) -> Unit,
    onDismiss: () -> Unit,
    initialDir: File = Environment.getExternalStorageDirectory()
) {
    // Current directory being displayed
    var currentDir by remember { mutableStateOf(resolveStart(initialDir)) }

    // Sorted children: directories first, then files; hidden entries excluded
    val children = remember(currentDir) {
        (currentDir.listFiles() ?: emptyArray())
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Back button – navigate to parent directory
                    val canGoUp = currentDir.parentFile != null &&
                            currentDir.absolutePath != Environment.getExternalStorageDirectory().absolutePath

                    IconButton(
                        onClick = {
                            if (canGoUp) currentDir = currentDir.parentFile!!
                        },
                        enabled = canGoUp,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Go up",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    Text(
                        text = currentDir.name.ifEmpty { "Storage" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Breadcrumb path hint
                Text(
                    text = currentDir.absolutePath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }
        },
        text = {
            if (children.isEmpty()) {
                Text(
                    text = "This folder is empty.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn {
                    items(children, key = { it.absolutePath }) { entry ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = entry.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (entry.isDirectory) FontWeight.Medium
                                                 else FontWeight.Normal
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = if (entry.isDirectory)
                                        Icons.Rounded.Folder
                                    else
                                        Icons.AutoMirrored.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (entry.isDirectory)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.isDirectory) {
                                        currentDir = entry
                                    } else {
                                        onFilePicked(entry)
                                    }
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Resolves the best starting directory:
 * - Uses [dir] if it exists and is a directory.
 * - Falls back to external storage root, then internal storage.
 */
private fun resolveStart(dir: File): File {
    if (dir.exists() && dir.isDirectory) return dir
    val ext = Environment.getExternalStorageDirectory()
    if (ext.exists()) return ext
    return File("/storage/emulated/0").takeIf { it.exists() } ?: File("/sdcard")
}
