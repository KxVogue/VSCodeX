/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package io.vscodex.ai.ui.screens.editor.components.drawer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import io.vscodex.ai.activities.Editor.LocalEditorDrawerNavController
import io.vscodex.ai.activities.SettingsActivity
import io.vscodex.ai.activities.TerminalActivity
import io.vscodex.ai.app.drawables
import io.vscodex.ai.extensions.open
import io.vscodex.ai.resources.R
import io.vscodex.ai.ui.navigateSingleTop
import io.vscodex.ai.ui.screens.EditorDrawerScreens

@Composable
fun NavRail(
    modifier: Modifier = Modifier,
    selectedItemIndex: Int
) {
    data class NavItem(
        val label: String,
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector
    )

    val context = LocalContext.current
    val navController = LocalEditorDrawerNavController.current

    val items = listOf(
        NavItem(stringResource(R.string.files),    Icons.Rounded.Folder,      Icons.Outlined.Folder),
        NavItem(stringResource(R.string.git),      ImageVector.vectorResource(drawables.ic_git),
                                                    ImageVector.vectorResource(drawables.ic_git)),
        NavItem("AI",                              Icons.Rounded.AutoAwesome,  Icons.Outlined.AutoAwesome),
        NavItem(stringResource(R.string.terminal), Icons.Rounded.Terminal,    Icons.Outlined.Terminal),
        NavItem(stringResource(R.string.settings), Icons.Rounded.Settings,    Icons.Outlined.Settings),
    )

    NavigationRail(
        modifier = modifier.widthIn(max = 64.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Spacer(Modifier.height(8.dp))

        items.fastForEachIndexed { index, item ->
            val selected = selectedItemIndex == index

            val indicatorColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                              else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "nav_indicator_$index"
            )
            val iconTint by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "nav_tint_$index"
            )

            NavigationRailItem(
                icon = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(indicatorColor)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            modifier = Modifier.size(20.dp),
                            tint = iconTint
                        )
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                alwaysShowLabel = true,
                selected = selected,
                // Hide the default indicator — we draw our own above
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                ),
                onClick = {
                    when (index) {
                        0 -> navController.navigateSingleTop(EditorDrawerScreens.FileExplorer)
                        1 -> navController.navigateSingleTop(EditorDrawerScreens.GitManager)
                        2 -> navController.navigateSingleTop(EditorDrawerScreens.AiAgent)
                        3 -> context.open(TerminalActivity::class.java)
                        4 -> context.open(SettingsActivity::class.java)
                    }
                }
            )
        }
    }
}
