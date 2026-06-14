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

package io.vscodex.net.plugins.impl

import com.blankj.utilcode.util.FileUtils
import io.vscodex.net.ui.screens.file.FileExplorerViewModel
import io.vscodex.net.plugins.Workspace
import java.io.File

class WorkspaceImpl(
    private val fileExplorerViewModel: FileExplorerViewModel
) : Workspace {
    override fun getProjectFiles(): MutableList<File> {
        return fileExplorerViewModel.openedFolder.value?.asRawFile()?.listFiles()?.toMutableList()
            ?: mutableListOf()
    }

    override fun getRootDirectory(): File {
        return fileExplorerViewModel.openedFolder.value!!.asRawFile()!!
    }

    override fun createFile(path: String): Boolean {
        return FileUtils.createOrExistsFile(path)
    }
}
