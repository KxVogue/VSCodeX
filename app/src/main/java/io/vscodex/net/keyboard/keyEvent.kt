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

package io.vscodex.net.keyboard

import android.view.KeyCharacterMap
import android.view.KeyEvent

fun createKeyEvent(
    keyCode: Int,
    action: Int = KeyEvent.ACTION_DOWN,
    metaState: Int = KeyEvent.META_CTRL_ON
): KeyEvent {
    return KeyEvent(0, 0, action, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0)
}
