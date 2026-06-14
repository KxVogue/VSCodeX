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

package io.vscodex.ai.app

import android.content.res.Resources
import io.vscodex.ai.resources.R

typealias strings = R.string
typealias drawables = R.drawable
typealias fonts = R.font
typealias ids = R.id

inline val Int.dp: Int
  get() = (Resources.getSystem().displayMetrics.density * this + 0.5f).toInt()