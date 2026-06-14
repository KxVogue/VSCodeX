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

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import io.vscodex.ai.app.BaseApplication
import io.vscodex.ai.core.Secrets
import io.vscodex.ai.resources.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini model name — defined as a constant so it is easy to bump when
 * the experimental model graduates or expires, without hunting through code.
 */
const val GEMINI_MODEL_NAME = "gemini-2.0-flash-thinking-exp-01-21"

/**
 * Gemini-backed [AiProvider].
 *
 * Shared helpers ([isJetpackComposeCode], [stripMarkdownCodeFence]) live in
 * AiProvider.kt so they are not duplicated between providers.
 */
object GeminiProvider : AiProvider {

    private val model = GenerativeModel(
        modelName = GEMINI_MODEL_NAME,
        apiKey    = Secrets.getGenerativeAiApiKey(),
        generationConfig = generationConfig {
            temperature    = 0.7f
            topK           = 64
            topP           = 0.95f
            maxOutputTokens = 65536
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT,        BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.HATE_SPEECH,       BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE),
        )
    )

    // ── private helper ────────────────────────────────────────────────────────

    private suspend fun generate(prompt: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                model.generateContent(content { text(prompt) })
                    .candidates.firstOrNull()
                    ?.content?.parts?.firstOrNull()
                    ?.asTextOrNull()
                    ?: "No response received."
            }
        }

    // ── AiProvider ────────────────────────────────────────────────────────────

    override suspend fun explainCode(code: String): Result<String> =
        generate(BaseApplication.instance.getString(R.string.explain_code_msg, code))

    override suspend fun rewriteCode(code: String, instruction: String): Result<String> =
        generate(
            "Rewrite the following code based on this instruction: $instruction\n\n" +
            "Code:\n$code\n\nReturn ONLY the rewritten code, no explanation."
        )

    override suspend fun generateCode(prompt: String, fileExtension: String?): Result<String> =
        generate(
            "Write the code based on my prompt" +
            (if (!fileExtension.isNullOrEmpty()) " for file extension $fileExtension" else "") +
            " and provide me only code:\nThe prompt:\n\n$prompt"
        )

    override suspend fun completeCode(metadata: CompletionMetadata): Result<String> =
        generate(
            """
            Please complete the following ${metadata.language} code:
            
            ${metadata.textBeforeCursor}
            <cursor>
            ${metadata.textAfterCursor}
            
            Use modern ${metadata.language} practices and hooks where appropriate.
            Please provide only the completed part of the code without additional comments or explanations.
            """.trimIndent()
        )

    override suspend fun chat(prompt: String): Result<String> = generate(prompt)

    /**
     * Gemini SDK does not expose SSE streaming in the same way as OpenRouter,
     * so we deliver the full response as a single token.
     */
    override suspend fun chatStream(
        history: List<ChatMessage>,
        onToken: suspend (String) -> Unit
    ): Result<String> {
        // Extract system-role messages injected by the skill layer and prepend
        // them as a system instruction block so Gemini respects them.
        // Regular user/assistant turns are formatted as a readable conversation.
        val systemBlock = history
            .filter { it.role == "system" }
            .joinToString("\n\n") { it.content }
        val chatBlock = history
            .filter { it.role != "system" }
            .joinToString("\n\n") { msg ->
                "${if (msg.role == "user") "User" else "Assistant"}: ${msg.content}"
            }
        val fullPrompt = buildString {
            if (systemBlock.isNotBlank()) {
                append("[System Instructions]\n")
                append(systemBlock)
                append("\n\n")
            }
            if (chatBlock.isNotBlank()) append(chatBlock)
        }.trim()
        return generate(fullPrompt).onSuccess { text -> onToken(text) }
    }

    suspend fun importComponents(code: String): Result<String> {
        if (!isJetpackComposeCode(code)) {
            return Result.failure(
                IllegalArgumentException("The provided code does not appear to be Jetpack Compose code.")
            )
        }
        return generate(
            BaseApplication.instance.getString(R.string.import_compose_components_msg, code)
        )
    }
}

/**
 * Legacy top-level object kept for source-compatibility with call sites that
 * have not yet been migrated to [AiProviderFactory].
 *
 * New code should call [AiProviderFactory.active] instead.
 */
@Deprecated(
    message  = "Use AiProviderFactory.active() or GeminiProvider directly.",
    replaceWith = ReplaceWith("GeminiProvider", "io.vscodex.ai.core.ai.GeminiProvider")
)
object Gemini {
    suspend fun chat(prompt: String) = GeminiProvider.chat(prompt)
    suspend fun explainCode(code: String) = GeminiProvider.explainCode(code)
    suspend fun importComponents(code: String) = GeminiProvider.importComponents(code)
    suspend fun generateCode(prompt: String, fileExtension: String? = null) =
        GeminiProvider.generateCode(prompt, fileExtension)
    suspend fun rewriteCode(code: String, instruction: String) =
        GeminiProvider.rewriteCode(code, instruction)
    suspend fun completeCode(metadata: CompletionMetadata) =
        GeminiProvider.completeCode(metadata)
    fun removeBackticksFromMarkdownCodeBlock(raw: String?) = stripMarkdownCodeFence(raw)
}
