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

package io.vscodex.net.compose.ui

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vscodex.net.compose.ui.graphics.rememberSvgAssetImageBitmap
import io.vscodex.net.core.FileIcons
import io.vscodex.net.ui.screens.editor.EditorViewModel
import kiwi.orbit.compose.icons.Icons
import kiwi.orbit.compose.ui.controls.Icon
import kiwi.orbit.compose.ui.controls.Tab
import kiwi.orbit.compose.ui.controls.Text

@SuppressLint("MaterialDesignInsteadOrbitDesign")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTab(
    files: List<EditorViewModel.OpenedFile>,
    selectedFileIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onTabReselected: (Int) -> Unit = {},
    onCloseOthers: (Int) -> Unit = {},
    onCloseAll: () -> Unit = {}
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceColor)
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedFileIndex,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp,
            divider = {},
            containerColor = surfaceColor,
            contentColor = onSurfaceColor,
            indicator = {}
        ) {
            files.forEachIndexed { index, file ->
                var expanded by remember { mutableStateOf(false) }
                val isSelected = index == selectedFileIndex

                val tabBg by animateColorAsState(
                    targetValue = if (isSelected) surfaceContainerColor else Color.Transparent,
                    animationSpec = tween(150),
                    label = "tab_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) onSurfaceColor else onSurfaceVariantColor,
                    animationSpec = tween(150),
                    label = "tab_text"
                )

                val svgIconPath = FileIcons.getSvgIconForFile(file.file.path)
                val isDefaultIcon = svgIconPath == "files/icons/file.svg"

                Tab(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            expanded = true
                            onTabReselected(index)
                        } else {
                            onTabSelected(index)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(tabBg)
                            .padding(horizontal = 12.dp)
                    ) {
                        // Active tab bottom border indicator
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(primaryColor)
                            )
                        }

                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // File type icon
                            if (isDefaultIcon) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Image(
                                    bitmap = rememberSvgAssetImageBitmap(svgIconPath),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            // File name
                            Text(
                                text = file.file.name,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Close button (always visible, adequate touch target).
                            // Files with unsaved changes show a small dot badge.
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onTabClose(index) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Close,
                                    contentDescription = "Close",
                                    tint = textColor,
                                    modifier = Modifier.size(12.dp)
                                )

                                if (file.isModified) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(primaryColor)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Close") },
                                onClick = {
                                    expanded = false
                                    onTabClose(index)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close Others") },
                                onClick = {
                                    expanded = false
                                    onCloseOthers(index)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close All") },
                                onClick = {
                                    expanded = false
                                    onCloseAll()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom border for the whole tab row
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}
