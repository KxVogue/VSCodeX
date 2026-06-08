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

package io.vscodex.ai.editor.completion

import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import io.vscodex.ai.utils.getAttrColor
import io.github.rosemoe.sora.widget.component.DefaultCompletionLayout
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class CustomCompletionLayout : DefaultCompletionLayout() {
    override fun onApplyColorScheme(colorScheme: EditorColorScheme) {
        (completionList.parent as? ViewGroup)?.background =
            GradientDrawable().apply {
                setStroke(
                    2,
                    completionList.context.getAttrColor(com.google.android.material.R.attr.colorOutline),
                )
                setColor(
                    completionList.context.getAttrColor(com.google.android.material.R.attr.colorSurface)
                )
                setCornerRadius(10f)
            }
    }
}
