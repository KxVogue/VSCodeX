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

package io.vscodex.ai.plugins

import androidx.compose.runtime.mutableStateOf
import io.vscodex.ai.plugins.dialog.DialogButtonClickListener

class DialogManager {
    companion object {
        @JvmStatic
        val instance by lazy { DialogManager() }
    }

    val showDialog = mutableStateOf(false)

    val title = mutableStateOf("")
    val message = mutableStateOf("")
    val positiveButtonText = mutableStateOf("")
    val negativeButtonText = mutableStateOf("")
    val positiveButtonClickListener = mutableStateOf<DialogButtonClickListener?>(null)
    val negativeButtonClickListener = mutableStateOf<DialogButtonClickListener?>(null)

    fun showDialog() {
        showDialog.value = true
    }

    fun hideDialog() {
        showDialog.value = false
    }
}
