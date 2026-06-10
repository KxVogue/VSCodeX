/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package io.vscodex.ai.linter

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Downloads VS Code's official `language-configuration.json` from the
 * microsoft/vscode GitHub repository and parses it into a [LangConfig]
 * that [SyntaxErrorDetector] uses to power its bracket-balance checks,
 * comment stripping, and indent/colon rules.
 *
 * All results are cached in-memory after the first fetch so repeated calls
 * within an app session are instant.
 *
 * ### What we pull from VS Code
 * | Field in JSON               | Used for                                  |
 * |-----------------------------|-------------------------------------------|
 * | `comments.lineComment`      | Stripping line comments correctly         |
 * | `comments.blockComment`     | Stripping block comments correctly        |
 * | `brackets`                  | Bracket-balance error detection           |
 * | `onEnterRules[].beforeText` | Detecting lines that must end with `:`    |
 * | `indentationRules`          | Detecting unclosed indent blocks          |
 */
object VsCodeLanguageConfigFetcher {

    private const val TAG = "VsCodeLangConfig"
    private const val RAW_BASE =
        "https://raw.githubusercontent.com/microsoft/vscode/main/extensions"

    // ── Extension → config file path ─────────────────────────────────────────
    // Maps each file extension (lowercase, no dot) to the VS Code extension
    // folder that contains its language-configuration.json.
    private val EXT_TO_VSCODE_PATH = mapOf(
        // JVM
        "java"       to "java/language-configuration.json",
        "kt"         to "java/language-configuration.json",  // closest available
        "kts"        to "java/language-configuration.json",
        "groovy"     to "java/language-configuration.json",
        "gradle"     to "java/language-configuration.json",
        // Web
        "html"       to "html/language-configuration.json",
        "htm"        to "html/language-configuration.json",
        "css"        to "css/language-configuration.json",
        "scss"       to "css/language-configuration.json",
        "sass"       to "css/language-configuration.json",
        "json"       to "json/language-configuration.json",
        "jsonc"      to "json/language-configuration.json",
        "ts"         to "typescript-basics/language-configuration.json",
        "tsx"        to "typescript-basics/language-configuration.json",
        "js"         to "typescript-basics/language-configuration.json",
        "jsx"        to "typescript-basics/language-configuration.json",
        "mjs"        to "typescript-basics/language-configuration.json",
        "cjs"        to "typescript-basics/language-configuration.json",
        // Systems
        "c"          to "cpp/language-configuration.json",
        "h"          to "cpp/language-configuration.json",
        "cpp"        to "cpp/language-configuration.json",
        "cxx"        to "cpp/language-configuration.json",
        "cc"         to "cpp/language-configuration.json",
        "hpp"        to "cpp/language-configuration.json",
        "cs"         to "csharp/language-configuration.json",
        "rs"         to "rust/language-configuration.json",
        "go"         to "go/language-configuration.json",
        // Scripting
        "py"         to "python/language-configuration.json",
        "pyi"        to "python/language-configuration.json",
        "rb"         to "ruby/language-configuration.json",
        "php"        to "php/language-configuration.json",
        "lua"        to "lua/language-configuration.json",
        "sh"         to "shellscript/language-configuration.json",
        "bash"       to "shellscript/language-configuration.json",
        "zsh"        to "shellscript/language-configuration.json",
        "ksh"        to "shellscript/language-configuration.json",
        "fish"       to "shellscript/language-configuration.json",
        // Mobile / cross-platform
        "dart"       to "dart/language-configuration.json",
        "swift"      to "swift/language-configuration.json",
        // Data / config
        "yaml"       to "yaml/language-configuration.json",
        "yml"        to "yaml/language-configuration.json",
        "sql"        to "sql/language-configuration.json",
    )

    // ── In-memory cache ───────────────────────────────────────────────────────
    private val cache = ConcurrentHashMap<String, LangConfig>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()

    // ─────────────────────────────────────────────────────────────────────────
    // Data model
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parsed subset of VS Code's language-configuration.json relevant for
     * static linting.
     */
    data class LangConfig(
        /** e.g. "//" for Java/JS/Kotlin  "#" for Python/Shell */
        val lineComment: String,
        /** e.g. Pair("/*","*/") or Pair("=begin","=end") for Ruby */
        val blockComment: Pair<String, String>?,
        /**
         * Bracket pairs: list of (open, close) chars.
         * e.g. listOf('{'to'}', '['to']', '('to')')
         */
        val bracketPairs: List<Pair<Char, Char>>,
        /**
         * Patterns from `onEnterRules.beforeText` that indicate a line
         * requires a trailing colon (Python/YAML style).  Stored as compiled
         * Regex objects.
         */
        val colonRequiredPatterns: List<Regex>,
        /**
         * `indentationRules.increaseIndentPattern` — a line matching this
         * opens an indent block (used to detect unclosed blocks).
         */
        val increaseIndentPattern: Regex?,
        /**
         * `indentationRules.decreaseIndentPattern` — a line matching this
         * closes an indent block.
         */
        val decreaseIndentPattern: Regex?,
    ) {
        companion object {
            /** Sensible defaults when the network is unavailable. */
            fun fallback(ext: String): LangConfig {
                val (lc, bo, bc) = when (ext.lowercase()) {
                    "py", "pyi", "rb", "sh", "bash", "zsh", "ksh", "fish",
                    "yaml", "yml" ->
                        Triple("#", null as String?, null as String?)
                    "lua"  -> Triple("--", "--[[", "]]")
                    "sql"  -> Triple("--", "/*", "*/")
                    "css", "scss", "sass" ->
                        Triple("", "/*", "*/")
                    else -> Triple("//", "/*", "*/")
                }
                val block = if (bo != null && bc != null) bo to bc else null
                return LangConfig(
                    lineComment = lc,
                    blockComment = block,
                    bracketPairs = listOf('{' to '}', '(' to ')', '[' to ']'),
                    colonRequiredPatterns = emptyList(),
                    increaseIndentPattern = null,
                    decreaseIndentPattern = null,
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the [LangConfig] for the given file [extension].
     * Fetches from GitHub on the first call; returns a cached result on all
     * subsequent calls.  Falls back to [LangConfig.fallback] if the network
     * is unavailable.
     *
     * This function is **suspend** — call it from a coroutine.
     */
    suspend fun getConfig(extension: String): LangConfig {
        val ext = extension.lowercase().trimStart('.')
        cache[ext]?.let { return it }
        val cfg = fetchAndParse(ext)
        cache[ext] = cfg
        return cfg
    }

    /** Pre-warms the cache for [extension]. Safe to call eagerly. */
    suspend fun prefetch(extension: String) {
        getConfig(extension)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchAndParse(ext: String): LangConfig =
        withContext(Dispatchers.IO) {
            val path = EXT_TO_VSCODE_PATH[ext]
                ?: return@withContext LangConfig.fallback(ext)
            val url = "$RAW_BASE/$path"
            Log.d(TAG, "Fetching VS Code language config: $url")
            try {
                val req  = Request.Builder().url(url).build()
                val body = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext LangConfig.fallback(ext)
                    resp.body?.string() ?: return@withContext LangConfig.fallback(ext)
                }
                parse(body, ext)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch config for '$ext': ${e.message}")
                LangConfig.fallback(ext)
            }
        }

    private fun parse(raw: String, ext: String): LangConfig {
        return try {
            // VS Code language-configuration.json allows JS-style // comments
            val stripped = raw.replace(Regex("//[^\n]*"), "")
            val root = gson.fromJson(stripped, JsonObject::class.java)

            // ── Comments ──────────────────────────────────────────────────────
            val comments = root.getObj("comments")
            val lineComment  = comments?.get("lineComment")?.asStringSafe() ?: ""
            val blockComment = comments?.getArr("blockComment")
                ?.let { arr ->
                    val open  = arr.getOrNull(0)?.asStringSafe()
                    val close = arr.getOrNull(1)?.asStringSafe()
                    if (open != null && close != null) open to close else null
                }

            // ── Bracket pairs ─────────────────────────────────────────────────
            // "brackets": [ ["{","}"], ["[","]"], ["(",")"] ]
            val brackets: List<Pair<Char, Char>> = root.getArr("brackets")
                ?.mapNotNull { el ->
                    if (!el.isJsonArray) return@mapNotNull null
                    val arr2 = el.asJsonArray
                    val o = arr2.getOrNull(0)?.asStringSafe()?.firstOrNull()
                    val c = arr2.getOrNull(1)?.asStringSafe()?.firstOrNull()
                    if (o != null && c != null) o to c else null
                }
                ?: listOf('{' to '}', '(' to ')', '[' to ']')

            // ── onEnterRules → colon-required patterns ────────────────────────
            val colonPatterns = root.getArr("onEnterRules")
                ?.mapNotNull { el ->
                    if (!el.isJsonObject) return@mapNotNull null
                    val obj = el.asJsonObject
                    // Accept either a plain string or {"pattern":"..."} for beforeText
                    val before = obj.get("beforeText")?.let { bt ->
                        when {
                            bt.isJsonPrimitive -> bt.asStringSafe()
                            bt.isJsonObject    -> bt.asJsonObject.get("pattern")?.asStringSafe()
                            else -> null
                        }
                    } ?: return@mapNotNull null
                    // Only keep rules whose pattern ends with `:` — these are
                    // the "must have colon" indent rules (Python, YAML, etc.)
                    if (!before.contains(":\\\\s*\$") && !before.contains(":\\s*$")) {
                        return@mapNotNull null
                    }
                    try { Regex(before) } catch (_: Exception) { null }
                }
                ?: emptyList()

            // ── indentationRules ──────────────────────────────────────────────
            val indentRules = root.getObj("indentationRules")
            val increasePattern = indentRules?.get("increaseIndentPattern")
                ?.let { el ->
                    val pat = when {
                        el.isJsonPrimitive -> el.asStringSafe()
                        el.isJsonObject    -> el.asJsonObject.get("pattern")?.asStringSafe()
                        else -> null
                    }
                    pat?.let { runCatching { Regex(it) }.getOrNull() }
                }
            val decreasePattern = indentRules?.get("decreaseIndentPattern")
                ?.let { el ->
                    val pat = when {
                        el.isJsonPrimitive -> el.asStringSafe()
                        el.isJsonObject    -> el.asJsonObject.get("pattern")?.asStringSafe()
                        else -> null
                    }
                    pat?.let { runCatching { Regex(it) }.getOrNull() }
                }

            LangConfig(
                lineComment            = lineComment,
                blockComment           = blockComment,
                bracketPairs           = brackets,
                colonRequiredPatterns  = colonPatterns,
                increaseIndentPattern  = increasePattern,
                decreaseIndentPattern  = decreasePattern,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse language config: ${e.message}")
            LangConfig.fallback(ext)
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun JsonObject.getObj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.getArr(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonElement.asStringSafe(): String? =
        runCatching { asString }.getOrNull()

    private fun JsonArray.getOrNull(index: Int): JsonElement? =
        if (index < size()) get(index) else null
}
