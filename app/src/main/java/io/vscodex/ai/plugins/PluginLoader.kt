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
package io.vscodex.ai.plugins

import android.content.Context
import android.util.Log
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.ToastUtils
import dalvik.system.DexClassLoader
import io.vscodex.ai.PluginConstants
import io.vscodex.ai.extensions.extractZipFile
import io.vscodex.ai.extensions.toFile
import io.vscodex.ai.plugins.internal.PluginInfo
import io.vscodex.ai.utils.runOnUiThread
import io.vscodex.ai.utils.showShortToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PluginLoader"

object PluginLoader {

    /**
     * Loads all plugins found under [PluginConstants.PLUGIN_HOME_PATH].
     *
     * Each plugin directory is loaded in its own [runCatching] block so that
     * a malformed or incompatible plugin does not prevent other plugins from
     * loading. Errors are logged and skipped rather than propagated.
     */
    fun loadPlugins(context: Context): List<Pair<PluginInfo, Plugin>> {
        val results = mutableListOf<Pair<PluginInfo, Plugin>>()

        val pluginsPath = PluginConstants.PLUGIN_HOME_PATH.toFile()
        FileUtils.createOrExistsDir(pluginsPath)

        pluginsPath.listFiles()?.forEach { file ->
            runCatching { loadSinglePlugin(context, file) }
                .onSuccess { pair -> if (pair != null) results.add(pair) }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load plugin '${file.name}': ${error.message}", error)
                    runOnUiThread {
                        showShortToast(context, "Plugin '${file.name}' failed to load — see logcat for details.")
                    }
                }
        }

        return results
    }

    /**
     * Attempts to load a single plugin from [pluginDir].
     *
     * @return A [Pair] of [PluginInfo] and [Plugin], or null if the directory
     *         contains no plugin file (non-fatal — it may be a stray folder).
     * @throws Exception for any hard errors (missing properties, bad class, etc.)
     *         so the caller's [runCatching] can log and continue.
     */
    private fun loadSinglePlugin(context: Context, pluginDir: File): Pair<PluginInfo, Plugin>? {
        val properties = pluginDir.resolve("plugin.properties")
        if (!properties.exists()) {
            throw IllegalArgumentException(
                "Plugin directory '${pluginDir.name}' does not contain plugin.properties"
            )
        }

        val pluginInfo = PluginInfo(properties)
        val pluginFileName = pluginInfo.pluginFileName ?: run {
            // No file name declared — log a warning and skip silently
            Log.w(TAG, "Plugin '${pluginDir.name}' declares no pluginFileName — skipping.")
            return null
        }

        val jarFilePath = pluginDir.resolve(pluginFileName).apply {
            setWritable(false)
            setReadable(true, true)
        }

        val dexClassLoader = DexClassLoader(
            jarFilePath.absolutePath,
            /* optimizedDirectory = */ null,
            /* librarySearchPath  = */ null,
            context.applicationContext.classLoader
        )

        val pluginClass = dexClassLoader.loadClass(pluginInfo.mainClass)

        if (!Plugin::class.java.isAssignableFrom(pluginClass)) {
            throw IllegalArgumentException(
                "Plugin class '${pluginInfo.mainClass}' in '${pluginDir.name}' does not implement Plugin"
            )
        }

        val plugin = pluginClass.getConstructor().newInstance() as Plugin
        return pluginInfo to plugin
    }

    suspend fun extractPluginZip(pluginZipFile: File): File {
        return withContext(Dispatchers.IO) {
            val path         = "${PluginConstants.PLUGIN_HOME_PATH}/${pluginZipFile.nameWithoutExtension}"
            val internalFile = path.toFile()
            runCatching {
                FileUtils.createOrExistsDir(internalFile)
                pluginZipFile.extractZipFile(internalFile)
            }.onFailure {
                ToastUtils.showShort(it.message)
            }

            val properties = internalFile.resolve("plugin.properties")
            if (!properties.exists()) {
                throw IllegalArgumentException(
                    "Plugin directory '${internalFile.name}' does not contain plugin.properties"
                )
            }

            val pluginInfo = PluginInfo(properties)
            internalFile.apply {
                if (pluginInfo.name.isNullOrBlank()) {
                    throw NullPointerException("Plugin name is empty.")
                }
                FileUtils.rename(this, pluginInfo.name)
            }
        }
    }
}
