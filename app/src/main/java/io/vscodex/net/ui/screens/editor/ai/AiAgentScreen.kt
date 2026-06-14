/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * VSCodeX is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;\n * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with VSCodeX.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package io.vscodex.net.ui.screens.editor.ai

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.ai.client.generativeai.type.asTextOrNull
import com.itsvks.monaco.MonacoEditor
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.vscodex.net.core.ai.AiChatHistory
import io.vscodex.net.core.ai.ChatMessage
import io.vscodex.net.core.ai.Gemini
import io.vscodex.net.core.ai.OpenRouter
import io.vscodex.net.ui.screens.editor.EditorViewModel
import io.vscodex.net.ui.screens.editor.components.view.CodeEditorView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

data class AttachedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Base64-encoded content (for images / text files sent to the AI) */
    val base64Content: String? = null,
    /** Plain text content (for code/text files appended to the prompt) */
    val textContent: String? = null
)

data class AiMessage(
    val content: String,
    val isUser: Boolean,
    val isError: Boolean    = false,
    val isStreaming: Boolean = false,
    /** Snapshot of the user prompt that triggered this AI reply — used for regeneration. */
    val sourcePrompt: String? = null,
    val sourceFull: String?   = null,
    /** Files the user attached when sending this message */
    val attachedFiles: List<AttachedFile> = emptyList(),
    /** Whether extended thinking was active when this message was sent */
    val usedThinking: Boolean = false
)

sealed class MessageSegment {
    data class Prose(val text: String) : MessageSegment()
    data class Code(val code: String, val lang: String) : MessageSegment()
}

fun parseSegments(raw: String): List<MessageSegment> {
    val result = mutableListOf<MessageSegment>()
    val fence  = Regex("```(\\w*)\\n?([\\s\\S]*?)```", RegexOption.MULTILINE)
    var cursor = 0
    for (match in fence.findAll(raw)) {
        if (match.range.first > cursor) {
            val prose = raw.substring(cursor, match.range.first).trim()
            if (prose.isNotEmpty()) result += MessageSegment.Prose(prose)
        }
        result += MessageSegment.Code(
            code = match.groupValues[2].trimEnd(),
            lang = match.groupValues[1].ifBlank { "code" }
        )
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        val tail = raw.substring(cursor).trim()
        if (tail.isNotEmpty()) result += MessageSegment.Prose(tail)
    }
    return result.ifEmpty { listOf(MessageSegment.Prose(raw)) }
}

/** Supported image MIME types for inline display */
private val IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

private val QUICK_PROMPTS = listOf(
    "Analyze"   to "Analyze this file thoroughly. Describe what it does, its structure, key functions/classes, and any potential issues.",
    "Find bugs" to "Look for bugs, logic errors, null pointer risks, or incorrect assumptions in this code. List each issue with line context.",
    "Explain"   to "Explain this code in plain language. What is its purpose, how does it work, and what are the main components?",
    "Improve"   to "Suggest concrete improvements for readability, performance, best practices, and maintainability.",
    "Tests"     to "Write unit tests for the key logic in this file. Include edge cases.",
    "Refactor"  to "Refactor this code for clarity and modern best practices. Show before/after for each change.",
    "Docs"      to "Write comprehensive documentation comments (KDoc/Javadoc) for all public symbols in this file."
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiAgentScreen(
    editorViewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    /** When non-null, called when the user presses the maximize/restore button.
     *  Pass `true` = expand to full-screen, `false` = restore to panel. */
    onMaximizeToggle: ((Boolean) -> Unit)? = null,
    isFullScreen: Boolean = false
) {
    val messages  = remember { mutableStateListOf<AiMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context   = LocalContext.current

    // Active streaming job
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val editorUiState by editorViewModel.uiState.collectAsStateWithLifecycle()
    var lastAnalyzedFilePath by remember { mutableStateOf<String?>(null) }

    // ── Feature toggles ───────────────────────────────────────────────────────
    var thinkingEnabled by remember { mutableStateOf(false) }
    var showThinkingMenu by remember { mutableStateOf(false) }
    var thinkingLevel by remember { mutableStateOf("medium") }   // low | medium | high

    // ── File attachments ──────────────────────────────────────────────────────
    val pendingAttachments = remember { mutableStateListOf<AttachedFile>() }
    var fileUploadError by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        scope.launch {
            uris.forEach { uri ->
                try {
                    val cr = context.contentResolver
                    val mime = cr.getType(uri) ?: "application/octet-stream"
                    val cursor = cr.query(uri, null, null, null, null)
                    val name = cursor?.use {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        it.moveToFirst()
                        if (idx >= 0) it.getString(idx) else "file"
                    } ?: "file"
                    val size = cursor?.use {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        it.moveToFirst()
                        if (idx >= 0) it.getLong(idx) else 0L
                    } ?: 0L

                    val isImage = mime in IMAGE_MIME_TYPES
                    val isText  = mime.startsWith("text/") || name.endsWith(".kt") ||
                                  name.endsWith(".java") || name.endsWith(".py") ||
                                  name.endsWith(".js")   || name.endsWith(".ts") ||
                                  name.endsWith(".json")  || name.endsWith(".xml") ||
                                  name.endsWith(".md")    || name.endsWith(".gradle") ||
                                  name.endsWith(".toml")  || name.endsWith(".yaml") ||
                                  name.endsWith(".yml")   || name.endsWith(".sh")

                    val (b64, text) = cr.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (isImage) Base64.encodeToString(bytes, Base64.NO_WRAP) to null
                        else if (isText) null to String(bytes, Charsets.UTF_8)
                        else Base64.encodeToString(bytes, Base64.NO_WRAP) to null
                    } ?: (null to null)

                    pendingAttachments.add(
                        AttachedFile(
                            uri          = uri,
                            name         = name,
                            mimeType     = mime,
                            sizeBytes    = size,
                            base64Content = b64,
                            textContent   = text
                        )
                    )
                    fileUploadError = null
                } catch (e: Exception) {
                    fileUploadError = "Could not read: ${e.message ?: "file"}"
                }
            }
        }
    }

    // ── Smart auto-scroll ─────────────────────────────────────────────────────
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.last()
            lastVisible.index >= info.totalItemsCount - 2
        }
    }
    var autoScrollEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(messages.size) {
        autoScrollEnabled = true
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(listState) {
        snapshotFlow { isAtBottom }.distinctUntilChanged().collect { atBottom ->
            if (!atBottom) autoScrollEnabled = false
        }
    }
    LaunchedEffect(messages.lastOrNull()?.content) {
        if (autoScrollEnabled && messages.isNotEmpty())
            listState.scrollToItem(messages.size - 1)
    }

    // ── File helpers ──────────────────────────────────────────────────────────

    fun getFileContent(): Pair<String, String>? {
        val state      = editorUiState
        val openedFile = state.openedFiles.getOrNull(state.selectedFileIndex) ?: return null
        val file       = openedFile.file
        val editorContent: String? = when (val v = editorViewModel.getSelectedEditor()) {
            is CodeEditorView -> v.editor.text.toString().takeIf { it.isNotEmpty() }
            is MonacoEditor   -> v.text.takeIf { it.isNotEmpty() }
            else              -> editorViewModel.getEditorForFile(file)?.editor?.text?.toString()
        }
        if (!editorContent.isNullOrEmpty()) return file.name to editorContent
        val disk = file.asRawFile()?.takeIf { it.exists() }?.readText() ?: return null
        return file.name to disk
    }

    fun currentFilePath(): String? {
        val state = editorUiState
        return state.openedFiles.getOrNull(state.selectedFileIndex)?.file?.absolutePath
    }

    fun getSelectedText(): String? {
        val editor = editorViewModel.getSelectedEditor()
        if (editor is CodeEditorView) {
            val cursor = editor.editor.cursor
            if (cursor.isSelected) {
                return editor.editor.text
                    .subContent(
                        cursor.leftLine,  cursor.leftColumn,
                        cursor.rightLine, cursor.rightColumn
                    )
                    .toString()
                    .takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    fun applyCodeToEditor(newCode: String) {
        val editor = editorViewModel.getSelectedEditor()
        if (editor is CodeEditorView) {
            val text    = editor.editor.text
            val lastLine = (text.lineCount - 1).coerceAtLeast(0)
            val lastCol  = text.getColumnCount(lastLine)
            text.replace(0, 0, lastLine, lastCol, newCode)
        } else if (editor is MonacoEditor) {
            editor.evaluateJavascript(
                """(function(){
                    var model = editor.getModel();
                    var range = model.getFullModelRange();
                    editor.pushUndoStop();
                    model.pushEditOperations([],
                        [{range: range, text: ${org.json.JSONObject.quote(newCode)}}], null);
                    editor.pushUndoStop();
                })();""", null
            )
        }
    }

    fun buildPrompt(
        userPrompt: String,
        fileName: String,
        fileContent: String,
        selectedText: String? = null,
        attachments: List<AttachedFile> = emptyList()
    ): String {
        val truncated = if (fileContent.length > 12_000)
            fileContent.take(12_000) + "\n... [file truncated]" else fileContent
        val selSection = if (!selectedText.isNullOrBlank())
            "\n\nCurrently selected snippet:\n```\n$selectedText\n```" else ""
        val attachSection = if (attachments.isNotEmpty()) {
            val parts = attachments.mapNotNull { att ->
                when {
                    att.textContent != null -> {
                        val preview = if (att.textContent.length > 4_000)
                            att.textContent.take(4_000) + "\n... [truncated]" else att.textContent
                        "Attached file \"${att.name}\":\n```\n$preview\n```"
                    }
                    att.base64Content != null && att.mimeType in IMAGE_MIME_TYPES ->
                        "[Image attached: ${att.name}]"
                    else -> "[File attached: ${att.name} (${att.mimeType})]"
                }
            }
            if (parts.isNotEmpty()) "\n\nAttachments:\n" + parts.joinToString("\n\n") else ""
        } else ""

        return """
            File: $fileName
            ```
            $truncated
            ```$selSection$attachSection

            User request: $userPrompt
        """.trimIndent()
    }

    fun buildPromptNoFile(
        userPrompt: String,
        attachments: List<AttachedFile> = emptyList()
    ): String {
        if (attachments.isEmpty()) return userPrompt
        val parts = attachments.mapNotNull { att ->
            when {
                att.textContent != null -> {
                    val preview = if (att.textContent.length > 4_000)
                        att.textContent.take(4_000) + "\n... [truncated]" else att.textContent
                    "Attached file \"${att.name}\":\n```\n$preview\n```"
                }
                att.base64Content != null && att.mimeType in IMAGE_MIME_TYPES ->
                    "[Image: ${att.name}]"
                else -> "[File: ${att.name}]"
            }
        }
        return userPrompt + "\n\nAttachments:\n" + parts.joinToString("\n\n")
    }

    fun buildHistory(upToIndex: Int = messages.size): List<ChatMessage> =
        messages.take(upToIndex)
            .filter { !it.isStreaming && it.content.isNotBlank() && !it.isError }
            .map { ChatMessage(role = if (it.isUser) "user" else "assistant", content = it.content) }

    fun persistHistory(filePath: String) {
        val toSave = messages
            .filter { !it.isStreaming && it.content.isNotBlank() }
            .map { AiChatHistory.PersistedMessage(it.content, it.isUser, it.isError) }
        AiChatHistory.save(context, filePath, toSave)
    }

    // ── Thinking suffix injected into the system prompt ───────────────────────
    fun thinkingSuffix(): String {
        if (!thinkingEnabled) return ""
        return when (thinkingLevel) {
            "low"  -> "\n\nThink briefly before answering."
            "high" -> "\n\nThink deeply and step-by-step before answering. Show your reasoning."
            else   -> "\n\nThink through the problem carefully before answering."
        }
    }

    // ── Core send + streaming ─────────────────────────────────────────────────

    fun sendPromptInternal(
        userDisplayText: String,
        fullPrompt: String,
        attachments: List<AttachedFile> = emptyList(),
        replaceIndex: Int? = null
    ) {
        if (isLoading) return

        val usedThinking = thinkingEnabled
        if (replaceIndex == null) {
            messages.add(
                AiMessage(
                    content       = userDisplayText,
                    isUser        = true,
                    attachedFiles = attachments,
                    usedThinking  = usedThinking
                )
            )
        }
        isLoading = true

        val streamingIndex = replaceIndex ?: messages.size
        val placeholder = AiMessage(
            content      = "",
            isUser       = false,
            isStreaming  = true,
            sourcePrompt = userDisplayText,
            sourceFull   = fullPrompt,
            usedThinking = usedThinking
        )
        if (replaceIndex != null && replaceIndex < messages.size) {
            messages[replaceIndex] = placeholder
        } else {
            messages.add(placeholder)
        }

        val history = buildHistory(upToIndex = streamingIndex).let { h ->
            h + ChatMessage("user", fullPrompt)
        }

        val job = scope.launch {
            try {
                if (OpenRouter.isConfigured()) {
                    val accumulated = StringBuilder()
                    val streamResult: Result<String> = OpenRouter.chatStream(
                        history  = history,
                        onToken  = { token: String ->
                            accumulated.append(token)
                            if (streamingIndex < messages.size) {
                                messages[streamingIndex] = messages[streamingIndex].copy(
                                    content    = accumulated.toString(),
                                    isStreaming = true
                                )
                            }
                        }
                    )
                    streamResult.onSuccess { _: String ->
                        if (streamingIndex < messages.size) {
                            messages[streamingIndex] = messages[streamingIndex].copy(
                                content    = accumulated.toString(),
                                isStreaming = false
                            )
                            currentFilePath()?.let { persistHistory(it) }
                        }
                    }.onFailure { error: Throwable ->
                        if (streamingIndex < messages.size) {
                            messages[streamingIndex] = AiMessage(
                                content    = "⚠ ${error.message ?: "Something went wrong."}",
                                isUser     = false,
                                isError    = true,
                                isStreaming = false
                            )
                        }
                    }
                } else {
                    Gemini.chat(fullPrompt)
                        .onSuccess { response ->
                            val text = response.candidates.firstOrNull()
                                ?.content?.parts?.firstOrNull()?.asTextOrNull()
                                ?: "No response received."
                            if (streamingIndex < messages.size) {
                                messages[streamingIndex] = AiMessage(
                                    content      = text,
                                    isUser       = false,
                                    isStreaming  = false,
                                    sourcePrompt = userDisplayText,
                                    sourceFull   = fullPrompt
                                )
                                currentFilePath()?.let { persistHistory(it) }
                            }
                        }
                        .onFailure { error ->
                            if (streamingIndex < messages.size) {
                                messages[streamingIndex] = AiMessage(
                                    content    = "⚠ ${error.message ?: "Something went wrong."}",
                                    isUser     = false,
                                    isError    = true,
                                    isStreaming = false
                                )
                            }
                        }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                if (streamingIndex < messages.size) {
                    val partial = messages[streamingIndex].content
                    messages[streamingIndex] = messages[streamingIndex].copy(
                        content    = partial.ifBlank { "_(generation stopped)_" },
                        isStreaming = false
                    )
                }
            } catch (e: Exception) {
                if (streamingIndex < messages.size) {
                    messages[streamingIndex] = AiMessage(
                        content    = "⚠ ${e.message ?: "Unknown error"}",
                        isUser     = false,
                        isError    = true,
                        isStreaming = false
                    )
                }
            } finally {
                isLoading = false
                activeJob = null
            }
        }
        activeJob = job
    }

    fun sendMessage() {
        val prompt = inputText.trim()
        val atts   = pendingAttachments.toList()
        if ((prompt.isEmpty() && atts.isEmpty()) || isLoading) return
        inputText = ""
        pendingAttachments.clear()
        fileUploadError = null

        val fileInfo     = getFileContent()
        val selectedText = getSelectedText()
        val full = if (fileInfo != null)
            buildPrompt(prompt, fileInfo.first, fileInfo.second, selectedText, atts)
        else
            buildPromptNoFile(prompt, atts)

        val displayText = buildString {
            if (prompt.isNotBlank()) {
                if (!selectedText.isNullOrBlank()) append("[selection] ")
                append(prompt)
            }
            if (atts.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append("[+${atts.size} file${if (atts.size > 1) "s" else ""}]")
            }
        }

        sendPromptInternal(
            userDisplayText = displayText,
            fullPrompt      = full,
            attachments     = atts
        )
    }

    fun sendQuickPrompt(label: String, prompt: String) {
        if (isLoading) return
        val fileInfo = getFileContent() ?: return
        sendPromptInternal(label, buildPrompt(prompt, fileInfo.first, fileInfo.second))
    }

    fun stopGeneration() { activeJob?.cancel() }

    fun regenerateMessage(aiMessageIndex: Int) {
        val msg  = messages.getOrNull(aiMessageIndex) ?: return
        val full = msg.sourceFull ?: return
        val display = msg.sourcePrompt ?: "Regenerate"
        sendPromptInternal(display, full, replaceIndex = aiMessageIndex)
    }

    // ── Restore saved history on file open ────────────────────────────────────
    val currentPath = currentFilePath()

    LaunchedEffect(currentPath) {
        val path = currentPath ?: return@LaunchedEffect
        if (path == lastAnalyzedFilePath) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        val saved = AiChatHistory.load(context, path)
        messages.clear()
        if (saved.isNotEmpty()) {
            saved.forEach { m ->
                messages.add(AiMessage(content = m.content, isUser = m.isUser, isError = m.isError))
            }
        }
        lastAnalyzedFilePath = path
    }

    // ── Derived UI state ──────────────────────────────────────────────────────
    val fileInfo = remember(editorUiState.selectedFileIndex, editorUiState.openedFiles.size) {
        getFileContent()
    }
    val lineCount = remember(currentPath) {
        currentPath?.let { p ->
            try { java.io.File(p).readLines().size } catch (_: Exception) { null }
        }
    }
    val isThinking = isLoading &&
            messages.lastOrNull()?.let { it.isStreaming && it.content.isEmpty() } == true
    val selectedText = remember(editorUiState) { getSelectedText() }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome, null,
                            tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "AI Agent",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (OpenRouter.isConfigured()) "OpenRouter" else "Gemini",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Header action icons
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Thinking toggle
                    Box {
                        IconButton(
                            onClick  = { showThinkingMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Psychology, "Thinking mode",
                                modifier = Modifier.size(19.dp),
                                tint = if (thinkingEnabled)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        DropdownMenu(
                            expanded       = showThinkingMenu,
                            onDismissRequest = { showThinkingMenu = false }
                        ) {
                            DropdownMenuItem(
                                text   = { Text("Thinking: Off", style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    thinkingEnabled  = false
                                    showThinkingMenu = false
                                },
                                leadingIcon = {
                                    if (!thinkingEnabled)
                                        Icon(Icons.Rounded.Clear, null, modifier = Modifier.size(16.dp))
                                }
                            )
                            DropdownMenuItem(
                                text   = { Text("Thinking: Low", style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    thinkingEnabled  = true
                                    thinkingLevel    = "low"
                                    showThinkingMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text   = { Text("Thinking: Medium", style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    thinkingEnabled  = true
                                    thinkingLevel    = "medium"
                                    showThinkingMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text   = { Text("Thinking: High", style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    thinkingEnabled  = true
                                    thinkingLevel    = "high"
                                    showThinkingMenu = false
                                }
                            )
                        }
                    }

                    // Clear chat
                    AnimatedVisibility(visible = messages.isNotEmpty()) {
                        IconButton(
                            onClick  = {
                                activeJob?.cancel()
                                messages.clear()
                                lastAnalyzedFilePath = null
                                currentPath?.let { AiChatHistory.clear(context, it) }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Clear, "Clear chat",
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Full-screen toggle (only shown if the host provides a callback)
                    if (onMaximizeToggle != null) {
                        IconButton(
                            onClick  = { onMaximizeToggle(!isFullScreen) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (isFullScreen) Icons.Outlined.FullscreenExit
                                else Icons.Outlined.Fullscreen,
                                if (isFullScreen) "Restore panel" else "Full screen",
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── File/selection context chips ──────────────────────────────────
            AnimatedVisibility(
                visible = fileInfo != null || !selectedText.isNullOrBlank(),
                enter   = fadeIn() + slideInVertically(),
                exit    = fadeOut()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (fileInfo != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome, null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${fileInfo.first} · ${if (lineCount != null && lineCount > 0) "$lineCount lines" else "…"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    AnimatedVisibility(visible = !selectedText.isNullOrBlank()) {
                        ContextChip(label = "selection attached",
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    // Thinking badge
                    AnimatedVisibility(visible = thinkingEnabled) {
                        ContextChip(
                            label = "thinking: $thinkingLevel",
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            textColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── Streaming progress bar ────────────────────────────────────────
            AnimatedVisibility(visible = isLoading && !isThinking) {
                LinearProgressIndicator(
                    modifier   = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .height(2.dp).clip(RoundedCornerShape(1.dp)),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // ── File upload error banner ──────────────────────────────────────
            AnimatedVisibility(visible = fileUploadError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        fileUploadError ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { fileUploadError = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Close, null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Message list ──────────────────────────────────────────────────
            LazyColumn(
                state   = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(Modifier.height(6.dp)) }

                if (messages.isEmpty() && !isLoading) {
                    item {
                        EmptyState(fileInfo = fileInfo)
                    }
                }

                items(messages, key = { msg -> System.identityHashCode(msg).toLong() }) { message ->
                    if (message.isStreaming && message.content.isEmpty()) return@items
                    val idx = messages.indexOf(message)
                    MessageBubble(
                        message      = message,
                        onCopy       = { clipboard.setText(AnnotatedString(message.content)) },
                        onCopyCode   = { code -> clipboard.setText(AnnotatedString(code)) },
                        onRegenerate = if (!message.isUser && !message.isStreaming && message.sourceFull != null)
                            {{ regenerateMessage(idx) }} else null,
                        onApplyCode  = { code -> applyCodeToEditor(code) }
                    )
                }

                item { Spacer(Modifier.height(4.dp)) }
            }

            // ── Pending attachments preview ───────────────────────────────────
            AnimatedVisibility(visible = pendingAttachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingAttachments) { att ->
                        AttachmentChip(att = att, onRemove = { pendingAttachments.remove(att) })
                    }
                }
            }

            // ── Quick-action chips ────────────────────────────────────────────
            AnimatedVisibility(
                visible = fileInfo != null && !isLoading,
                enter   = fadeIn() + slideInVertically { it / 2 },
                exit    = fadeOut()
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(QUICK_PROMPTS) { (label, prompt) ->
                        FilterChip(
                            selected = false,
                            onClick  = { sendQuickPrompt(label, prompt) },
                            label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors   = FilterChipDefaults.filterChipColors(
                                containerColor         = MaterialTheme.colorScheme.surfaceContainer,
                                labelColor             = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = false,
                                borderColor         = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // ── Input bar ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Attach file button
                BadgedBox(
                    badge = {
                        if (pendingAttachments.isNotEmpty()) {
                            Badge { Text(pendingAttachments.size.toString(),
                                style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                ) {
                    IconButton(
                        onClick  = { filePicker.launch("*/*") },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Outlined.AttachFile, "Attach file",
                            modifier = Modifier.size(20.dp),
                            tint = if (pendingAttachments.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { inputText = it },
                    modifier      = Modifier.weight(1f),
                    placeholder   = {
                        Text(
                            when {
                                pendingAttachments.isNotEmpty() -> "Ask about your files…"
                                fileInfo != null -> "Ask about ${fileInfo.first}…"
                                else -> "Ask about your code…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    shape     = RoundedCornerShape(24.dp),
                    maxLines  = 6,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors    = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(Modifier.width(6.dp))

                // Send / Stop button
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(
                        if (isLoading) MaterialTheme.colorScheme.errorContainer
                        else if (inputText.isNotBlank() || pendingAttachments.isNotEmpty())
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        IconButton(onClick = { stopGeneration() }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Rounded.Stop, "Stop generation",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    } else {
                        IconButton(
                            onClick  = { sendMessage() },
                            enabled  = inputText.isNotBlank() || pendingAttachments.isNotEmpty(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(Icons.Rounded.Send, "Send",
                                modifier = Modifier.size(20.dp),
                                tint = if (inputText.isNotBlank() || pendingAttachments.isNotEmpty())
                                    MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ── Thinking overlay ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = isThinking,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        ) { ThinkingPill() }

        // ── Scroll-to-bottom FAB ──────────────────────────────────────────────
        AnimatedVisibility(
            visible  = !isAtBottom && isLoading,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 100.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    autoScrollEnabled = true
                    scope.launch { listState.animateScrollToItem(messages.size - 1) }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation      = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Scroll to bottom",
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(fileInfo: Pair<String, String>?) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.AutoAwesome, null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (fileInfo != null) "Context loaded — ask anything"
                else "Open a file or attach files to get started",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "You can also attach images or code files below",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Context chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContextChip(label: String, color: Color, textColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Attachment chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AttachmentChip(att: AttachedFile, onRemove: () -> Unit) {
    val isImage = att.mimeType in IMAGE_MIME_TYPES
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isImage) {
            AsyncImage(
                model    = att.uri,
                contentDescription = att.name,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
            )
        } else {
            Icon(
                Icons.Outlined.AttachFile, null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column {
            Text(
                att.name.take(20) + if (att.name.length > 20) "…" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                formatFileSize(att.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Rounded.Close, "Remove",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024          -> "${bytes}B"
    bytes < 1024 * 1024   -> "${bytes / 1024}KB"
    else                  -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
}

// ─────────────────────────────────────────────────────────────────────────────
// Message bubble
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    message     : AiMessage,
    onCopy      : () -> Unit,
    onCopyCode  : (String) -> Unit,
    onRegenerate: (() -> Unit)?,
    onApplyCode : (String) -> Unit
) {
    val isUser = message.isUser

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome, null,
                    tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier            = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (isUser) {
                // Show attached images inline above the bubble
                if (message.attachedFiles.any { it.mimeType in IMAGE_MIME_TYPES }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        items(message.attachedFiles.filter { it.mimeType in IMAGE_MIME_TYPES }) { att ->
                            AsyncImage(
                                model    = att.uri,
                                contentDescription = att.name,
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
                // Non-image file chips
                val nonImageAtts = message.attachedFiles.filter { it.mimeType !in IMAGE_MIME_TYPES }
                if (nonImageAtts.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        items(nonImageAtts) { att ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.AttachFile, null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(att.name.take(18) + if (att.name.length > 18) "…" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                // Thinking badge on user messages
                if (message.usedThinking) {
                    Text(
                        "thinking mode",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (message.content.isNotBlank()) {
                    Card(
                        shape  = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text       = message.content,
                                modifier   = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style      = MaterialTheme.typography.bodyMedium,
                                color      = MaterialTheme.colorScheme.onPrimary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            } else {
                if (message.isStreaming) {
                    Card(
                        shape  = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column {
                            SelectionContainer {
                                Text(
                                    message.content,
                                    modifier   = Modifier.padding(
                                        start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
                                    style      = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp
                                )
                            }
                            StreamingCursor()
                        }
                    }
                } else {
                    val segments = remember(message.content) { parseSegments(message.content) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        segments.forEach { segment ->
                            when (segment) {
                                is MessageSegment.Prose -> {
                                    if (segment.text.isNotBlank()) {
                                        Card(
                                            shape  = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (message.isError)
                                                    MaterialTheme.colorScheme.errorContainer
                                                else MaterialTheme.colorScheme.surfaceContainer
                                            ),
                                            elevation = CardDefaults.cardElevation(0.dp)
                                        ) {
                                            MarkdownText(
                                                markdown         = segment.text,
                                                isTextSelectable = true,
                                                style            = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize   = 14.sp,
                                                    lineHeight = 20.sp
                                                ),
                                                modifier         = Modifier.padding(
                                                    horizontal = 14.dp, vertical = 10.dp)
                                            )
                                        }
                                    }
                                }
                                is MessageSegment.Code -> {
                                    CodeBlock(
                                        code    = segment.code,
                                        lang    = segment.lang,
                                        onCopy  = { onCopyCode(segment.code) },
                                        onApply = { onApplyCode(segment.code) }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.ContentCopy, "Copy",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                            if (onRegenerate != null) {
                                IconButton(onClick = onRegenerate, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.Refresh, "Regenerate",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Code block
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CodeBlock(
    code   : String,
    lang   : String,
    onCopy : () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                lang.lowercase().ifBlank { "code" },
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick        = onApply,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier       = Modifier.height(26.dp)
                ) {
                    Text("Apply",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick        = onCopy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier       = Modifier.height(26.dp)
                ) {
                    Text("Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        SelectionContainer {
            Text(
                text       = code,
                modifier   = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                fontFamily = FontFamily.Monospace,
                fontSize   = 12.sp,
                lineHeight = 18.sp,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Thinking pill
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThinkingPill() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue  = -200f,
        targetValue   = 400f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerOffset"
    )
    val pillAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.88f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pillAlpha"
    )

    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f * pillAlpha))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.matchParentSize().background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    start = Offset(shimmerOffset, 0f),
                    end   = Offset(shimmerOffset + 200f, 60f)
                )
            )
        )
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.AutoAwesome, null,
                tint     = MaterialTheme.colorScheme.primary.copy(alpha = pillAlpha),
                modifier = Modifier.size(14.dp))
            Text("Thinking…",
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = pillAlpha))
            ThinkingDots()
        }
    }
}

@Composable
private fun ThinkingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue  = 0.4f,
                targetValue   = 1f,
                animationSpec = infiniteRepeatable(
                    tween(500, delayMillis = index * 140, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "dot_scale_$index"
            )
            Box(
                modifier = Modifier
                    .size((5 * scale).dp.coerceAtLeast(2.dp))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f + 0.4f * scale))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Streaming cursor
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StreamingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Box(
        modifier = Modifier
            .padding(start = 12.dp, bottom = 8.dp)
            .size(width = 8.dp, height = 14.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                shape = RoundedCornerShape(2.dp)
            )
    )
}
