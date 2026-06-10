/*
 * This file is part of Visual Code X.
 *
 * Visual Code X is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Visual Code X is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Visual Code X.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package io.vscodex.ai.plugins;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.List;

public interface Workspace {
    /**
     * Get project files
     *
     * @return List of project files
     */
    List<File> getProjectFiles();

    /**
     * Get root directory
     *
     * @return Root directory
     */
    File getRootDirectory();

    /**
     * Create a new file at the specified path
     *
     * @param path Path to the file
     * @return true if the file was created successfully, false otherwise
     */
    boolean createFile(@NonNull String path);
}
