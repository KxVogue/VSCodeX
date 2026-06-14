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

package io.vscodex.net.ui.screens.editor

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.ToastUtils
import com.google.gson.Gson
import com.itsvks.monaco.MonacoEditor
import io.vscodex.net.activities.EditorActivity.Companion.LAST_OPENED_FILES_JSON_PATH
import io.vscodex.net.core.EventManager
import io.vscodex.net.extensions.toFile
import io.vscodex.net.file.File
import io.vscodex.net.file.wrapFile
import io.vscodex.net.models.FileHistory
import io.vscodex.net.ui.screens.editor.components.view.CodeEditorView
import io.vscodex.net.plugins.event.FileCloseEvent
import io.vscodex.net.plugins.event.FileModifiedEvent
import io.vscodex.net.plugins.event.FileOpenedEvent
import io.vscodex.net.plugins.event.FileSaveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File as JFile

class EditorViewModel : ViewModel() {
    data class OpenedFile(
        val file: File,
        val isModified: Boolean = false
    )

    data class UiState(
        val openedFiles: List<OpenedFile> = emptyList(),
        val selectedFileIndex: Int = 0
    ) {
        val selectedFile get() = openedFiles.getOrNull(selectedFileIndex)
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState get() = _uiState.asStateFlow()

    private val _editors = mutableStateMapOf<String, CodeEditorView>()
    val editors get() = _editors

    private val _monacoEditors = mutableStateMapOf<String, MonacoEditor>()
    val monacoEditors get() = _monacoEditors

    private val _editorConfigMap = mutableStateMapOf<String, Boolean>()
    val editorConfigMap get() = _editorConfigMap

    private val _canEditorHandleCurrentKeyBinding = MutableStateFlow(false)
    val canEditorHandleCurrentKeyBinding get() = _canEditorHandleCurrentKeyBinding.asStateFlow()

    fun setCanEditorHandleCurrentKeyBinding(value: Boolean) {
        _canEditorHandleCurrentKeyBinding.value = value
    }

    fun setEditorConfiguredForFile(file: File) {
        _editorConfigMap[file.path] = true
    }

    fun getEditorForFile(
        context: Context,
        file: File,
        isAdvancedEditor: Boolean = false
    ): View {
        return if (isAdvancedEditor) {
            _monacoEditors.getOrPut(file.path) {
                MonacoEditor(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        } else {
            _editors.getOrPut(file.path) {
                CodeEditorView(context, file)
            }
        }
    }

    fun getEditorForFile(file: File): CodeEditorView? {
        return _editors[file.path]
    }

    fun getSelectedEditor(): View? {
        val files = uiState.value.openedFiles
        val index = uiState.value.selectedFileIndex
        val path = files.getOrNull(index)?.file?.path ?: return null
        return _monacoEditors[path] ?: _editors[path]
    }

    fun clearSelectionOnAllEditors() {
        _editors.values.forEach { editorView ->
            try {
                val editor = editorView.editor
                if (editor != null) {
                    val cursor = editor.cursor
                    val line = cursor.leftLine
                    val col = cursor.leftColumn
                    editor.setSelection(line, col)
                    editor.hideEditorWindows()
                }
            } catch (_: Exception) {}
        }
    }

    fun rememberLastFiles() {
        val lastOpenedFiles = Gson().toJson(FileHistory(uiState.value.openedFiles.mapNotNull {
            if (it.file.canRestoreFromPath) it.file.path else null
        }))

        viewModelScope.launch(Dispatchers.IO) {
            JFile(LAST_OPENED_FILES_JSON_PATH).apply {
                FileUtils.createOrExistsFile(this)
                writeText(lastOpenedFiles)
            }
        }
    }

    fun lastOpenedFiles(): List<File> {
        val file = JFile(LAST_OPENED_FILES_JSON_PATH)
        if (!file.exists()) return emptyList()

        val fileHistory =
            Gson().fromJson(file.readText(), FileHistory::class.java) ?: return emptyList()
        return fileHistory.lastOpenedFilesPath.map { it.toFile().wrapFile() }
    }

    fun setModified(file: File, modified: Boolean) {
        _uiState.update { currentState ->
            val updatedFiles = currentState.openedFiles.map { openedFile ->
                if (openedFile.file == file) {
                    openedFile.copy(isModified = modified)
                } else {
                    openedFile
                }
            }
            currentState.copy(openedFiles = updatedFiles)
        }

        if (modified) {
            EventManager.instance.postEvent(FileModifiedEvent(java.io.File(file.absolutePath)))
        }
    }

    suspend fun saveFile(editorView: View? = null) {
        val editor = getSelectedEditor() ?: editorView
        if (editor is CodeEditorView) {
            editor.saveFile()
            EventManager.instance.postEvent(FileSaveEvent(java.io.File(editor.file!!.absolutePath)))
            editor.file?.let { setModified(it, false) }
        } else if (editor is MonacoEditor) {
            uiState.value.selectedFile?.file?.let {
                it.write(editor.context, editor.text)
                setModified(it, false)
            }
        }
    }

    suspend fun saveAll() {
        editors.values.forEach {
            it.saveFile()
            EventManager.instance.postEvent(FileSaveEvent(java.io.File(it.file!!.absolutePath)))
            it.file?.let { file -> setModified(file, false) }
        }
    }

    fun addFile(file: File) {
        runCatching {
            val openedFile = OpenedFile(file)

            val newOpenedFiles = uiState.value.openedFiles.toMutableList()

            newOpenedFiles.remove(
                newOpenedFiles.find {
                    it.file.name == "untitled.txt" && !it.isModified
                }
            )

            if (!newOpenedFiles.contains(openedFile)) newOpenedFiles.add(openedFile)

            val newSelectedFileIndex = newOpenedFiles.indexOf(openedFile)

            _uiState.value = uiState.value.copy(
                openedFiles = newOpenedFiles,
                selectedFileIndex = newSelectedFileIndex
            )

            EventManager.instance.postEvent(FileOpenedEvent(java.io.File(file.absolutePath)))
        }.onFailure {
            ToastUtils.showShort(it.message)
        }
    }

    fun addFiles(files: List<File>) {
        viewModelScope.launch {
            files.forEach { addFile(it) }
        }
    }

    fun selectFile(index: Int) {
        _uiState.value = uiState.value.copy(selectedFileIndex = index)
        val selectedFile = uiState.value.openedFiles[index].file
        EventManager.instance.postEvent(FileOpenedEvent(java.io.File(selectedFile.absolutePath)))
    }

    fun closeFile(index: Int) {
        val newOpenedFiles = uiState.value.openedFiles.toMutableList()
        val closingFilePath = newOpenedFiles[index].file.path
        newOpenedFiles.removeAt(index)

        val newSelectedFileIndex = if (newOpenedFiles.isEmpty()) {
            0
        } else {
            index.coerceAtMost(newOpenedFiles.size - 1)
        }

        _editorConfigMap[closingFilePath] = false

        _uiState.value = uiState.value.copy(
            openedFiles = newOpenedFiles,
            selectedFileIndex = newSelectedFileIndex
        )
        EventManager.instance.postEvent(FileCloseEvent(java.io.File(closingFilePath)))

        _editors.remove(closingFilePath)?.release()
    }

    fun closeOthers(index: Int) {
        val newOpenedFiles = uiState.value.openedFiles.toMutableList()
        val selectedFile = newOpenedFiles[index]
        newOpenedFiles.removeAll { it != selectedFile }
        _uiState.value = uiState.value.copy(
            openedFiles = newOpenedFiles,
            selectedFileIndex = 0
        )
    }

    fun closeAll() {
        _editorConfigMap.clear()

        _uiState.value = uiState.value.copy(openedFiles = emptyList())

        _editors.values.forEach {
            EventManager.instance.postEvent(FileCloseEvent(java.io.File(it.file!!.absolutePath)))
            it.release()
        }
        _editors.clear()
    }
}
