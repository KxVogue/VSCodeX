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

package io.vscodex.net.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.vscodex.net.app.BaseApplication
import io.vscodex.net.core.ai.DEFAULT_OPENROUTER_MODEL
import io.vscodex.net.core.ai.DEFAULT_SYSTEM_PROMPT
import io.vscodex.net.core.ai.openRouterConfig
import io.vscodex.net.core.ai.openRouterPrefs
import io.vscodex.net.core.ai.saveOpenRouterApiKey
import io.vscodex.net.core.ai.saveOpenRouterModel
import io.vscodex.net.core.ai.saveOpenRouterSystemPrompt
import io.vscodex.net.core.settings.Settings
import me.zhanghai.compose.preference.preferenceCategory

/**
 * Available models via OpenRouter.
 * Updated June 2026.
 */
private val SUGGESTED_MODELS = listOf(
    "deepseek/deepseek-v4-flash"           to "DeepSeek V4 Flash",
    "google/gemini-3.1-flash-lite"         to "Gemini 3.1 Flash Lite",
    "qwen/qwen3.5-flash-02-23"             to "Qwen 3.5 Flash",
    "anthropic/claude-sonnet-4-6"          to "Claude Sonnet 4.6",
    "anthropic/claude-opus-4-6"            to "Claude Opus 4.6",
    "anthropic/claude-haiku-4-5-20251001"  to "Claude Haiku 4.5",
    "anthropic/claude-opus-4-7"            to "Claude Opus 4.7",
    "anthropic/claude-opus-4-8"            to "Claude Opus 4.8",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit
) {
    BackHandler(onBack = onNavigateUp)

    val uriHandler = LocalUriHandler.current
    val app        = BaseApplication.instance
    val initial    = remember { app.openRouterConfig() }

    var keyDraft        by remember { mutableStateOf(initial.apiKey) }
    var modelDraft      by remember { mutableStateOf(initial.model) }
    var systemPromptDraft by remember { mutableStateOf(initial.systemPrompt) }
    var showKey         by remember { mutableStateOf(false) }
    var dropdownOpen    by remember { mutableStateOf(false) }
    var keySaved        by remember { mutableStateOf(false) }
    var modelSaved      by remember { mutableStateOf(false) }
    var promptSaved     by remember { mutableStateOf(false) }

    // AI Rewrite toggle — persisted via DataStore
    var aiRewriteEnabled by Settings.AI.rememberAiRewriteEnabled()

    val backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        preferenceCategory(
            key   = "ai_settings_category",
            title = { Text("AI Agent Configuration") }
        )

        // ── API Key ────────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PreferenceShape.Top)
                    .background(backgroundColor)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("OpenRouter API Key", style = MaterialTheme.typography.labelLarge)
                Text(
                    text  = "Free account at openrouter.ai — no credit card needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value                = keyDraft,
                    onValueChange        = { keyDraft = it; keySaved = false },
                    modifier             = Modifier.fillMaxWidth(),
                    label                = { Text("API Key") },
                    placeholder          = { Text("sk-or-v1-...") },
                    singleLine           = true,
                    visualTransformation = if (showKey) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon         = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Hide" else "Show"
                            )
                        }
                    }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Button(
                        onClick  = { app.saveOpenRouterApiKey(keyDraft); keySaved = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        if (keySaved) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Saved!")
                        } else {
                            Text("Save API Key")
                        }
                    }
                    OutlinedButton(onClick = { uriHandler.openUri("https://openrouter.ai/keys") }) {
                        Text("Get Key")
                    }
                }
            }
        }

        // ── Model picker ───────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PreferenceShape.Middle)
                    .background(backgroundColor)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Model", style = MaterialTheme.typography.labelLarge)
                Text(
                    text  = "\"Auto\" is recommended — OpenRouter picks the best available free model automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(
                    expanded         = dropdownOpen,
                    onExpandedChange = { dropdownOpen = it }
                ) {
                    OutlinedTextField(
                        value         = SUGGESTED_MODELS.firstOrNull { it.first == modelDraft }?.second
                                        ?: modelDraft,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Select model") },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownOpen) }
                    )
                    ExposedDropdownMenu(
                        expanded         = dropdownOpen,
                        onDismissRequest = { dropdownOpen = false }
                    ) {
                        SUGGESTED_MODELS.forEach { (id, label) ->
                            DropdownMenuItem(
                                text    = { Text(label) },
                                onClick = { modelDraft = id; modelSaved = false; dropdownOpen = false }
                            )
                        }
                    }
                }

                Button(
                    onClick  = { app.saveOpenRouterModel(modelDraft); modelSaved = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (modelSaved) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Model Saved!")
                    } else {
                        Text("Save Model")
                    }
                }
            }
        }

        // ── AI Rewrite ─────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PreferenceShape.Middle)
                    .background(backgroundColor)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Rewrite", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text  = if (aiRewriteEnabled)
                                        "Enabled — AI will ask before modifying your file in the editor."
                                    else
                                        "Disabled — AI will not prompt to rewrite files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked         = aiRewriteEnabled,
                        onCheckedChange = { aiRewriteEnabled = it }
                    )
                }
                if (aiRewriteEnabled) {
                    Text(
                        text  = "When enabled, the AI Agent can suggest refactoring or rewriting the currently open file. " +
                                "It will always ask for your confirmation before applying any changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Custom system prompt ───────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PreferenceShape.Bottom)
                    .background(backgroundColor)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Custom System Prompt", style = MaterialTheme.typography.labelLarge)
                Text(
                    text  = "Instructions prepended to every AI request — set your coding style, preferred language, or any persona.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value         = systemPromptDraft,
                    onValueChange = { systemPromptDraft = it; promptSaved = false },
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text("System prompt") },
                    placeholder   = { Text(DEFAULT_SYSTEM_PROMPT) },
                    minLines      = 3,
                    maxLines      = 8
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = { app.saveOpenRouterSystemPrompt(systemPromptDraft); promptSaved = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        if (promptSaved) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Saved!")
                        } else {
                            Text("Save Prompt")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            systemPromptDraft = DEFAULT_SYSTEM_PROMPT
                            promptSaved = false
                        }
                    ) { Text("Reset") }
                }
            }
        }
    }
}
