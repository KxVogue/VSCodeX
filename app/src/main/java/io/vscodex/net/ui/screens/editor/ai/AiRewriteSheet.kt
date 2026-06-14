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

package io.vscodex.net.ui.screens.editor.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom sheet shown when AI Rewrite is triggered.
 *
 * Flow:
 *  1. User selects code (or the full file is used as context).
 *  2. This sheet opens — shows the original code and an instruction text field.
 *  3. User types an instruction (e.g. "add error handling", "refactor to use coroutines").
 *  4. Tap "Rewrite" → AI generates the rewritten code.
 *  5. Rewritten code is shown; user taps "Apply" to replace the editor content, or "Discard".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRewriteSheet(
    originalCode: String,
    isLoading: Boolean,
    rewrittenCode: String?,
    onRewriteRequest: (instruction: String) -> Unit,
    onApply: (newCode: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var instruction by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "AI Rewrite",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                }
            }

            // ── Original code preview ────────────────────────────────────────
            if (rewrittenCode == null) {
                Text(
                    text = "Selected code",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CodePreview(code = originalCode, maxLines = 8)
            }

            // ── Instruction field ───────────────────────────────────────────
            if (rewrittenCode == null) {
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Instruction") },
                    placeholder = { Text("e.g. add null checks, convert to Kotlin DSL…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isLoading
                )

                Button(
                    onClick = { onRewriteRequest(instruction.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = instruction.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Rewriting…")
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rewrite")
                    }
                }
            }

            // ── Rewritten result ─────────────────────────────────────────────
            if (rewrittenCode != null) {
                Text(
                    text = "Rewritten code",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CodePreview(code = rewrittenCode, maxLines = 20)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Discard")
                    }
                    Button(
                        onClick = { onApply(rewrittenCode) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun CodePreview(code: String, maxLines: Int) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(surfaceColor)
            .horizontalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        val displayCode = if (code.lines().size > maxLines)
            code.lines().take(maxLines).joinToString("\n") + "\n…"
        else code

        Text(
            text = displayCode,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
