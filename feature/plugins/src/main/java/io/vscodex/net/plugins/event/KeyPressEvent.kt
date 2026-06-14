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

package io.vscodex.net.plugins.event

class KeyPressEvent(
    val key: String?,
    val keyCode: Long = -1,
    val isCtrlPressed: Boolean = false,
    val isShiftPressed: Boolean = false,
    val isAltPressed: Boolean = false,
    override val type: EventType = EventType.KEY_PRESSED,
    override val data: Any? = key
) : Event {
    override fun toString(): String {
        return "KeyPressEvent(key='$key', type=$type, data=$data)"
    }
}
