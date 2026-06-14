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

package io.vscodex.ai.ui.screens.editor.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itsvks.monaco.MonacoEditor
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.vscodex.ai.core.ai.AiChatHistory
import io.vscodex.ai.core.ai.AiProviderFactory
import io.vscodex.ai.core.ai.ChatMessage
import io.vscodex.ai.core.ai.OpenRouter
import io.vscodex.ai.ui.screens.editor.EditorViewModel
import io.vscodex.ai.ui.screens.editor.components.view.CodeEditorView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// ── Data model ────────────────────────────────────────────────────────────────

data class AiMessage(
    val content: String,
    val isUser: Boolean,
    val isError: Boolean    = false,
    val isStreaming: Boolean = false,
    val sourcePrompt: String? = null,
    val sourceFull: String?   = null
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

// ── Quick prompts (shown in empty state) ─────────────────────────────────────

private data class QuickPrompt(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val prompt: String
)

private val QUICK_PROMPTS = listOf(
    QuickPrompt(
        label       = "Analyze",
        description = "Structure, purpose & issues",
        icon        = Icons.Outlined.Search,
        prompt      = "Analyze this file thoroughly. Describe what it does, its structure, key functions/classes, and any potential issues."
    ),
    QuickPrompt(
        label       = "Find bugs",
        description = "Logic errors & risks",
        icon        = Icons.Outlined.BugReport,
        prompt      = "Look for bugs, logic errors, null pointer risks, or incorrect assumptions in this code. List each issue with line context."
    ),
    QuickPrompt(
        label       = "Explain",
        description = "Plain language walkthrough",
        icon        = Icons.Outlined.Psychology,
        prompt      = "Explain this code in plain language. What is its purpose, how does it work, and what are the main components?"
    ),
    QuickPrompt(
        label       = "Improve",
        description = "Readability & performance",
        icon        = Icons.Outlined.Speed,
        prompt      = "Suggest concrete improvements for readability, performance, best practices, and maintainability."
    ),
    QuickPrompt(
        label       = "Write tests",
        description = "Unit tests with edge cases",
        icon        = Icons.Outlined.Code,
        prompt      = "Write unit tests for the key logic in this file. Include edge cases."
    )
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAgentScreen(
    editorViewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val messages  = remember { mutableStateListOf<AiMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context   = LocalContext.current

    var activeJob by remember { mutableStateOf<Job?>(null) }

    val editorUiState by editorViewModel.uiState.collectAsStateWithLifecycle()
    var lastAnalyzedFilePath by remember { mutableStateOf<String?>(null) }

    // Smart auto-scroll
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

    // File helpers

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
            val text     = editor.editor.text
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

    fun buildPrompt(userPrompt: String, fileName: String, fileContent: String,
                    selectedText: String? = null): String {
        val truncated = if (fileContent.length > 12_000)
            fileContent.take(12_000) + "\n... [file truncated]" else fileContent
        val selSection = if (!selectedText.isNullOrBlank())
            "\n\nCurrently selected snippet:\n```\n$selectedText\n```" else ""
        return """
            File: $fileName
            ```
            $truncated
            ```$selSection

            User request: $userPrompt
        """.trimIndent()
    }

    // Load skill system prompts from assets (silently injected on chat start)
    val skillSystemPrompt = remember {
        try {
            context.assets.open("ai/skills/SKILL.md").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }
    val codingSkillPrompt = remember {
        try {
            context.assets.open("ai/skills/CodingSKILL.md").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    /** Convert current messages to API history format for multi-turn context. */
    fun buildHistory(upToIndex: Int = messages.size): List<ChatMessage> {
        val chatHistory = messages.take(upToIndex)
            .filter { !it.isStreaming && it.content.isNotBlank() && !it.isError }
            .map { ChatMessage(role = if (it.isUser) "user" else "assistant", content = it.content) }
        // Always prepend skill system messages at the top of every request so the
        // agent behaviour is active for the full session, not just the first turn.
        val systemMessages = listOfNotNull(
            skillSystemPrompt?.let { ChatMessage(role = "system", content = it) },
            codingSkillPrompt?.let { ChatMessage(role = "system", content = it) }
        )
        return systemMessages + chatHistory
    }

    /** Persist the current session. */
    fun persistHistory(filePath: String) {
        val toSave = messages
            .filter { !it.isStreaming && it.content.isNotBlank() }
            .map { AiChatHistory.PersistedMessage(it.content, it.isUser, it.isError) }
        AiChatHistory.save(context, filePath, toSave)
    }

    // Core send + streaming

    fun sendPromptInternal(
        userDisplayText: String,
        fullPrompt: String,
        replaceIndex: Int? = null
    ) {
        if (isLoading) return

        if (replaceIndex == null) {
            messages.add(AiMessage(content = userDisplayText, isUser = true))
        }
        isLoading = true

        val streamingIndex = replaceIndex ?: messages.size
        val placeholder = AiMessage(
            content      = "",
            isUser       = false,
            isStreaming  = true,
            sourcePrompt = userDisplayText,
            sourceFull   = fullPrompt
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
                val accumulated = StringBuilder()
                val provider = AiProviderFactory.active()
                val streamResult: Result<String> = provider.chatStream(
                    history = history,
                    onToken = { token: String ->
                        accumulated.append(token)
                        if (streamingIndex < messages.size) {
                            messages[streamingIndex] = messages[streamingIndex].copy(
                                content    = accumulated.toString(),
                                isStreaming = true
                            )
                        }
                    }
                )
                streamResult.onSuccess {
                    if (streamingIndex < messages.size) {
                        messages[streamingIndex] = messages[streamingIndex].copy(
                            content    = accumulated.toString(),
                            isStreaming = false
                        )
                        currentFilePath()?.let { persistHistory(it) }
                    }
                }.onFailure { error ->
                    if (streamingIndex < messages.size) {
                        messages[streamingIndex] = AiMessage(
                            content    = "⚠ ${error.message ?: "Something went wrong."}",
                            isUser     = false,
                            isError    = true,
                            isStreaming = false
                        )
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                if (streamingIndex < messages.size) {
                    val partial = messages[streamingIndex].content
                    messages[streamingIndex] = messages[streamingIndex].copy(
                        content     = partial.ifBlank { "_(generation stopped)_" },
                        isStreaming = false
                    )
                }
            } catch (e: Exception) {
                if (streamingIndex < messages.size) {
                    messages[streamingIndex] = AiMessage(
                        content     = "⚠ ${e.message ?: "Unknown error"}",
                        isUser      = false,
                        isError     = true,
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
        if (prompt.isEmpty() || isLoading) return
        inputText = ""
        val fileInfo     = getFileContent()
        val selectedText = getSelectedText()
        val full = if (fileInfo != null)
            buildPrompt(prompt, fileInfo.first, fileInfo.second, selectedText)
        else prompt
        sendPromptInternal(
            userDisplayText = if (!selectedText.isNullOrBlank()) "[selection] $prompt" else prompt,
            fullPrompt      = full
        )
    }

    fun sendQuickPrompt(prompt: QuickPrompt) {
        if (isLoading) return
        val fileInfo = getFileContent() ?: return
        sendPromptInternal(prompt.label, buildPrompt(prompt.prompt, fileInfo.first, fileInfo.second))
    }

    fun stopGeneration() { activeJob?.cancel() }

    fun regenerateMessage(aiMessageIndex: Int) {
        val msg = messages.getOrNull(aiMessageIndex) ?: return
        val full = msg.sourceFull ?: return
        val display = msg.sourcePrompt ?: "Regenerate"
        sendPromptInternal(display, full, replaceIndex = aiMessageIndex)
    }

    // Restore saved history on file open
    val currentPath = currentFilePath()

    LaunchedEffect(currentPath) {
        val path = currentPath ?: return@LaunchedEffect
        if (path == lastAnalyzedFilePath) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        val saved = AiChatHistory.load(context, path)
        if (saved.isNotEmpty()) {
            messages.clear()
            saved.forEach { m ->
                messages.add(AiMessage(content = m.content, isUser = m.isUser, isError = m.isError))
            }
        } else {
            messages.clear()
        }
        lastAnalyzedFilePath = path
    }

    // Derived UI state
    val fileInfo = remember(editorUiState.selectedFileIndex, editorUiState.openedFiles.size) {
        getFileContent()
    }
    val lineCount = remember(currentPath) {
        currentPath?.let { p ->
            try { java.io.File(p).readLines().size } catch (_: Exception) { null }
        }
    }
    val selectedText = remember(editorUiState) { getSelectedText() }
    val isThinking = isLoading &&
            messages.lastOrNull()?.let { it.isStreaming && it.content.isEmpty() } == true
    val isEmpty = messages.isEmpty() && !isLoading
    val providerName = if (OpenRouter.isConfigured()) "OpenRouter" else "Gemini"

    // ── UI ────────────────────────────────────────────────────────────────────

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {

        Column(modifier = Modifier.fillMaxSize().imePadding()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Animated gradient orb — Claude-style
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome, null,
                            tint     = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "AI Agent",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    // Provider pill — Claude-style model label
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            providerName,
                            style     = MaterialTheme.typography.labelSmall,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize  = 10.sp
                        )
                    }
                }
                AnimatedVisibility(visible = messages.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            activeJob?.cancel()
                            messages.clear()
                            lastAnalyzedFilePath = null
                            currentPath?.let { AiChatHistory.clear(context, it) }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Clear, "Clear chat",
                            modifier = Modifier.size(18.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Message list ──────────────────────────────────────────────────
            LazyColumn(
                state   = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Empty state ───────────────────────────────────────────────
                if (isEmpty) {
                    item {
                        Column(
                            modifier            = Modifier.fillMaxWidth().padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Gradient orb — larger for empty state
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome, null,
                                    tint     = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (fileInfo != null) "What can I help you with?"
                                else "Open a file to get started",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.onSurface
                            )
                            if (fileInfo != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Analyzing ${fileInfo.first}${if (lineCount != null) " · $lineCount lines" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Quick prompt suggestion cards
                    if (fileInfo != null) {
                        item {
                            FlowRow(
                                modifier            = Modifier.fillMaxWidth().padding(top = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement   = Arrangement.spacedBy(8.dp)
                            ) {
                                QUICK_PROMPTS.forEach { qp ->
                                    SuggestionCard(
                                        prompt = qp,
                                        onClick = { sendQuickPrompt(qp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Messages ──────────────────────────────────────────────────
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

                // ── Thinking indicator — inline at bottom of list ─────────────
                if (isThinking) {
                    item { ThinkingRow() }
                }

                item { Spacer(Modifier.height(4.dp)) }
            }

            // ── Input area ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // File + selection context chips inside a container above the field
                val showContext = fileInfo != null || !selectedText.isNullOrBlank()
                AnimatedVisibility(visible = showContext) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (fileInfo != null) {
                            ContextChip(
                                label = "${fileInfo.first}${if (lineCount != null) " · $lineCount lines" else ""}",
                                icon  = Icons.Outlined.Code
                            )
                        }
                        if (!selectedText.isNullOrBlank()) {
                            ContextChip(
                                label = "selection",
                                icon  = Icons.Outlined.Code,
                                tint  = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                // Input card — Claude-style borderless inner field with send button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier          = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value         = inputText,
                            onValueChange = { inputText = it },
                            modifier      = Modifier.weight(1f),
                            placeholder   = {
                                Text(
                                    if (fileInfo != null) "Ask about ${fileInfo.first}…"
                                    else "Message AI Agent…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            maxLines  = 6,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors    = TextFieldDefaults.colors(
                                focusedContainerColor   = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor   = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor  = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction      = ImeAction.Default
                            )
                        )
                        // Send / Stop button
                        val canSend = inputText.isNotBlank() && !isLoading
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 4.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isLoading -> MaterialTheme.colorScheme.errorContainer
                                        canSend   -> MaterialTheme.colorScheme.primary
                                        else      -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                )
                                .clickable {
                                    if (isLoading) stopGeneration() else sendMessage()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = isLoading,
                                transitionSpec = {
                                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                },
                                label = "send_stop_icon"
                            ) { loading ->
                                if (loading) {
                                    Icon(
                                        Icons.Rounded.Stop, "Stop",
                                        modifier = Modifier.size(16.dp),
                                        tint     = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.ArrowUpward, "Send",
                                        modifier = Modifier.size(16.dp),
                                        tint     = if (canSend) MaterialTheme.colorScheme.onPrimary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Scroll-to-bottom FAB — visible any time user scrolled up ──────────
        AnimatedVisibility(
            visible  = !isAtBottom,
            enter    = fadeIn() + slideInVertically { it / 2 },
            exit     = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 100.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
                    .clickable {
                        autoScrollEnabled = true
                        scope.launch {
                            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown, "Scroll to bottom",
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Suggestion card (empty state) ─────────────────────────────────────────────

@Composable
private fun SuggestionCard(prompt: QuickPrompt, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                prompt.icon, null,
                modifier = Modifier.size(15.dp),
                tint     = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    prompt.label,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    prompt.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Context chip ──────────────────────────────────────────────────────────────

@Composable
private fun ContextChip(
    label: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(10.dp), tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

// ── Thinking row — inline in message list ─────────────────────────────────────

@Composable
private fun ThinkingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoAwesome, null,
                tint     = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        ThinkingDots()
    }
}

// ── Message bubble ────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    message     : AiMessage,
    onCopy      : () -> Unit,
    onCopyCode  : (String) -> Unit,
    onRegenerate: (() -> Unit)?,
    onApplyCode : (String) -> Unit
) {
    if (message.isUser) {
        // User — right-aligned, constrained bubble
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                SelectionContainer {
                    Text(
                        text       = message.content,
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = MaterialTheme.colorScheme.onPrimary,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    } else {
        // AI — full-width, left-aligned with avatar
        Row(
            modifier  = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar orb
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome, null,
                    tint     = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (message.isStreaming) {
                    // Live streaming — plain text, no card
                    SelectionContainer {
                        Text(
                            text       = message.content,
                            style      = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    StreamingCursor()
                } else {
                    // Finished — parse segments
                    val segments = remember(message.content) { parseSegments(message.content) }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        segments.forEach { segment ->
                            when (segment) {
                                is MessageSegment.Prose -> {
                                    if (segment.text.isNotBlank()) {
                                        if (message.isError) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.errorContainer)
                                                    .padding(12.dp)
                                            ) {
                                                Text(
                                                    segment.text,
                                                    style  = MaterialTheme.typography.bodyMedium,
                                                    color  = MaterialTheme.colorScheme.onErrorContainer,
                                                    lineHeight = 22.sp
                                                )
                                            }
                                        } else {
                                            SelectionContainer {
                                                MarkdownText(
                                                    markdown         = segment.text,
                                                    isTextSelectable = true,
                                                    style            = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize   = 14.sp,
                                                        lineHeight = 22.sp
                                                    )
                                                )
                                            }
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

                        // Action row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            SmallActionButton(
                                icon        = Icons.Rounded.ContentCopy,
                                description = "Copy",
                                onClick     = onCopy
                            )
                            if (onRegenerate != null) {
                                SmallActionButton(
                                    icon        = Icons.Rounded.Refresh,
                                    description = "Regenerate",
                                    onClick     = onRegenerate
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Small icon action button ──────────────────────────────────────────────────

@Composable
private fun SmallActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier.size(30.dp)
    ) {
        Icon(
            icon, description,
            modifier = Modifier.size(14.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
    }
}

// ── Syntax token colors ───────────────────────────────────────────────────────

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "enum", "sealed",
    "data", "abstract", "open", "override", "private", "public", "protected",
    "internal", "companion", "init", "constructor", "return", "if", "else",
    "when", "for", "while", "do", "try", "catch", "finally", "throw", "is",
    "as", "in", "out", "by", "get", "set", "suspend", "inline", "reified",
    "import", "package", "typealias", "this", "super", "null", "true", "false",
    "def", "lambda", "pass", "yield", "with", "from", "raise", "except",
    "async", "await", "and", "or", "not", "del", "global", "nonlocal",
    "const", "let", "function", "typeof", "instanceof", "new", "delete",
    "void", "switch", "case", "break", "continue", "default", "export",
    "extends", "implements", "static", "readonly", "type", "namespace",
    "func", "struct", "impl", "trait", "pub", "use", "mod", "fn", "mut",
    "ref", "where", "match", "loop", "unsafe", "extern", "crate", "self",
    "defer", "go", "chan", "select", "map", "range", "make", "len", "cap",
    "SELECT", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER",
    "ON", "GROUP", "BY", "ORDER", "HAVING", "INSERT", "INTO", "UPDATE",
    "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "DISTINCT", "LIMIT"
)

/** Tokenize [code] into a syntax-colored [AnnotatedString]. */
@Composable
private fun syntaxHighlight(code: String, lang: String): AnnotatedString {
    val colorSurface    = MaterialTheme.colorScheme.onSurface
    val colorKeyword    = MaterialTheme.colorScheme.primary
    val colorString     = Color(0xFF4CAF50)
    val colorComment    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val colorNumber     = Color(0xFFFF9800)
    val colorAnnotation = MaterialTheme.colorScheme.tertiary

    val skipLangs = setOf("text", "txt", "plaintext", "markdown", "md", "")
    if (lang.lowercase() in skipLangs) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = colorSurface)) { append(code) }
        }
    }

    data class Span(val start: Int, val end: Int, val color: Color,
                    val bold: Boolean = false, val italic: Boolean = false)

    val spans = mutableListOf<Span>()
    val covered = mutableListOf<IntRange>()

    fun isCovered(start: Int) = covered.any { it.contains(start) }

    // 1. Single-line comments  // …  and  # …
    Regex("""(//[^\n]*|#[^\n]*)""").findAll(code).forEach { m ->
        spans += Span(m.range.first, m.range.last + 1, colorComment, italic = true)
        covered += m.range
    }
    // 2. Block comments  /* … */
    Regex("""/\*[\s\S]*?\*/""").findAll(code).forEach { m ->
        if (!isCovered(m.range.first)) {
            spans += Span(m.range.first, m.range.last + 1, colorComment, italic = true)
            covered += m.range
        }
    }
    // 3. Triple-quoted strings
    Regex("""\"\"\"[\s\S]*?\"\"\"""").findAll(code).forEach { m ->
        if (!isCovered(m.range.first)) {
            spans += Span(m.range.first, m.range.last + 1, colorString)
            covered += m.range
        }
    }
    // 4. Single-line strings  "…"  and  '…'
    Regex(""""[^"\\\n]*(?:\\.[^"\\\n]*)*"|'[^'\\\n]*(?:\\.[^'\\\n]*)*'""").findAll(code).forEach { m ->
        if (!isCovered(m.range.first)) {
            spans += Span(m.range.first, m.range.last + 1, colorString)
            covered += m.range
        }
    }
    // 5. Numbers
    Regex("""\b\d+\.?\d*[fFdDlL]?\b""").findAll(code).forEach { m ->
        if (!isCovered(m.range.first)) {
            spans += Span(m.range.first, m.range.last + 1, colorNumber)
            covered += m.range
        }
    }
    // 6. Annotations / decorators  @Word
    Regex("""@\w+""").findAll(code).forEach { m ->
        if (!isCovered(m.range.first)) {
            spans += Span(m.range.first, m.range.last + 1, colorAnnotation)
            covered += m.range
        }
    }
    // 7. Keywords (whole-word)
    Regex("""\b(\w+)\b""").findAll(code).forEach { m ->
        val word = m.groupValues[1]
        if (word in CODE_KEYWORDS && !isCovered(m.range.first)) {
            spans += Span(m.range.first, m.range.last + 1, colorKeyword, bold = true)
            covered += m.range
        }
    }

    spans.sortBy { it.start }

    return buildAnnotatedString {
        var cursor = 0
        for (span in spans) {
            if (span.start > cursor) {
                withStyle(SpanStyle(color = colorSurface)) {
                    append(code.substring(cursor, span.start))
                }
            }
            if (span.start >= cursor) {
                val end = minOf(span.end, code.length)
                withStyle(SpanStyle(
                    color      = span.color,
                    fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle  = if (span.italic) FontStyle.Italic else FontStyle.Normal
                )) { append(code.substring(span.start, end)) }
                cursor = end
            }
        }
        if (cursor < code.length) {
            withStyle(SpanStyle(color = colorSurface)) { append(code.substring(cursor)) }
        }
    }
}

// ── Code block ────────────────────────────────────────────────────────────────

private const val COLLAPSE_THRESHOLD = 25

@Composable
private fun CodeBlock(
    code   : String,
    lang   : String,
    onCopy : () -> Unit,
    onApply: () -> Unit
) {
    val lines     = remember(code) { code.lines() }
    val lineCount = lines.size
    val isLong    = lineCount > COLLAPSE_THRESHOLD
    var expanded  by remember { mutableStateOf(!isLong) }

    val displayCode = remember(code, expanded) {
        if (!expanded) lines.take(COLLAPSE_THRESHOLD).joinToString("\n") else code
    }

    val highlighted  = syntaxHighlight(displayCode, lang)
    val gutterWidth  = if (lineCount >= 100) 38.dp else 28.dp
    val codeBg       = MaterialTheme.colorScheme.surfaceContainerHighest
    val headerBg     = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        lang.lowercase().ifBlank { "code" },
                        style      = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 10.sp
                    )
                }
                Text(
                    "$lineCount lines",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    fontSize = 10.sp
                )
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmallActionButton(Icons.Rounded.ContentCopy, "Copy", onCopy)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable { onApply() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Apply",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 11.sp
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
        }

        // Header / body divider
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
        )

        // ── Code body ─────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().background(codeBg)) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {

                // Line-number gutter
                val displayLines = if (!expanded) minOf(lineCount, COLLAPSE_THRESHOLD) else lineCount
                Column(
                    modifier = Modifier
                        .width(gutterWidth)
                        .background(headerBg.copy(alpha = 0.6f))
                        .padding(top = 12.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    repeat(displayLines) { i ->
                        Text(
                            text       = "${i + 1}",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 11.sp,
                            lineHeight = 19.sp,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier   = Modifier.padding(end = 8.dp)
                        )
                    }
                }

                // Syntax-highlighted code — left border replaces the gutter separator
                SelectionContainer {
                    Text(
                        text       = highlighted,
                        modifier   = Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(0.dp)
                            )
                            .padding(
                                start  = 12.dp,
                                end    = 24.dp,
                                top    = 12.dp,
                                bottom = 12.dp
                            ),
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        lineHeight = 19.sp,
                        softWrap   = false
                    )
                }
            }
        }

        // ── Expand / collapse footer ──────────────────────────────────────────
        if (isLong) {
            val expandIcon = Icons.Rounded.KeyboardArrowDown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (!expanded)
                            Brush.verticalGradient(listOf(codeBg.copy(alpha = 0f), codeBg))
                        else
                            Brush.verticalGradient(listOf(codeBg, codeBg))
                    )
                    .clickable { expanded = !expanded }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    expandIcon, null,
                    modifier = Modifier.size(14.dp).rotate(if (expanded) 180f else 0f),
                    tint     = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (expanded) "Show less"
                    else "Show ${lineCount - COLLAPSE_THRESHOLD} more lines",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Thinking dots ─────────────────────────────────────────────────────────────

@Composable
private fun ThinkingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_dots")
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue  = 0.25f,
                targetValue   = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = index * 180, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "dot_alpha_$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

// ── Streaming cursor ──────────────────────────────────────────────────────────

@Composable
private fun StreamingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Box(
        modifier = Modifier
            .size(width = 2.dp, height = 16.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                shape = RoundedCornerShape(1.dp)
            )
    )
}
