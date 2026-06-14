/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package io.vscodex.ai.ui.components.ai

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.ToastUtils
import com.itsvks.monaco.MonacoEditor
import io.vscodex.ai.app.strings
import io.vscodex.ai.core.ai.Gemini
import io.vscodex.ai.core.ai.OpenRouter
import io.vscodex.ai.ui.screens.editor.components.view.CodeEditorView
import io.vscodex.ai.utils.launchWithProgressDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GenerateContentDialog(
    editor: View,
    modifier: Modifier = Modifier,
    fileExtension: String? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(true) }

    if (!showDialog) return

    AlertDialog(
        modifier = modifier,
        onDismissRequest = { showDialog = false },
        // ── Icon header ────────────────────────────────────────────────────────
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
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
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(strings.generate_code),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (fileExtension != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = ".$fileExtension file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Describe what you want to generate and the AI will write the code for you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(strings.tell_me_what_you_want_to_generate)) },
                    placeholder = { Text("e.g. A function that sorts a list of objects by date") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { showDialog = false }) {
                Text(stringResource(strings.cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (prompt.isNotEmpty()) {
                        scope.launchWithProgressDialog(
                            uiContext = context,
                            configureBuilder = {
                                it.apply {
                                    setMessage(strings.generating_code)
                                    setCancelable(false)
                                }
                            }
                        ) { _, _ ->
                            if (OpenRouter.isConfigured()) {
                                OpenRouter.generateCode(
                                    prompt = prompt,
                                    fileExtension = fileExtension
                                ).onSuccess { text ->
                                    val cleaned = OpenRouter.removeBackticksFromMarkdownCodeBlock(text)
                                    withContext(Dispatchers.Main) {
                                        insertIntoEditor(editor, cleaned)
                                    }
                                }.onFailure { ToastUtils.showShort(it.message) }
                            } else {
                                Gemini.generateCode(
                                    prompt = prompt,
                                    fileExtension = fileExtension
                                ).onSuccess { text ->
                                    val cleaned = Gemini.removeBackticksFromMarkdownCodeBlock(text)
                                    withContext(Dispatchers.Main) {
                                        insertIntoEditor(editor, cleaned)
                                    }
                                }.onFailure { ToastUtils.showShort(it.message) }
                            }
                        }
                        showDialog = false
                    } else {
                        ToastUtils.showShort(context.getString(strings.enter_prompt))
                    }
                },
                enabled = prompt.isNotBlank()
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(strings.generate))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

private fun insertIntoEditor(editor: View, text: String) {
    when (editor) {
        is MonacoEditor    -> editor.insert(text = text, position = editor.position)
        is CodeEditorView  -> {
            val vcEditor = editor.editor
            val cursor   = vcEditor.cursor
            vcEditor.text.insert(cursor.leftLine, cursor.leftColumn, text)
        }
    }
}
