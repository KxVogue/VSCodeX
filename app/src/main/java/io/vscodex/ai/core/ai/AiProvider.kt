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

package io.vscodex.ai.core.ai

/**
 * Common interface for all AI back-ends (Gemini, OpenRouter, …).
 *
 * Call sites should depend on this interface and obtain the active
 * implementation via [AiProviderFactory.active], so that switching
 * providers never requires changes in UI code.
 */
interface AiProvider {
    suspend fun explainCode(code: String): Result<String>
    suspend fun rewriteCode(code: String, instruction: String): Result<String>
    suspend fun generateCode(prompt: String, fileExtension: String? = null): Result<String>
    suspend fun completeCode(metadata: CompletionMetadata): Result<String>
    suspend fun chat(prompt: String): Result<String>
    /**
     * Multi-turn streaming chat. Implementors that do not support streaming
     * may fall back to a single [chat] call and deliver the full response as
     * one token.
     */
    suspend fun chatStream(
        history: List<ChatMessage>,
        onToken: suspend (String) -> Unit
    ): Result<String>
}

// ── Shared helpers used by every provider ────────────────────────────────────

/** The keywords we use to detect Jetpack Compose code. */
private val COMPOSE_KEYWORDS = listOf(
    "@Composable", "Modifier", "Column", "Row", "Button", "Text", "Box",
    "LazyColumn", "LazyRow", "remember", "mutableStateOf"
)

fun isJetpackComposeCode(code: String): Boolean =
    COMPOSE_KEYWORDS.any { code.contains(it) }

/**
 * Strips a single surrounding markdown code-fence (``` … ```) if present.
 * Works identically for both Gemini and OpenRouter responses.
 */
fun stripMarkdownCodeFence(raw: String?): String {
    raw ?: return ""
    val trimmed = raw.trim()
    if (!trimmed.startsWith("```") || !trimmed.endsWith("```")) return raw
    val newline = trimmed.indexOf('\n')
    return if (newline > 3) trimmed.substring(newline + 1, trimmed.length - 3).trim()
    else trimmed.substring(3, trimmed.length - 3).trim()
}
