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

package io.vscodex.ai.ui.screens.editor.components.drawer

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val navigationRailItems = listOf(
        stringResource(R.string.files),
        stringResource(R.string.git),
        "AI",
        stringResource(R.string.terminal),
        stringResource(R.string.settings)
    )
    val navRailItemIconsUnselected = listOf(
        Icons.Outlined.Folder,
        ImageVector.vectorResource(drawables.ic_git),
        Icons.Outlined.AutoAwesome,
        Icons.Outlined.Terminal,
        Icons.Outlined.Settings
    )
    val navRailItemIconsSelected = listOf(
        Icons.Rounded.Folder,
        ImageVector.vectorResource(drawables.ic_git),
        Icons.Rounded.AutoAwesome,
        Icons.Rounded.Terminal,
        Icons.Rounded.Settings
    )

    val context = LocalContext.current
    val navController = LocalEditorDrawerNavController.current

    NavigationRail(
        modifier = modifier.widthIn(max = 60.dp)
    ) {
        navigationRailItems.fastForEachIndexed { index, name ->
            NavigationRailItem(
                icon = {
                    Icon(
                        imageVector = if (selectedItemIndex == index) navRailItemIconsSelected[index] else navRailItemIconsUnselected[index],
                        contentDescription = name,
                        modifier = Modifier.size(20.dp),
                    )
                },
                label = {
                    Text(
                        text = name,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                alwaysShowLabel = true,
                selected = selectedItemIndex == index,
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
