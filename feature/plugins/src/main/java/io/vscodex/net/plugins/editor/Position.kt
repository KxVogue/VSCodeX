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

package io.vscodex.net.plugins.editor

/**
 * Represents a cursor position within the editor.
 *
 * @property lineNumber The line number of the cursor position (1-based). Defaults to 1.
 * @property column The column number of the cursor position (1-based). Defaults to 1.
 *
 * @constructor Creates a new Position instance with the specified line and column numbers.
 *
 */
data class Position @JvmOverloads constructor(
    val lineNumber: Int = 1,
    val column: Int = 1
)
