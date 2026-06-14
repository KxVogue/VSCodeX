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

package io.vscodex.ai.terminal

import io.vscodex.ai.activities.TerminalActivity
import io.vscodex.ai.extensions.child
import io.vscodex.ai.extensions.tmpDir
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

object Session {

    /** Known Termux prefix locations; first existing directory wins. */
    private val TERMUX_CANDIDATES = listOf(
        "/data/data/com.termux/files/usr",
        "/data/data/com.termux.api/files/usr"
    )

    private fun termuxPrefix(): File? =
        TERMUX_CANDIDATES.map(::File).firstOrNull { it.isDirectory }

    fun createSession(
        activity: TerminalActivity,
        sessionClient: TerminalSessionClient,
        sessionId: String
    ): TerminalSession {
        with(activity) {

            // ── Working directory ─────────────────────────────────────────
            val workingDir = intent.getStringExtra("cwd")
                ?.takeIf { it.isNotBlank() }
                ?: home.absolutePath

            // ── Per-session tmpdir (prevents cross-session file collisions) ─
            val sessionTmpDir = File(tmpDir, "terminal/$sessionId").also { d ->
                d.deleteRecursively()
                d.mkdirs()
            }

            // ── Proot binary ──────────────────────────────────────────────
            val prootBinary = File(filesDir, "proot").apply {
                if (!exists()) {
                    assets.open("terminal/proot").use { inp -> writeBytes(inp.readBytes()) }
                }
                setExecutable(true)
            }

            // ── PATH seen by init-host (host side) ────────────────────────
            val hostPath = listOf(
                bin.absolutePath,
                "${prefix.absolutePath}/bin",
                "${prefix.absolutePath}/sbin",
                "/system/bin",
                "/system/xbin"
            ).joinToString(":")

            // ── Build environment ─────────────────────────────────────────
            val env = mutableListOf(
                // proot plumbing
                "PROOT=${prootBinary.absolutePath}",
                "PROOT_TMP_DIR=${sessionTmpDir.absolutePath}",
                "ALPINE=${alpineDir.absolutePath}",
                "LINKER=${Executor.linker}",
                // VSX directories (used by init-host & init)
                "PREFIX=${prefix.absolutePath}",
                "HOME=${home.absolutePath}",
                "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath ?: ""}",
                // Shell / locale
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "LANG=C.UTF-8",
                "LC_ALL=C.UTF-8",
                // Shared library path (host side — proot doesn't affect this)
                "LD_LIBRARY_PATH=${lib.absolutePath}",
                // Temp
                "TMPDIR=${sessionTmpDir.absolutePath}",
                // Path used on the host before proot takes over
                "PATH=$hostPath"
            )

            // ── Termux detection ──────────────────────────────────────────
            termuxPrefix()?.let { tp ->
                env += "TERMUX_PREFIX=${tp.absolutePath}"
                File(tp.parentFile, "home").takeIf { it.isDirectory }
                    ?.let { env += "TERMUX_HOME=${it.absolutePath}" }
            }

            // ── Pass through Android system env vars ──────────────────────
            listOf(
                "ANDROID_ART_ROOT", "ANDROID_DATA", "ANDROID_I18N_ROOT",
                "ANDROID_ROOT", "ANDROID_RUNTIME_ROOT", "ANDROID_TZDATA_ROOT",
                "BOOTCLASSPATH", "DEX2OATBOOTCLASSPATH", "EXTERNAL_STORAGE"
            ).forEach { key -> System.getenv(key)?.let { v -> env += "$key=$v" } }

            // ── Stage scripts into PREFIX/bin (visible inside proot) ──────
            fun stage(assetName: String, fileName: String) =
                bin.child(fileName).apply {
                    assets.open("terminal/$assetName").use { writeBytes(it.readBytes()) }
                    setExecutable(true)
                }

            val initHost = stage("init-host.sh", "init-host")
            stage("init.sh",    "init")
            stage("pkg",        "pkg")

            // ── Launch via host sh → init-host → proot → Alpine ──────────
            return TerminalSession(
                "/system/bin/sh",
                workingDir,
                arrayOf("-c", initHost.absolutePath),
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient
            )
        }
    }
}
