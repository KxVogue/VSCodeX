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

data class Range(
    val start: Position,
    val end: Position,
) {
    fun isEmpty(): Boolean = start == end

    fun contains(position: Position): Boolean =
        (start.lineNumber < position.lineNumber || (start.lineNumber == position.lineNumber && start.column <= position.column)) &&
            (end.lineNumber > position.lineNumber || (end.lineNumber == position.lineNumber && end.column >= position.column))

    fun contains(other: Range): Boolean =
        contains(other.start) && contains(other.end)
}
