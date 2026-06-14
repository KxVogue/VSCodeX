package io.vscodex.net.providers

import android.annotation.SuppressLint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.vscodex.net.file.File
import io.vscodex.net.file.extension
import io.vscodex.net.models.FileIcon
import io.vscodex.net.resources.R
import io.vscodex.net.app.BaseApplication.Companion.instance as app

/**
 * Class to provide File icons
 *
 * @author Felipe Teixeira
 */
object FileIconProvider {

    private var fileIcons: List<FileIcon> = mutableListOf()

    init {
        val fileIconsJson =
            app.assets.open("files/file_icons.json").bufferedReader().use { it.readText() }
        fileIcons = Gson().fromJson(fileIconsJson, object : TypeToken<List<FileIcon>>() {})
    }

    @SuppressLint("DiscouragedApi")
    fun findFileIconResource(file: File): Int {
        val fileIcon = findFileIconByExtension(file.extension) ?: return R.drawable.ic_file
        val resId = app.resources.getIdentifier(fileIcon.drawableName, "drawable", app.packageName)
        return if (resId == 0) R.drawable.ic_file else resId
    }

    /**
     * Resolve an icon drawable resource directly from a file extension (no [File] wrapper
     * required). Useful for plain [java.io.File] lists such as recent files.
     */
    @SuppressLint("DiscouragedApi")
    fun findFileIconResource(extension: String): Int {
        val fileIcon = findFileIconByExtension(extension) ?: return R.drawable.ic_file
        val resId = app.resources.getIdentifier(fileIcon.drawableName, "drawable", app.packageName)
        return if (resId == 0) R.drawable.ic_file else resId
    }

    private fun findFileIconByExtension(extension: String): FileIcon? =
        fileIcons.find { it.fileExtensions.contains(extension) }
}
