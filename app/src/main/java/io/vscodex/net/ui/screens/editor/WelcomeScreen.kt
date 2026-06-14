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

package io.vscodex.net.ui.screens.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vscodex.net.providers.FileIconProvider
import io.vscodex.net.resources.R
import io.vscodex.net.ui.extensions.harmonizeWithPrimary
import java.io.File

@Composable
fun WelcomeScreen(
    onOpenFile: () -> Unit,
    onNewFile: () -> Unit,
    onOpenFolder: () -> Unit,
    recentFiles: List<File> = emptyList(),
    onOpenRecentFile: (File) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.background.harmonizeWithPrimary(0.05f)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.harmonizeWithPrimary(0.1f)
            )
        ) {
            WelcomeScreenContent(
                onOpenFile = onOpenFile,
                onNewFile = onNewFile,
                onOpenFolder = onOpenFolder,
                recentFiles = recentFiles,
                onOpenRecentFile = onOpenRecentFile
            )
        }
    }
}

@Composable
private fun WelcomeScreenContent(
    onOpenFile: () -> Unit,
    onNewFile: () -> Unit,
    onOpenFolder: () -> Unit,
    recentFiles: List<File> = emptyList(),
    onOpenRecentFile: (File) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_adaptive_fore),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 12.dp)
        )

        Text(
            text = "Welcome to VSCodeX",
            color = MaterialTheme.colorScheme.onSurface.harmonizeWithPrimary(fraction = 0.4f),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Open a file or folder to start coding",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WelcomeScreenButton(
                text = "Open File",
                onClick = onOpenFile
            )

            Spacer(modifier = Modifier.width(16.dp))

            WelcomeScreenButton(
                text = "New File",
                onClick = onNewFile
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenFolder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Open Folder")
        }

        // ── Recent files ──────────────────────────────────────────────────
        if (recentFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Recent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
            recentFiles.take(5).forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenRecentFile(file) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(
                            FileIconProvider.findFileIconResource(file.extension.removePrefix("."))
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = file.parent?.let {
                                // Shorten long paths: show only last 2 segments
                                val parts = it.split("/").filter { p -> p.isNotEmpty() }
                                if (parts.size > 2) "…/${parts.takeLast(2).joinToString("/")}"
                                else it
                            } ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.WelcomeScreenButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.weight(1f)
    ) {
        Text(text = text)
    }
}
