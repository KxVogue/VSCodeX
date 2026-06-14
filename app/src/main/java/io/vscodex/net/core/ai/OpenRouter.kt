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

package io.vscodex.net.core.ai

import io.vscodex.net.app.BaseApplication
import io.vscodex.net.resources.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** A single turn in the conversation history sent to the API. */
data class ChatMessage(val role: String, val content: String)

object OpenRouter {

    private const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"

    // ── credentials ──────────────────────────────────────────────────────────

    private fun apiKey(): String    = BaseApplication.instance.openRouterConfig().apiKey
    private fun model(): String     = BaseApplication.instance.openRouterConfig().model
    private fun sysPrompt(): String = BaseApplication.instance.openRouterConfig().systemPrompt

    fun isConfigured(): Boolean = apiKey().isNotBlank()

    // ── core HTTP call (non-streaming) ────────────────────────────────────────

    private suspend fun complete(
        systemPrompt: String?,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val messages = JSONArray().apply {
                val sys = systemPrompt ?: sysPrompt()
                if (sys.isNotBlank()) put(JSONObject().put("role", "system").put("content", sys))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            }
            doRequest(messages, stream = false).let { (code, body) ->
                if (code in 200..299) {
                    JSONObject(body)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else {
                    val errMsg = runCatching {
                        val e = JSONObject(body)
                        e.optJSONObject("error")?.optString("message") ?: e.optString("message") ?: body
                    }.getOrDefault(body)
                    throw RuntimeException("OpenRouter error $code: $errMsg")
                }
            }
        }
    }

    // ── SSE streaming with full history + cancellation ────────────────────────

    /**
     * Streams a response using the full [history] for multi-turn context.
     * Calls [onToken] on Main for each arriving delta.
     * Respects coroutine cancellation — when the coroutine is cancelled the
     * loop exits cleanly and the connection is disconnected.
     */
    suspend fun chatStream(
        history: List<ChatMessage>,
        onToken: suspend (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val messages = JSONArray().apply {
                val sys = sysPrompt()
                if (sys.isNotBlank()) put(JSONObject().put("role", "system").put("content", sys))
                history.forEach { msg ->
                    put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
            }

            val body = JSONObject()
                .put("model", model())
                .put("messages", messages)
                .put("max_completion_tokens", BaseApplication.instance.openRouterConfig().maxTokens)
                .put("temperature", BaseApplication.instance.openRouterConfig().temperature.toDouble())
                .put("stream", true)
                .toString()

            val conn = openConnection(stream = true)
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val statusCode = conn.responseCode
            if (statusCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $statusCode"
                conn.disconnect()
                throw RuntimeException("OpenRouter error $statusCode: $err")
            }

            val fullText = StringBuilder()
            try {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String? = null
                    while (isActive && reader.readLine().also { line = it } != null) {
                        val raw = line ?: continue
                        if (!raw.startsWith("data:")) continue
                        val data = raw.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        runCatching {
                            val json    = JSONObject(data)
                            val choices = json.optJSONArray("choices") ?: return@runCatching
                            val delta   = choices.getJSONObject(0).optJSONObject("delta") ?: return@runCatching
                            val token   = delta.optString("content", "")
                            if (token.isNotEmpty()) {
                                fullText.append(token)
                                withContext(Dispatchers.Main) { onToken(token) }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            fullText.toString()
        }
    }

    // Legacy single-prompt variant kept for non-agent callers
    suspend fun chat(prompt: String): Result<String> = chatStream(
        history  = listOf(ChatMessage("user", prompt)),
        onToken  = {}
    )

    // ── public API ────────────────────────────────────────────────────────────

    suspend fun explainCode(code: String): Result<String> = complete(
        systemPrompt = "You are an expert programmer. Explain the code clearly and concisely.",
        userPrompt   = BaseApplication.instance.getString(R.string.explain_code_msg, code)
    )

    suspend fun importComponents(code: String): Result<String> {
        if (!isJetpackComposeCode(code)) {
            return Result.failure(
                IllegalArgumentException("The provided code does not appear to be Jetpack Compose code.")
            )
        }
        return complete(
            systemPrompt = "You are a Jetpack Compose expert. Provide only the required import statements, nothing else.",
            userPrompt   = BaseApplication.instance.getString(R.string.import_compose_components_msg, code)
        )
    }

    suspend fun rewriteCode(code: String, instruction: String): Result<String> = complete(
        systemPrompt = "You are an expert programmer. Rewrite the code based on the instruction. " +
                "Return ONLY the raw rewritten code — no markdown fences, no explanation, no commentary.",
        userPrompt   = "Instruction: $instruction\n\nCode to rewrite:\n$code"
    )

    suspend fun generateCode(prompt: String, fileExtension: String? = null): Result<String> = complete(
        systemPrompt = "You are an expert programmer. Return ONLY raw code — no markdown fences, no explanation.",
        userPrompt   = "Write code based on this prompt" +
                (if (!fileExtension.isNullOrEmpty()) " for a .$fileExtension file" else "") +
                ":\n\n$prompt"
    )

    suspend fun completeCode(completionMetadata: CompletionMetadata): Result<String> = complete(
        systemPrompt = "You are an expert ${completionMetadata.language} programmer. " +
                "Complete the code at the <cursor> marker. Return ONLY the inserted text, no fences.",
        userPrompt   = """
            Complete the following ${completionMetadata.language} code:
            
            ${completionMetadata.textBeforeCursor}
            <cursor>
            ${completionMetadata.textAfterCursor}
            
            Use modern ${completionMetadata.language} practices and conventions.
        """.trimIndent()
    )

    // ── utilities ─────────────────────────────────────────────────────────────

    fun removeBackticksFromMarkdownCodeBlock(codeWithBackticks: String?): String {
        codeWithBackticks ?: return ""
        val trimmed = codeWithBackticks.trim()
        if (!trimmed.startsWith("```") || !trimmed.endsWith("```")) return codeWithBackticks
        val newline = trimmed.indexOf('\n')
        return if (newline > 3) trimmed.substring(newline + 1, trimmed.length - 3).trim()
        else trimmed.substring(3, trimmed.length - 3).trim()
    }

    private fun isJetpackComposeCode(code: String): Boolean =
        listOf(
            "@Composable", "Modifier", "Column", "Row", "Button", "Text", "Box",
            "LazyColumn", "LazyRow", "remember", "mutableStateOf"
        ).any { code.contains(it) }

    // ── internal HTTP helpers ─────────────────────────────────────────────────

    private fun openConnection(stream: Boolean): HttpURLConnection =
        (URL(BASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer ${apiKey()}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", if (stream) "text/event-stream" else "application/json")
            setRequestProperty("HTTP-Referer", "https://github.com/io-vscodex-net/VSCodeX")
            setRequestProperty("X-Title", "VSCodeX")
            doOutput       = true
            doInput        = true
            connectTimeout = 30_000
            readTimeout    = if (stream) 120_000 else 90_000
        }

    private fun doRequest(messages: JSONArray, stream: Boolean): Pair<Int, String> {
        val body = JSONObject()
            .put("model", model())
            .put("messages", messages)
            .put("max_completion_tokens", BaseApplication.instance.openRouterConfig().maxTokens)
            .put("temperature", BaseApplication.instance.openRouterConfig().temperature.toDouble())
            .apply { if (stream) put("stream", true) }
            .toString()

        val conn = openConnection(stream)
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
        val code   = conn.responseCode
        val stream2 = if (code in 200..299) conn.inputStream else conn.errorStream
        val text   = BufferedReader(InputStreamReader(stream2, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()
        return code to text
    }
}
