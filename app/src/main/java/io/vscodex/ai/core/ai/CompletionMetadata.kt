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

package io.vscodex.ai.core.ai

/**
 * Context passed to AI providers for inline code completion.
 *
 * @param fileName  Optional file name (e.g. "MainActivity.kt") included in the
 *                  prompt to give the model additional context about the file's
 *                  purpose and expected conventions.
 */
data class CompletionMetadata(
    val language: String,
    val textBeforeCursor: String,
    val textAfterCursor: String,
    val fileName: String? = null
)
