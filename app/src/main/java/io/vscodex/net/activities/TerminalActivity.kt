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

package io.vscodex.net.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.ToastUtils
import io.vscodex.net.app.strings
import io.vscodex.net.extensions.child
import io.vscodex.net.extensions.createFileIfNot
import io.vscodex.net.extensions.localDir
import io.vscodex.net.extensions.tmpDir
import io.vscodex.net.terminal.Terminal
import io.vscodex.net.terminal.alpineDir
import io.vscodex.net.terminal.appDataDir
import io.vscodex.net.terminal.hosts
import io.vscodex.net.terminal.nameserver
import io.vscodex.net.terminal.prefix
import io.vscodex.net.terminal.service.TerminalService
import io.vscodex.net.ui.theme.VSXTheme
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files

class TerminalActivity : ComponentActivity() {

    var terminalBinder: TerminalService.TerminalBinder? = null
    var isBound by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            terminalBinder = service as TerminalService.TerminalBinder
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            terminalBinder = null
            isBound = false
        }
    }

    val workingDirectory: String
        get() {
            val dir = intent.extras?.getString(KEY_WORKING_DIRECTORY)
            return if (!dir.isNullOrBlank()) dir else PathUtils.getRootPathExternalFirst()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VSXTheme { Surface { MainScreen() } } }
    }

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, TerminalService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        if (isBound) { unbindService(serviceConnection); isBound = false }
        super.onStop()
    }

    // ── Setup screen ──────────────────────────────────────────────────────────

    @Composable
    fun MainScreen() {
        var progress     by remember { mutableFloatStateOf(0f) }
        var progressText by remember { mutableStateOf("Initializing...") }
        var setupDone    by remember { mutableStateOf(false) }
        var needsDownload by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            try {
                val abi = Build.SUPPORTED_ABIS

                val filesToDownload = mutableListOf(
                    DownloadFile(
                        url = when {
                            abi.contains("x86_64")     -> x86_64_packages
                            abi.contains("arm64-v8a")  -> aarch64_packages
                            abi.contains("armeabi-v7a") -> arm_packages
                            else -> throw RuntimeException("Unsupported CPU architecture")
                        },
                        outputPath = "tmp/usr.tar.gz"
                    )
                )

                if (alpineDir.listFiles().isNullOrEmpty()) {
                    filesToDownload += DownloadFile(
                        url = when {
                            abi.contains("x86_64")     -> alpine_x86_64
                            abi.contains("arm64-v8a")  -> alpine_aarch64
                            abi.contains("armeabi-v7a") -> alpine_arm
                            else -> throw RuntimeException("Unsupported CPU architecture")
                        },
                        outputPath = "tmp/alpine.tar.gz"
                    )
                }

                needsDownload = filesToDownload.any { !File(filesDir.parentFile, it.outputPath).exists() }

                setupEnvironment(
                    context       = this@TerminalActivity,
                    filesToDownload = filesToDownload,
                    onProgress    = { done, total, cur ->
                        if (needsDownload) {
                            val p = ((done.toFloat() + cur) / total).coerceIn(0f, 1f)
                            progress = p
                            progressText = "Downloading... ${(p * 100).toInt()}%"
                        }
                    },
                    onComplete    = { setupDone = true },
                    onError       = { e ->
                        e.printStackTrace()
                        ToastUtils.showShort("Setup failed: ${e.message}")
                        finish()
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort("Setup failed: ${e.message}")
                finish()
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!setupDone) {
                if (needsDownload) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = progressText, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                    }
                }
            } else {
                Terminal(terminalActivity = this@TerminalActivity)
            }
        }
    }

    // ── File execution helpers ────────────────────────────────────────────────

    /**
     * Run a Python file in the terminal.
     * Uses `python3` (always available after `pkg install python3`).
     * Does NOT call `exit` so the terminal stays open after the script finishes.
     */
    fun compilePython(terminal: TerminalView) {
        var filePath = intent?.extras?.getString(KEY_PYTHON_FILE_PATH) ?: return
        if (filePath.contains(" ")) filePath = "'$filePath'"
        ThreadUtils.getMainHandler().post {
            // Auto-install python3 if missing, then run
            val cmd = "clear && " +
                "( command -v python3 >/dev/null 2>&1 || pkg install python3 ) && " +
                "python3 $filePath; " +
                "echo; echo -e '\\033[32;1m--- done (exit \$?) ---\\033[0m'"
            terminal.mTermSession.write("$cmd\r")
        }
    }

    /**
     * Run a shell / Python script in the terminal.
     * Detects the interpreter from the shebang line.
     * Does NOT call `exit` so the terminal stays open.
     */
    fun runShellScript(terminal: TerminalView) {
        var filePath = intent?.extras?.getString(KEY_SHELL_FILE_PATH) ?: return
        val rawPath = filePath
        if (filePath.contains(" ")) filePath = "'$filePath'"

        val shebang = try {
            File(rawPath).bufferedReader().readLine()?.trim() ?: ""
        } catch (_: Exception) { "" }

        val interpreter = when {
            shebang.startsWith("#!/usr/bin/env python3") ||
            shebang.startsWith("#!/usr/bin/env python")   -> "python3"
            shebang.startsWith("#!/usr/bin/env bash") ||
            shebang.startsWith("#!/bin/bash") ||
            shebang.startsWith("#!/usr/bin/bash")          -> "bash"
            shebang.startsWith("#!/usr/bin/env zsh") ||
            shebang.startsWith("#!/bin/zsh")               -> "zsh"
            shebang.startsWith("#!/usr/bin/env fish")      -> "fish"
            shebang.startsWith("#!/usr/bin/env sh") ||
            shebang.startsWith("#!/bin/sh")                -> "sh"
            else                                           -> "bash"
        }

        ThreadUtils.getMainHandler().post {
            val cmd = "clear && chmod +x $filePath && " +
                "( command -v $interpreter >/dev/null 2>&1 || pkg install $interpreter ) && " +
                "$interpreter $filePath; " +
                "echo; echo -e '\\033[32;1m--- done (exit \$?) ---\\033[0m'"
            terminal.mTermSession.write("$cmd\r")
        }
    }

    // ── Environment setup ─────────────────────────────────────────────────────

    data class DownloadFile(val url: String, val outputPath: String)

    private suspend fun setupEnvironment(
        context: Context,
        filesToDownload: List<DownloadFile>,
        onProgress: (completedFiles: Int, totalFiles: Int, currentProgress: Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            var completed = 0
            val total = filesToDownload.size

            filesToDownload.forEach { file ->
                val out = File(context.filesDir.parentFile, file.outputPath)
                out.parentFile?.mkdirs()
                if (!out.exists()) {
                    out.createNewFile()
                    downloadFile(out, file.url) { downloaded, totalBytes ->
                        val cur = downloaded.toFloat() / totalBytes.toFloat()
                        runOnUiThread { onProgress(completed, total, cur) }
                    }
                }
                completed++
                withContext(Dispatchers.Main) { onProgress(completed, total, 0f) }
                runCatching { out.setExecutable(true) }
            }

            makeRootFs { extractPackage { runOnUiThread { onComplete() } } }

        } catch (e: Exception) {
            e.printStackTrace()
            localDir.deleteRecursively()
            withContext(Dispatchers.Main) { onError(e) }
        }
    }

    private fun extractPackage(onComplete: () -> Unit) {
        val usr = File(tmpDir, "usr.tar.gz")
        if (!usr.exists() || prefix.listFiles()?.isNotEmpty() == true) {
            onComplete(); return
        }
        // Use array form to avoid shell injection and ensure correct argument splitting
        Runtime.getRuntime()
            .exec(arrayOf("tar", "-xf", usr.absolutePath, "-C", appDataDir.absolutePath))
            .waitFor()
        usr.delete()

        // Create libtalloc.so.2 → libtalloc.so.2.4.1 symlink if needed
        val so2 = File(prefix, "lib/libtalloc.so.2")
        val so241 = File(prefix, "lib/libtalloc.so.2.4.1")
        if (so241.exists() && !so2.exists()) {
            runCatching { Files.createSymbolicLink(so2.toPath(), so241.toPath()) }
        }
        onComplete()
    }

    private fun makeRootFs(onComplete: () -> Unit) {
        val alpine = File(tmpDir, "alpine.tar.gz")
        if (!alpine.exists() || alpineDir.listFiles()?.isNotEmpty() == true) {
            onComplete(); return
        }
        Runtime.getRuntime()
            .exec(arrayOf("tar", "-xf", alpine.absolutePath, "-C", alpineDir.absolutePath))
            .waitFor()
        alpine.delete()
        with(alpineDir) {
            child("etc/hostname").writeText(getString(strings.app_name))
            child("etc/resolv.conf").also { it.createFileIfNot(); it.writeText(nameserver) }
            child("etc/hosts").writeText(hosts)
        }
        onComplete()
    }

    private suspend fun downloadFile(
        outputFile: File,
        url: String,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code} for $url")
            val body = response.body ?: throw Exception("Empty response for $url")
            val total = body.contentLength()
            var downloaded = 0L
            outputFile.outputStream().use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(8 * 1024)
                    var n: Int
                    while (inp.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        withContext(Dispatchers.Main) { onProgress(downloaded, total) }
                    }
                }
            }
        }
    }

    companion object {
        const val KEY_WORKING_DIRECTORY = "terminal_workingDirectory"
        const val KEY_PYTHON_FILE_PATH  = "terminal_python_file"
        const val KEY_SHELL_FILE_PATH   = "terminal_shell_file"
    }
}

// ── Package/rootfs URLs ───────────────────────────────────────────────────────

private const val aarch64_packages =
    "https://github.com/itsvks19/vcspace-packages/raw/refs/heads/main/aarch64/usr.tar.gz"
private const val arm_packages =
    "https://github.com/itsvks19/vcspace-packages/raw/refs/heads/main/arm/usr.tar.gz"
private const val x86_64_packages =
    "https://github.com/itsvks19/vcspace-packages/raw/refs/heads/main/x86_64/usr.tar.gz"

private const val alpine_arm =
    "https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/armhf/alpine-minirootfs-3.22.1-armhf.tar.gz"
private const val alpine_aarch64 =
    "https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/aarch64/alpine-minirootfs-3.22.1-aarch64.tar.gz"
private const val alpine_x86_64 =
    "https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/x86_64/alpine-minirootfs-3.22.1-x86_64.tar.gz"
