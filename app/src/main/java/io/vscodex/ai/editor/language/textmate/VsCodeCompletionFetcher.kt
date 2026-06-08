/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package io.vscodex.ai.editor.language.textmate

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.vscodex.ai.editor.completion.CompletionItemKind
import io.vscodex.ai.editor.completion.SimpleCompletionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Fetches VS Code's built-in language snippet and keyword data directly from
 * the microsoft/vscode GitHub repository (raw content), then converts them into
 * [SimpleCompletionItem]s that the editor's completion popup can display.
 *
 * ### How it works
 * VS Code ships snippets for each language as JSON files under
 * `extensions/<language>/snippets/<language>.code-snippets` (or `.json`).
 * This fetcher downloads those files at runtime and parses the standard VS Code
 * snippet format:
 *
 * ```json
 * "For Loop": {
 *   "prefix": "for",
 *   "body": ["for (${1:item} of ${2:items}) {", "\t$0", "}"],
 *   "description": "For Loop"
 * }
 * ```
 *
 * Results are **cached in-memory per language name** so subsequent calls are
 * instantaneous.
 *
 * ### Usage
 * ```kotlin
 * val items = VsCodeCompletionFetcher.fetchCompletions("kotlin", "for")
 * items.forEach { publisher.addItem(it) }
 * ```
 */
object VsCodeCompletionFetcher {

    private const val TAG = "VsCodeCompletionFetcher"

    /**
     * Raw-content base URL for the VS Code repository on the `main` branch.
     * Individual snippet files are appended as a path suffix.
     */
    private const val RAW_BASE =
        "https://raw.githubusercontent.com/microsoft/vscode/main/extensions"

    /**
     * Maps a grammar/language name (lowercase) to the VS Code extension folder
     * and snippet file path within that extension.
     *
     * Format: `"language-key" to "extension-folder/snippets/file.code-snippets"`
     */
    private val LANGUAGE_SNIPPET_PATHS = mapOf(
        "kotlin"     to "kotlin/snippets/kotlin.code-snippets",
        "java"       to "java/snippets/java.code-snippets",
        "python"     to "python/snippets/python.code-snippets",
        "javascript" to "javascript/snippets/javascript.code-snippets",
        "typescript" to "typescript/snippets/typescript.code-snippets",
        "html"       to "html/snippets/html.code-snippets",
        "css"        to "css/snippets/css.code-snippets",
        "scss"       to "scss/snippets/scss.code-snippets",
        "json"       to "json/snippets/json.code-snippets",
        "markdown"   to "markdown/snippets/markdown.code-snippets",
        "go"         to "go/snippets/go.code-snippets",
        "rust"       to "rust/snippets/rust.code-snippets",
        "cpp"        to "cpp/snippets/cpp.code-snippets",
        "c"          to "c/snippets/c.code-snippets",
        "csharp"     to "csharp/snippets/csharp.code-snippets",
        "swift"      to "swift/snippets/swift.code-snippets",
        "php"        to "php/snippets/php.code-snippets",
        "ruby"       to "ruby/snippets/ruby.code-snippets",
        "shellscript" to "shellscript/snippets/shellscript.code-snippets",
        "powershell" to "powershell/snippets/powershell.code-snippets",
        "sql"        to "sql/snippets/sql.code-snippets",
        "r"          to "r/snippets/r.code-snippets",
        "lua"        to "lua/snippets/lua.code-snippets",
        "dart"       to "dart/snippets/dart.code-snippets",
        "xml"        to "xml/snippets/xml.code-snippets",
        "yaml"       to "yaml/snippets/yaml.code-snippets",
    )

    /** In-memory cache: languageKey → list of parsed completion items */
    private val cache = ConcurrentHashMap<String, List<VsCodeSnippetItem>>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns VS Code snippet completions for [languageName] whose prefix
     * starts with [typedPrefix] (case-insensitive).
     *
     * This function is **suspend** and should be called from a coroutine.
     * It performs network I/O on [Dispatchers.IO] automatically.
     *
     * @param languageName  the grammar name, e.g. `"kotlin"`, `"python"`, `"html"`.
     *                      The lookup is case-insensitive and does a *contains* match
     *                      so `"TypeScript React"` still resolves to `"typescript"`.
     * @param typedPrefix   the characters the user has typed so far; used to filter
     *                      snippet prefixes.  Pass an empty string to get everything.
     * @return a list of [SimpleCompletionItem]s ready for the completion publisher.
     */
    suspend fun fetchCompletions(
        languageName: String,
        typedPrefix: String,
    ): List<SimpleCompletionItem> {
        val key = resolveLanguageKey(languageName) ?: return emptyList()
        val snippets = getOrFetch(key)
        return snippets
            .filter { it.prefix.startsWith(typedPrefix, ignoreCase = true) && it.prefix != typedPrefix }
            .map { it.toCompletionItem(typedPrefix.length) }
    }

    /**
     * Pre-warms the cache for [languageName] so the first completion request
     * returns instantly.  Safe to call on the main thread — it launches on IO.
     */
    suspend fun prefetch(languageName: String) {
        val key = resolveLanguageKey(languageName) ?: return
        getOrFetch(key)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Matches a grammar name to the canonical key used in [LANGUAGE_SNIPPET_PATHS]. */
    private fun resolveLanguageKey(grammarName: String): String? {
        val lower = grammarName.lowercase()
        return LANGUAGE_SNIPPET_PATHS.keys.firstOrNull { lower.contains(it) }
    }

    /** Returns cached items or fetches + parses them from GitHub. */
    private suspend fun getOrFetch(key: String): List<VsCodeSnippetItem> {
        cache[key]?.let { return it }
        val fetched = fetchAndParse(key)
        cache[key] = fetched
        return fetched
    }

    /** Downloads and parses the VS Code snippet JSON for [key]. */
    private suspend fun fetchAndParse(key: String): List<VsCodeSnippetItem> =
        withContext(Dispatchers.IO) {
            val path = LANGUAGE_SNIPPET_PATHS[key] ?: return@withContext emptyList()
            val url = "$RAW_BASE/$path"
            Log.d(TAG, "Fetching VS Code snippets from: $url")

            try {
                val request = Request.Builder().url(url).build()
                val body = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "HTTP ${response.code} for $url")
                        return@withContext emptyList()
                    }
                    response.body?.string() ?: return@withContext emptyList()
                }
                parseSnippetJson(body)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch snippets for '$key': ${e.message}")
                emptyList()
            }
        }

    /**
     * Parses the VS Code `.code-snippets` JSON format.
     *
     * The format is a JSON object where each value has:
     * - `prefix`      → String | Array<String>  (trigger word(s))
     * - `body`        → String | Array<String>  (inserted text lines)
     * - `description` → String  (shown as the secondary label)
     */
    private fun parseSnippetJson(json: String): List<VsCodeSnippetItem> {
        return try {
            // Strip JS-style // comments that VS Code allows in .code-snippets
            val stripped = json.replace(Regex("//[^\n]*"), "")
            val root = gson.fromJson(stripped, JsonObject::class.java)

            buildList {
                for ((snippetName, element) in root.entrySet()) {
                    if (!element.isJsonObject) continue
                    val obj = element.asJsonObject

                    val description = obj.get("description")?.asString ?: snippetName

                    // prefix can be a string or an array of strings
                    val prefixes: List<String> = when {
                        obj.has("prefix") && obj.get("prefix").isJsonPrimitive ->
                            listOf(obj.get("prefix").asString)
                        obj.has("prefix") && obj.get("prefix").isJsonArray ->
                            obj.get("prefix").asJsonArray
                                .mapNotNull { if (it.isJsonPrimitive) it.asString else null }
                        else -> continue
                    }

                    // body can be a string or an array of strings
                    val bodyLines: List<String> = when {
                        obj.has("body") && obj.get("body").isJsonPrimitive ->
                            listOf(obj.get("body").asString)
                        obj.has("body") && obj.get("body").isJsonArray ->
                            obj.get("body").asJsonArray
                                .mapNotNull { if (it.isJsonPrimitive) it.asString else null }
                        else -> continue
                    }

                    // Convert VS Code tabstop syntax ($1, ${1:placeholder}) to plain text
                    val bodyText = bodyLines.joinToString("\n")
                        .replace(Regex("""\$\{[0-9]+:([^}]*)}"""), "$1")  // ${1:placeholder} → placeholder
                        .replace(Regex("""\$[0-9]+"""), "")                // bare $1 → empty
                        .replace("\t", "    ")                              // tabs → 4 spaces

                    for (prefix in prefixes) {
                        if (prefix.isBlank()) continue
                        add(VsCodeSnippetItem(prefix.trim(), bodyText, description))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse snippet JSON: ${e.message}")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data model
    // ─────────────────────────────────────────────────────────────────────────

    /** Internal representation of one VS Code snippet entry. */
    private data class VsCodeSnippetItem(
        val prefix: String,
        val body: String,
        val description: String,
    ) {
        fun toCompletionItem(prefixLength: Int): SimpleCompletionItem =
            SimpleCompletionItem(
                completionKind = CompletionItemKind.SNIPPET,
                label          = prefix,
                desc           = description,
                prefixLength   = prefixLength,
                commitText     = body,
            )
    }
}
