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

package io.vscodex.ai.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import io.vscodex.ai.file.File
import io.vscodex.ai.file.extension
import java.nio.file.Files
import java.nio.file.Paths
import java.io.File as JFile

/** Regex used to check if file name extension is not of a text file. */
val INVALID_TEXT_FILES_REGEX =
    Regex(
        ".*\\.(bin|ttf|png|jpe?g|bmp|mp4|mp3|m4a|iso|so|zip|rar|jar|dex|odex|vdex|7z|apk|apks|xapk)$"
    )

// Known text-based MIME types that don't start with "text/"
private val ADDITIONAL_TEXT_MIME_TYPES = setOf(
    "application/json",
    "application/xml",
    "application/javascript",
    "application/x-sh",
    "application/x-www-form-urlencoded",
    "application/x-yaml",
    "application/x-php",
    "application/x-httpd-php",
    "application/x-perl",
    "application/xhtml+xml",
    "application/sql",
    "application/rtf",
    "application/csv",
    "application/x-latex"
)

// Extensions the OS MIME detector may not know but we treat as text
private val EXTRA_TEXT_EXTENSIONS = setOf(
    "ts", "tsx", "jsx", "vue", "svelte", "toml", "graphql",
    // Kotlin script – probeContentType returns null for .kts on Android
    "kts",
    // Other common code/config files the OS may not recognise
    "gradle", "kt", "dart", "rs", "go", "rb", "swift", "lua",
    "groovy", "tf", "hcl", "env", "gitignore", "gitmodules",
    "editorconfig", "prettierrc", "eslintrc", "babelrc",
    "ini", "cfg", "conf", "properties", "lock", "log",
    "diff", "patch", "proto", "thrift", "dockerfile",
)

/**
 * Returns true if [file] is safe to open in the text editor.
 *
 * An unknown MIME type (null) is treated as **binary** — the OS could not
 * determine the type, so opening it as text risks garbled content.
 * Users can always open such files explicitly.
 */
fun isValidTextFile(file: JFile): Boolean {
    if (INVALID_TEXT_FILES_REGEX.matches(file.name.lowercase())) return false
    if (file.extension.lowercase() in EXTRA_TEXT_EXTENSIONS) return true

    val type = Files.probeContentType(Paths.get(file.absolutePath))
    // null → OS could not determine type → treat as binary (safe default)
    type ?: return false

    return type.startsWith("text/") || type in ADDITIONAL_TEXT_MIME_TYPES
}

/**
 * Set of extensions whose files can be run/previewed in-app.
 * A Set<String> gives O(1) lookup and makes membership obvious at a glance.
 */
private val RUNNABLE_FILE_EXTENSIONS = setOf(
    "py", "html", "htm", "md", "sh", "bash", "zsh", "ksh", "fish"
)

fun isFileRunnable(file: File?): Boolean =
    file != null && file.extension.lowercase() in RUNNABLE_FILE_EXTENSIONS

/**
 * Checks if storage permission has been granted.
 *
 * @return If permission has been granted.
 */
fun Context.isStoragePermissionGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED)
    }
}

/**
 * Get the previous path from the current path, for example: [/path1/path2] it will get the
 * [/path1].
 *
 * @param path The way to get the parent path.
 * @return The parent path.
 */
fun getParentDirPath(path: String): String {
    val index = path.lastIndexOf("/")
    return if (index >= 0) path.substring(0, index) else path
}

fun createNomediaFile(directoryPath: String) {
    val directory = JFile(directoryPath)
    if (!directory.exists()) {
        directory.mkdirs()
    }
    val nomediaFile = JFile(directory, ".nomedia")
    if (!nomediaFile.exists()) {
        nomediaFile.createNewFile()
    }
}
