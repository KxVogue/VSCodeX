/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package io.vscodex.ai.ui.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.vscodex.ai.app.strings
import io.vscodex.ai.extensions.isNull
import io.vscodex.ai.git.GitManager
import io.vscodex.ai.git.GitViewModel
import io.vscodex.ai.github.auth.Api
import io.vscodex.ai.github.auth.UserInfo
import io.vscodex.ai.resources.R
import io.vscodex.ai.ui.LocalToastHostState
import io.vscodex.ai.ui.rememberSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Status
import java.io.File
import kotlin.time.Duration.Companion.seconds
import io.vscodex.ai.git.GitManager.Companion.instance as git

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCommitSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    gitViewModel: GitViewModel = viewModel(),
    onSuccess: suspend CoroutineScope.() -> Unit = {},
    onFailure: suspend CoroutineScope.(Throwable) -> Unit = {}
) {
    val context = LocalContext.current
    var userInfo: UserInfo? by remember { mutableStateOf(null) }
    var isCredentialError by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        userInfo = Api.getUserInfo()
        if (userInfo.isNull()) isCredentialError = true
    }

    if (isCredentialError) {
        AlertDialog(
            onDismissRequest = { isCredentialError = false },
            title   = { Text(text = stringResource(R.string.credential_error)) },
            text    = { Text(text = stringResource(R.string.credential_error_msg)) },
            confirmButton = {
                TextButton(onClick = { isCredentialError = false }) {
                    Text(stringResource(strings.ok))
                }
            }
        )
    }

    var commitMessage by rememberSaveable { mutableStateOf("") }

    val workingTree by gitViewModel.workingTree.collectAsStateWithLifecycle(context = Dispatchers.IO)
    val changes     by gitViewModel.changes.collectAsStateWithLifecycle(context = Dispatchers.IO)
    val changesToBeCommitted = remember { mutableStateListOf<String>() }

    LaunchedEffect(key1 = true) {
        withContext(Dispatchers.IO) {
            gitViewModel.loadChanges()
            changesToBeCommitted.clear()
            changesToBeCommitted.addAll(changes.fileChanges)
        }
    }

    val gitChangeStats by gitViewModel.changeStats.collectAsStateWithLifecycle(context = Dispatchers.IO)
    var amendCommit by remember { mutableStateOf(false) }
    var signOff     by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        runCatching { git.getLastCommitMessage() }.onFailure { commitMessage = "Initial commit" }
    }
    LaunchedEffect(amendCommit) {
        if (amendCommit) {
            runCatching { git.getLastCommitMessage() }
                .onSuccess { commitMessage = it }
                .onFailure { amendCommit = false }
        }
    }

    val sheetState = rememberSheetState(
        initialValue          = SheetValue.Expanded,
        skipHiddenState       = false,
        skipPartiallyExpanded = true
    )
    val scope          = rememberCoroutineScope()
    val toastHostState = LocalToastHostState.current

    val hide: suspend () -> Unit = remember {
        {
            scope.launch(Dispatchers.Main) { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) onDismissRequest()
            }
        }
    }

    var status: Status? by remember { mutableStateOf(null) }
    LaunchedEffect(key1 = true) {
        status = GitManager.instance.git.status().call()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier         = modifier,
        sheetState       = sheetState,
        dragHandle       = {}
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.commit)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { hide() } }) {
                            Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = null)
                        }
                    }
                )
            },
            modifier            = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Changes to be committed ────────────────────────────────────
                SectionHeader(stringResource(R.string.changes_to_be_committed))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val loadingStats = gitChangeStats.isLoading
                    val stats        = gitChangeStats.changeStats

                    var filesChanged by remember { mutableIntStateOf(stats?.filesChanged ?: 0) }
                    var totalAdd     by remember { mutableIntStateOf(stats?.insertions ?: 0) }
                    @Suppress("CanBeVal")
                    var totalDel     by remember { mutableIntStateOf(stats?.deletions ?: 0) }

                    LaunchedEffect(key1 = true) {
                        status?.let { s ->
                            if (s.added.isEmpty() || workingTree == null) return@LaunchedEffect
                            filesChanged += s.added.size
                            s.added.forEach { path ->
                                runCatching {
                                    totalAdd += File(workingTree, path).readLines().size
                                }.onFailure(::println)
                            }
                        }
                    }

                    Checkbox(checked = true, onCheckedChange = {})
                    Text(
                        text  = if (loadingStats) "…"
                                else "$filesChanged file${if (filesChanged != 1) "s" else ""} changed, " +
                                     "$totalAdd insertion${if (totalAdd != 1) "s" else ""}(+), " +
                                     "$totalDel deletion${if (totalDel != 1) "s" else ""}(-)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Review changes ─────────────────────────────────────────────
                SectionHeader(stringResource(R.string.review_changes))

                if (changes.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        repeat(changes.fileChanges.size) { index ->
                            val change = changes.fileChanges[index]
                            CheckableFileRow(
                                name    = "${gitViewModel.workingTree.value!!.name}/$change",
                                checked = changesToBeCommitted.contains(change),
                                onCheckedChange = { checked ->
                                    if (checked) changesToBeCommitted.add(change)
                                    else changesToBeCommitted.remove(change)
                                }
                            )
                            if (index < changes.fileChanges.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                // ── Options ────────────────────────────────────────────────────
                SectionHeader(stringResource(R.string.options))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    OptionRow(
                        text            = stringResource(R.string.amend_previous_commit),
                        description     = stringResource(R.string.amend_previous_commit_msg),
                        checked         = amendCommit,
                        onCheckedChange = { amendCommit = it }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    OptionRow(
                        text             = stringResource(R.string.sign_off),
                        description      = if (signOff) stringResource(R.string.feature_not_available)
                                           else stringResource(R.string.sign_off_msg),
                        checked          = signOff,
                        descriptionColor = if (signOff) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                        onCheckedChange  = { value ->
                            signOff = value
                            scope.launch(Dispatchers.Main) {
                                delay(1.seconds)
                                signOff = !value
                                toastHostState.showToast(context.getString(R.string.not_yet_implemented))
                            }
                        }
                    )
                }

                // ── Commit message ─────────────────────────────────────────────
                OutlinedTextField(
                    value         = commitMessage,
                    onValueChange = { commitMessage = it },
                    label         = { Text(stringResource(R.string.message)) },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text(stringResource(R.string.enter_commit_message)) },
                    minLines      = 5,
                    shape         = MaterialTheme.shapes.medium
                )

                // ── Commit button ──────────────────────────────────────────────
                Button(
                    onClick  = {
                        scope.launch(Dispatchers.IO) {
                            userInfo?.let { info ->
                                doCommit(
                                    message  = commitMessage,
                                    amend    = amendCommit,
                                    sign     = signOff,
                                    only     = changesToBeCommitted.toTypedArray(),
                                    userInfo = info,
                                    onSuccess = {
                                        scope.launch {
                                            hide()
                                            withContext(Dispatchers.Main.immediate + SupervisorJob()) {
                                                onSuccess()
                                            }
                                        }
                                    },
                                    onFailure = { err ->
                                        scope.launch {
                                            hide()
                                            onFailure(err)
                                        }
                                    }
                                )
                            } ?: run { isCredentialError = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled  = commitMessage.isNotEmpty(),
                    shape    = MaterialTheme.shapes.medium
                ) {
                    Text(
                        stringResource(R.string.commit),
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun CheckableFileRow(
    name: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text       = name.substringAfterLast("/"),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                overflow   = TextOverflow.Ellipsis,
                maxLines   = 1,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text     = name.substringBeforeLast("/"),
                style    = MaterialTheme.typography.labelSmall,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionRow(
    text: String,
    description: String,
    checked: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    ListItem(
        headlineContent  = { Text(text = text, color = textColor) },
        supportingContent = { Text(text = description, color = descriptionColor) },
        trailingContent  = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier
            .padding(0.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

private fun doCommit(
    message: String,
    amend: Boolean,
    sign: Boolean,
    only: Array<String>,
    userInfo: UserInfo,
    onSuccess: () -> Unit = {},
    onFailure: (Throwable) -> Unit = {}
) {
    runCatching {
        git.commit(
            message  = message,
            amend    = amend,
            sign     = sign,
            only     = only,
            userInfo = userInfo
        )
    }.onSuccess { onSuccess() }.onFailure(onFailure)
}
