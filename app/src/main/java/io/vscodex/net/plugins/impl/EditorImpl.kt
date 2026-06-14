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

import android.content.Context
import io.vscodex.net.editor.VSXEditor
import io.vscodex.net.plugins.Editor
import io.vscodex.net.plugins.editor.Position
import io.vscodex.net.plugins.editor.Range
import java.io.File

class EditorImpl(private val listener: Listener) : Editor {
    override fun getCurrentFile(): File? {
        return listener.currentFile
    }

    override fun getContext(): Context {
        return listener.context
    }

    override fun getCursorPosition(): Position {
        return listener.cursorPosition
    }

    override fun setCursorPosition(position: Position) {
        listener.cursorPosition = position
    }

    override fun insertText(position: Position, text: String) {
        val editor = listener.editor ?: return
        editor.text.insert(position.lineNumber, position.column, text)
    }

    override fun replaceText(start: Position, end: Position, text: String?) {
        val editor = listener.editor ?: return
        editor.text.replace(start.lineNumber, start.column, end.lineNumber, end.column, text)
    }

    override fun getSelectionRange(): Range? {
        val editor = listener.editor ?: return null

        if (editor.cursor.isSelected) {
            val leftLine = editor.cursor.leftLine
            val leftColumn = editor.cursor.leftColumn
            val rightLine = editor.cursor.rightLine
            val rightColumn = editor.cursor.rightColumn
            return Range(
                Position(leftLine, leftColumn),
                Position(rightLine, rightColumn)
            )
        } else {
            return null
        }
    }

    override fun getText(): String {
        val editor = listener.editor ?: return ""
        return editor.text.toString()
    }

    override fun getText(range: Range?): String? {
        if (range == null) return null
        if (range.isEmpty()) return ""

        val editor = listener.editor ?: return null
        val content = editor.text
        return content.substring(
            content.getCharIndex(range.start.lineNumber, range.start.column),
            content.getCharIndex(range.end.lineNumber, range.end.column)
        )
    }

    interface Listener {
        val currentFile: File?
        val context: Context
        var cursorPosition: Position
        val editor: VSXEditor?
    }
}
