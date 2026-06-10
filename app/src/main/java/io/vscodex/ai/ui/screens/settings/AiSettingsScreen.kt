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

package io.vscodex.ai.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vscodex.ai.app.BaseApplication
import io.vscodex.ai.core.ai.DEFAULT_OPENROUTER_MODEL
import io.vscodex.ai.core.ai.DEFAULT_SYSTEM_PROMPT
import io.vscodex.ai.core.ai.openRouterConfig
import io.vscodex.ai.core.ai.openRouterPrefs
import io.vscodex.ai.core.ai.saveOpenRouterApiKey
import io.vscodex.ai.core.ai.saveOpenRouterModel
import io.vscodex.ai.core.ai.saveOpenRouterSystemPrompt
import io.vscodex.ai.core.settings.Settings
import me.zhanghai.compose.preference.preferenceCategory

/**
 * Available free-tier models via OpenRouter.
 * No tokens needed — all are free to use.
 */
private data class ModelOption(
    val id: String,
    val displayName: String,
    val provider: String,
    val tag: String = "Free"
)

private val SUGGESTED_MODELS = listOf(
    ModelOption("deepseek/deepseek-v4-flash",   "DeepSeek V4 Flash",       "DeepSeek",  "Free · Fast"),
    ModelOption("google/gemini-3.1-flash-lite",  "Gemini 3.1 Flash Lite",   "Google",    "Free · Fast"),
    ModelOption("qwen/qwen3.5-flash-02-23",      "Qwen 3.5 Flash",          "Alibaba",   "Free"),
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

    var keyDraft          by remember { mutableStateOf(initial.apiKey) }
    var modelDraft        by remember { mutableStateOf(initial.model) }
    var systemPromptDraft by remember { mutableStateOf(initial.systemPrompt) }
    var showKey           by remember { mutableStateOf(false) }
    var keySaved          by remember { mutableStateOf(false) }
    var modelSaved        by remember { mutableStateOf(false) }
    var promptSaved       by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Section header ────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "AI Agent",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Powered by OpenRouter — free models, no credit card required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── API Key card ──────────────────────────────────────────────────
        item {
            SettingsCard(icon = Icons.Outlined.Key, title = "API Key") {
                OutlinedTextField(
                    value                = keyDraft,
                    onValueChange        = { keyDraft = it; keySaved = false },
                    modifier             = Modifier.fillMaxWidth(),
                    label                = { Text("OpenRouter API Key") },
                    placeholder          = { Text("sk-or-v1-...") },
                    singleLine           = true,
                    visualTransformation = if (showKey) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape                = RoundedCornerShape(12.dp),
                    trailingIcon         = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Hide" else "Show"
                            )
                        }
                    }
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Button(
                        onClick  = { app.saveOpenRouterApiKey(keyDraft); keySaved = true },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        if (keySaved) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Saved!")
                        } else {
                            Text("Save Key")
                        }
                    }
                    OutlinedButton(
                        onClick = { uriHandler.openUri("https://openrouter.ai/keys") },
                        shape   = RoundedCornerShape(10.dp)
                    ) {
                        Text("Get Free Key")
                    }
                }
            }
        }

        // ── Model picker card ─────────────────────────────────────────────
        item {
            SettingsCard(icon = Icons.Outlined.AutoAwesome, title = "Model") {
                Text(
                    text  = "All models below are free on OpenRouter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                SUGGESTED_MODELS.forEach { model ->
                    val isSelected = modelDraft == model.id
                    ModelChip(
                        model      = model,
                        isSelected = isSelected,
                        onClick    = { modelDraft = model.id; modelSaved = false }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick  = { app.saveOpenRouterModel(modelDraft); modelSaved = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    enabled  = !modelSaved
                ) {
                    if (modelSaved) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Model Saved!")
                    } else {
                        Text("Save Model")
                    }
                }
            }
        }

        // ── System prompt card ────────────────────────────────────────────
        item {
            SettingsCard(icon = Icons.Outlined.Psychology, title = "System Prompt") {
                Text(
                    text  = "Instructions prepended to every AI request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value         = systemPromptDraft,
                    onValueChange = { systemPromptDraft = it; promptSaved = false },
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text("System prompt") },
                    placeholder   = { Text(DEFAULT_SYSTEM_PROMPT) },
                    minLines      = 3,
                    maxLines      = 8,
                    shape         = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = { app.saveOpenRouterSystemPrompt(systemPromptDraft); promptSaved = true },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        enabled  = !promptSaved
                    ) {
                        if (promptSaved) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Saved!")
                        } else {
                            Text("Save Prompt")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            systemPromptDraft = DEFAULT_SYSTEM_PROMPT
                            promptSaved = false
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Reset") }
                }
            }
        }
    }
}

// ── Reusable card shell ───────────────────────────────────────────────────────

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shape          = RoundedCornerShape(16.dp),
        modifier       = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Model selector chip ───────────────────────────────────────────────────────

@Composable
private fun ModelChip(
    model: ModelOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = model.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = model.provider,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label   = { Text(model.tag, fontSize = 10.sp) },
                colors  = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.surfaceContainer
                ),
                border = null,
                modifier = Modifier.height(24.dp)
            )
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
