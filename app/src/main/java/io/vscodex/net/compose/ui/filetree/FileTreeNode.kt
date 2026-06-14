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

package io.vscodex.net.compose.ui.filetree

import android.annotation.SuppressLint
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import io.vscodex.net.file.File
import io.vscodex.net.file.extension
import io.vscodex.net.ui.icon.LanguageCpp
import io.vscodex.net.ui.icons.Icons
import io.vscodex.net.ui.icons.LanguageC
import io.vscodex.net.ui.icons.LanguageCsharp
import io.vscodex.net.ui.icons.LanguageCss3
import io.vscodex.net.ui.icons.LanguageGo
import io.vscodex.net.ui.icons.LanguageHtml5
import io.vscodex.net.ui.icons.LanguageJava
import io.vscodex.net.ui.icons.LanguageJavascript
import io.vscodex.net.ui.icons.LanguageKotlin
import io.vscodex.net.ui.icons.LanguageLua
import io.vscodex.net.ui.icons.LanguageMarkdown
import io.vscodex.net.ui.icons.LanguagePhp
import io.vscodex.net.ui.icons.LanguagePython
import io.vscodex.net.ui.icons.LanguageRust
import io.vscodex.net.ui.icons.LanguageSwift
import io.vscodex.net.ui.icons.LanguageTypescript
import io.vscodex.net.ui.icons.LanguageXml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Data class representing a file or directory in the file tree
 */
data class FileTreeNode(
    val file: File,
    val isLoading: Boolean = false,
    val children: Flow<FileTreeNode> = emptyFlow()
)

fun createFileTreeFromPath(file: File): FileTreeNode {
    return FileTreeNode(
        file = file,
        children = if (file.isDirectory) {
            flow {
                val files = withContext(Dispatchers.IO) {
                    file.listFiles()?.toList().orEmpty()
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

                files.forEach { emit(createFileTreeFromPath(it)) }
            }.flowOn(Dispatchers.IO)
        } else {
            emptyFlow()
        }
    )
}

@SuppressLint("MaterialDesignInsteadOrbitDesign")
fun getIconForFile(node: FileTreeNode): ImageVector {
    return when (node.file.extension) {
        "c" -> Icons.LanguageC
        "cpp" -> Icons.LanguageCpp
        "cs" -> Icons.LanguageCsharp
        "css" -> Icons.LanguageCss3
        "html" -> Icons.LanguageHtml5
        "go" -> Icons.LanguageGo
        "lua" -> Icons.LanguageLua
        "rs" -> Icons.LanguageRust
        "md" -> Icons.LanguageMarkdown
        "php" -> Icons.LanguagePhp
        "py" -> Icons.LanguagePython
        "swift" -> Icons.LanguageSwift
        "java" -> Icons.LanguageJava
        "js", "jsx" -> Icons.LanguageJavascript
        "kt", "kts" -> Icons.LanguageKotlin
        "ts", "tsx" -> Icons.LanguageTypescript
        "xml" -> Icons.LanguageXml
        "jpg", "jpeg", "png", "gif", "bmp", "svg", "ico" -> androidx.compose.material.icons.Icons.Default.Image
        "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "ogv" -> androidx.compose.material.icons.Icons.Default.VideoFile
        else -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
