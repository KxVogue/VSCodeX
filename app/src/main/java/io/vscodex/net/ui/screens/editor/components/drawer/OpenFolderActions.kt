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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.documentfile.provider.DocumentFile
import com.blankj.utilcode.util.UriUtils
import io.vscodex.net.PreferenceKeys
import io.vscodex.net.activities.Editor.LocalCommandPaletteManager
import io.vscodex.net.app.strings
import io.vscodex.net.extensions.toFile
import io.vscodex.net.file.DocumentFileWrapper
import io.vscodex.net.file.wrapFile
import io.vscodex.net.keyboard.model.Command.Companion.newCommand
import io.vscodex.net.preferences.defaultPrefs
import io.vscodex.net.resources.R
import io.vscodex.net.ui.screens.file.FileExplorerViewModel
import io.vscodex.net.utils.showShortToast
import java.io.File

@Composable
fun OpenFolderActions(
    modifier: Modifier = Modifier,
    fileExplorerViewModel: FileExplorerViewModel
) {
    val context = LocalContext.current
    val commandPaletteManager = LocalCommandPaletteManager.current

    // Track whether the launcher is safely registered and ready to use
    var isLauncherReady by remember { mutableStateOf(false) }

    val openFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) DocumentFile.fromTreeUri(context, uri)?.let {
            val file = if (DocumentFileWrapper.shouldWrap(uri)) {
                DocumentFileWrapper(it)
            } else {
                UriUtils.uri2File(it.uri).wrapFile()
            }
            fileExplorerViewModel.openFolder(file)
        }
    }

    // Mark launcher as ready after first successful composition
    SideEffect {
        isLauncherReady = true
    }

    // Clean up: mark not ready when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            isLauncherReady = false
        }
    }

    // Register command only once, guarded by isLauncherReady
    DisposableEffect(commandPaletteManager) {
        val command = newCommand("Open Folder", "Ctrl+Shift+O") {
            if (isLauncherReady) {
                openFolder.launch(null)
            }
        }
        commandPaletteManager.addCommand(command)
        onDispose {
            commandPaletteManager.removeCommand(command)
        }
    }

    var showRecentFoldersDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                if (isLauncherReady) openFolder.launch(null)
            },
            shape = MaterialTheme.shapes.medium
        ) {
            Text(text = stringResource(strings.open_folder))
        }

        Button(
            onClick = { showRecentFoldersDialog = true },
            shape = MaterialTheme.shapes.medium
        ) {
            Text(text = stringResource(strings.open_recent))
        }
    }

    if (showRecentFoldersDialog) {
        RecentFoldersDialog(
            onDismissRequest = { showRecentFoldersDialog = false },
            onOpenFolder = {
                val treeUri = DocumentFile.fromFile(it).uri
                fileExplorerViewModel.openFolder(UriUtils.uri2File(treeUri).wrapFile())
                showRecentFoldersDialog = false
            }
        )
    }
}

@Composable
fun RecentFoldersDialog(
    onDismissRequest: () -> Unit,
    onOpenFolder: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val recentFolders = listOfNotNull(
        defaultPrefs.getString(PreferenceKeys.RECENT_FOLDER_1, ""),
        defaultPrefs.getString(PreferenceKeys.RECENT_FOLDER_2, ""),
        defaultPrefs.getString(PreferenceKeys.RECENT_FOLDER_3, ""),
        defaultPrefs.getString(PreferenceKeys.RECENT_FOLDER_4, ""),
        defaultPrefs.getString(PreferenceKeys.RECENT_FOLDER_5, "")
    ).filter { it.isNotEmpty() }.distinct()

    SideEffect {
        if (recentFolders.isEmpty()) {
            showShortToast(context, context.getString(R.string.no_recent_folder_found))
            onDismissRequest()
        }
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(strings.open_recent)) },
        text = {
            LazyColumn {
                items(recentFolders) { folderPath ->
                    val folder = folderPath.toFile()

                    ListItem(
                        headlineContent = {
                            Text(text = folder.name)
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                onClick = { onOpenFolder(folder) },
                                role = Role.Button
                            ),
                        supportingContent = {
                            Text(
                                text = folder.absolutePath,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Sharp.Folder,
                                contentDescription = null
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = AlertDialogDefaults.containerColor,
                            headlineColor = AlertDialogDefaults.titleContentColor,
                            supportingColor = AlertDialogDefaults.textContentColor,
                            leadingIconColor = AlertDialogDefaults.iconContentColor
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(strings.cancel))
            }
        }
    )
}
