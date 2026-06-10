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

package io.vscodex.ai.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Centralised registry of open editor tabs and their dirty state.
 *
 * Keeping this logic here — rather than scattered across ViewModels and
 * Composables — makes it easy to query "are there unsaved changes?" from
 * anywhere (e.g. back-press handlers, the window close prompt).
 *
 * UI layers should observe [openFiles] and [dirtyFiles] as [StateFlow]s so
 * they recompose automatically when the state changes.
 */
class EditorManager private constructor() {

    // ── State ─────────────────────────────────────────────────────────────────

    /** Absolute paths of all currently open editor tabs, in tab order. */
    private val _openFiles = MutableStateFlow<List<String>>(emptyList())
    val openFiles: StateFlow<List<String>> = _openFiles.asStateFlow()

    /** Subset of [openFiles] that have unsaved changes. */
    private val _dirtyFiles = MutableStateFlow<Set<String>>(emptySet())
    val dirtyFiles: StateFlow<Set<String>> = _dirtyFiles.asStateFlow()

    /** Index of the currently focused tab, or -1 when no file is open. */
    private val _selectedIndex = MutableStateFlow(-1)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    // ── Derived helpers ───────────────────────────────────────────────────────

    /** Returns true if any open file has unsaved changes. */
    val hasUnsavedChanges: Boolean
        get() = _dirtyFiles.value.isNotEmpty()

    /** Returns true if [filePath] has unsaved changes. */
    fun isDirty(filePath: String): Boolean = filePath in _dirtyFiles.value

    val selectedFilePath: String?
        get() = _openFiles.value.getOrNull(_selectedIndex.value)

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun openFile(filePath: String) {
        _openFiles.update { current ->
            if (filePath in current) current else current + filePath
        }
        _selectedIndex.value = _openFiles.value.indexOf(filePath)
    }

    fun closeFile(filePath: String) {
        _openFiles.update { it - filePath }
        _dirtyFiles.update { it - filePath }
        val newList = _openFiles.value
        _selectedIndex.value = when {
            newList.isEmpty() -> -1
            _selectedIndex.value >= newList.size -> newList.size - 1
            else -> _selectedIndex.value
        }
    }

    fun selectFile(filePath: String) {
        val idx = _openFiles.value.indexOf(filePath)
        if (idx >= 0) _selectedIndex.value = idx
    }

    fun markDirty(filePath: String) {
        _dirtyFiles.update { it + filePath }
    }

    fun markClean(filePath: String) {
        _dirtyFiles.update { it - filePath }
    }

    fun closeAll() {
        _openFiles.value  = emptyList()
        _dirtyFiles.value = emptySet()
        _selectedIndex.value = -1
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    companion object {
        @JvmStatic
        val instance by lazy { EditorManager() }
    }
}
