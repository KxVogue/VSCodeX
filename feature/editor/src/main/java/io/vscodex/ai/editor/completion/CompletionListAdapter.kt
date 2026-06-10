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

import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import com.blankj.utilcode.util.SizeUtils
import io.vscodex.ai.editor.databinding.LayoutCompletionItemBinding
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.widget.component.EditorCompletionAdapter

/**
 * VS Code-style completion popup adapter.
 *
 * Each row:   [●]  symbolName              Function  ← italic, right
 *                  override fun foo(p: Int)           ← muted signature
 *
 * Badge colors per kind (match VS Code / IntelliJ conventions):
 *   F  Function   #C75B39  orange-red
 *   K  Keyword    #7B4FBE  purple
 *   V  Value/var  #4B8BCD  blue
 *   C  Class      #3BA897  teal-green
 *   S  Snippet    #4BA87D  green
 *   A  AI         #3AA0A0  teal
 *   T  Tag        #C97E35  amber
 *   @  Attribute  #C2455A  red-pink
 *   ?  Unknown    #6B7A8D  gray
 */
class CompletionListAdapter : EditorCompletionAdapter() {

    override fun getItemHeight(): Int = SizeUtils.dp2px(48f)

    override fun getView(pos: Int, v: View?, parent: ViewGroup?, isSelected: Boolean): View {
        val binding = v?.let {
            LayoutCompletionItemBinding.bind(it)
        } ?: LayoutCompletionItemBinding.inflate(
            LayoutInflater.from(context), parent, false
        )

        val item = getItem(pos)

        val kind: CompletionItemKind = when (item) {
            is VSXCompletionItem    -> item.completionKind
            is SimpleCompletionItem -> kindFromDesc(item.desc?.toString())
            else                    -> CompletionItemKind.IDENTIFIER
        }

        val (letter, color) = kindBadge(kind)
        binding.itemIcon.text = letter
        binding.itemIcon.backgroundTintList = ColorStateList.valueOf(color)
        binding.itemType.text = kindDisplayName(kind)

        if (!TextUtils.isEmpty(item.label)) binding.itemLabel.text = item.label

        binding.itemDesc.text = when {
            !TextUtils.isEmpty(item.desc)  -> item.desc
            !TextUtils.isEmpty(item.label) -> item.label
            else                           -> ""
        }
        binding.itemDesc.visibility =
            if (TextUtils.isEmpty(binding.itemDesc.text)) View.GONE else View.VISIBLE

        binding.root.updatePadding(top = SizeUtils.dp2px(4f), bottom = SizeUtils.dp2px(4f))
        return binding.root
    }

    private fun kindFromDesc(desc: String?): CompletionItemKind {
        if (desc == null) return CompletionItemKind.IDENTIFIER
        return when {
            desc.contains("fun",       ignoreCase = true) ||
            desc.contains("function",  ignoreCase = true) ||
            desc.contains("method",    ignoreCase = true) -> CompletionItemKind.IDENTIFIER
            desc.contains("keyword",   ignoreCase = true) -> CompletionItemKind.KEYWORD
            desc.contains("class",     ignoreCase = true) ||
            desc.contains("type",      ignoreCase = true) -> CompletionItemKind.CLASS
            desc.contains("snippet",   ignoreCase = true) -> CompletionItemKind.SNIPPET
            desc.contains("value",     ignoreCase = true) ||
            desc.contains("val ",      ignoreCase = true) ||
            desc.contains("var ",      ignoreCase = true) -> CompletionItemKind.VALUE
            desc.contains("file",      ignoreCase = true) -> CompletionItemKind.FILE
            desc.contains("tag",       ignoreCase = true) -> CompletionItemKind.TAG
            desc.contains("attr",      ignoreCase = true) -> CompletionItemKind.ATTRIBUTE
            else                                          -> CompletionItemKind.IDENTIFIER
        }
    }

    private fun kindBadge(kind: CompletionItemKind): Pair<String, Int> = when (kind) {
        CompletionItemKind.IDENTIFIER    -> "F"  to 0xFFC75B39.toInt()
        CompletionItemKind.KEYWORD       -> "K"  to 0xFF7B4FBE.toInt()
        CompletionItemKind.VALUE         -> "V"  to 0xFF4B8BCD.toInt()
        CompletionItemKind.CLASS         -> "C"  to 0xFF3BA897.toInt()
        CompletionItemKind.SNIPPET       -> "S"  to 0xFF4BA87D.toInt()
        CompletionItemKind.AI_GENERATED  -> "A"  to 0xFF3AA0A0.toInt()
        CompletionItemKind.FILE          -> "F"  to 0xFF6B7A8D.toInt()
        CompletionItemKind.FOLDER        -> "D"  to 0xFF6B7A8D.toInt()
        CompletionItemKind.TAG           -> "T"  to 0xFFC97E35.toInt()
        CompletionItemKind.ATTRIBUTE     -> "@"  to 0xFFC2455A.toInt()
        CompletionItemKind.UNKNOWN       -> "?"  to 0xFF6B7A8D.toInt()
    }

    private fun kindDisplayName(kind: CompletionItemKind): String = when (kind) {
        CompletionItemKind.IDENTIFIER    -> "Function"
        CompletionItemKind.KEYWORD       -> "Keyword"
        CompletionItemKind.VALUE         -> "Value"
        CompletionItemKind.CLASS         -> "Class"
        CompletionItemKind.SNIPPET       -> "Snippet"
        CompletionItemKind.AI_GENERATED  -> "AI"
        CompletionItemKind.FILE          -> "File"
        CompletionItemKind.FOLDER        -> "Folder"
        CompletionItemKind.TAG           -> "Tag"
        CompletionItemKind.ATTRIBUTE     -> "Attribute"
        CompletionItemKind.UNKNOWN       -> ""
    }
}
