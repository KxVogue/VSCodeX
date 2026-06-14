package io.vscodex.ai.events

import io.vscodex.ai.app.Folder
import io.vscodex.ai.file.File

data class OnDeleteFileEvent(val file: File, val openedFolder: File)

data class OnCreateFileEvent(val file: File, val openedFolder: File)

data class OnCreateFolderEvent(val file: File, val openedFolder: File)

data class OnRefreshFolderEvent(val openedFolder: File)

data class OnRenameFileEvent(val oldFile: File, val newFile: File, val openedFolder: File)

data class OnOpenFolderEvent(val folder: Folder)
