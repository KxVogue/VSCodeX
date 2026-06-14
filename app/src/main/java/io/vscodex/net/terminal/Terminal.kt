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

package io.vscodex.net.terminal

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.vscodex.net.activities.TerminalActivity
import io.vscodex.net.activities.TerminalActivity.Companion.KEY_PYTHON_FILE_PATH
import io.vscodex.net.activities.TerminalActivity.Companion.KEY_SHELL_FILE_PATH
import io.vscodex.net.core.settings.Settings
import io.vscodex.net.terminal.service.TerminalService
import io.vscodex.net.ui.virtualkeys.VirtualKeysConstants
import io.vscodex.net.ui.virtualkeys.VirtualKeysInfo
import io.vscodex.net.ui.virtualkeys.VirtualKeysListener
import io.vscodex.net.ui.virtualkeys.VirtualKeysView
import io.vscodex.net.utils.showShortToast
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

// https://github.com/RohitKushvaha01/ReTerminal/blob/main/app/src/main/java/com/rk/terminal/terminal/Terminal.kt

private var terminalView = WeakReference<TerminalView?>(null)
var virtualKeysView = WeakReference<VirtualKeysView?>(null)
var virtualKeysId = View.generateViewId()

/** Next unique session name that doesn't clash with [existing]. */
private fun nextSessionName(existing: List<String>): String {
    var i = 1
    while ("main$i" in existing) i++
    return "main$i"
}

@SuppressLint("MaterialDesignInsteadOrbitDesign")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Terminal(modifier: Modifier = Modifier, terminalActivity: TerminalActivity) {
    val backgroundColor    = MaterialTheme.colorScheme.surface.toArgb()
    val terminalFontSize   by Settings.Terminal.rememberFontSize()
    val terminalFontFamily by Settings.Terminal.rememberFontFamily()
    val foregroundColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val context = LocalContext.current

    Box(modifier = Modifier.imePadding()) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val drawerWidth = (screenWidthDp * 0.84).dp

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sessions",
                                style = MaterialTheme.typography.titleLarge
                            )
                            IconButton(onClick = {
                                val tv = terminalView.get() ?: return@IconButton
                                val binder = terminalActivity.terminalBinder ?: return@IconButton
                                val newId = nextSessionName(binder.service.sessionList)
                                val client = TerminalBackend(tv, terminalActivity)
                                binder.createSession(newId, client, terminalActivity)
                                // Switch to the newly created session immediately
                                changeSession(terminalActivity, newId)
                                scope.launch { drawerState.close() }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "New Session")
                            }
                        }

                        terminalActivity.terminalBinder?.service?.sessionList?.let { sessions ->
                            LazyColumn {
                                items(sessions) { sessionId ->
                                    SelectableCard(
                                        selected = sessionId == terminalActivity.terminalBinder
                                            ?.service?.currentSession?.value,
                                        onSelect = {
                                            changeSession(terminalActivity, sessionId)
                                            scope.launch { drawerState.close() }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = sessionId,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            content = {
                Scaffold(topBar = {
                    TopAppBar(
                        title = { Text(text = "Terminal") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Sessions")
                            }
                        }
                    )
                }) { paddingValues ->
                    Column(modifier = Modifier.padding(paddingValues)) {

                        // ── Terminal view ─────────────────────────────────
                        AndroidView(
                            factory = { ctx ->
                                TerminalView(ctx, null).apply {
                                    terminalView = WeakReference(this)

                                    val client = TerminalBackend(this, terminalActivity)
                                    setTextSize(terminalFontSize.toInt())
                                    setTerminalViewClient(client)

                                    val binder = terminalActivity.terminalBinder!!
                                    val currentId = binder.service.currentSession.value
                                    val session = binder.getSession(currentId)
                                        ?: binder.createSession(currentId, client, terminalActivity)

                                    session.updateTerminalSessionClient(client)
                                    attachSession(session)

                                    setTypeface(
                                        Typeface.createFromAsset(
                                            ctx.assets,
                                            "fonts/$terminalFontFamily.ttf"
                                        )
                                    )

                                    post {
                                        setBackgroundColor(backgroundColor)
                                        keepScreenOn = true
                                        requestFocus()
                                        setFocusableInTouchMode(true)
                                        mEmulator?.mColors?.mCurrentColors?.apply {
                                            set(256, foregroundColor)
                                            set(258, foregroundColor)
                                        }

                                        // Run file-launch intents AFTER the view is ready
                                        val extras = terminalActivity.intent.extras
                                        if (extras?.containsKey(KEY_PYTHON_FILE_PATH) == true) {
                                            terminalActivity.compilePython(this@apply)
                                        }
                                        if (extras?.containsKey(KEY_SHELL_FILE_PATH) == true) {
                                            terminalActivity.runShellScript(this@apply)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            update = { tv -> tv.onScreenUpdated() }
                        )

                        // ── Virtual keys row ──────────────────────────────
                        AndroidView(
                            factory = { ctx ->
                                VirtualKeysView(ctx, null).apply {
                                    virtualKeysView = WeakReference(this)
                                    id = virtualKeysId
                                    virtualKeysViewClient = terminalView.get()
                                        ?.mTermSession?.let { VirtualKeysListener(it) }
                                    buttonTextColor = foregroundColor
                                    setBackgroundColor(backgroundColor)
                                    reload(
                                        VirtualKeysInfo(
                                            VIRTUAL_KEYS,
                                            "",
                                            VirtualKeysConstants.CONTROL_CHARS_ALIASES
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(75.dp)
                        )
                    }
                }
            }
        )
    }

    // Start the terminal service (idempotent if already running)
    LaunchedEffect(Unit) {
        context.startService(Intent(context, TerminalService::class.java))
    }
}

@SuppressLint("MaterialDesignInsteadOrbitDesign")
@Composable
fun SelectableCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                      else         MaterialTheme.colorScheme.surface,
        label = "cardContainerColor"
    )
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                           else          MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 8.dp else 2.dp
        ),
        enabled = enabled,
        onClick = onSelect
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

fun changeSession(terminalActivity: TerminalActivity, sessionId: String) {
    terminalView.get()?.apply {
        val client = TerminalBackend(this, terminalActivity)
        val binder = terminalActivity.terminalBinder ?: return
        val session = binder.getSession(sessionId)
            ?: binder.createSession(sessionId, client, terminalActivity)
        session.updateTerminalSessionClient(client)
        attachSession(session)
        setTerminalViewClient(client)
        post {
            // Resolve theme foreground colour safely
            val typedValue = TypedValue()
            val resolved = context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface,
                typedValue,
                true
            )
            keepScreenOn = true
            requestFocus()
            setFocusableInTouchMode(true)
            if (resolved) {
                mEmulator?.mColors?.mCurrentColors?.apply {
                    set(256, typedValue.data)
                    set(258, typedValue.data)
                }
            }
        }
        virtualKeysView.get()?.virtualKeysViewClient =
            mTermSession?.let { VirtualKeysListener(it) }
    }
    terminalActivity.terminalBinder?.service?.currentSession?.value = sessionId
    showShortToast(terminalActivity, sessionId)
}

/**
 * Virtual key row definition.
 * Row 1: ESC  /  -  HOME  ↑  END  PGUP
 * Row 2: TAB  CTRL  ALT  SHIFT  ←  ↓  →  PGDN
 *
 * SHIFT is now included so readShiftKey() can return true.
 */
const val VIRTUAL_KEYS = (
    "[" +
    "\n  [" +
    "\n    \"ESC\"," +
    "\n    {\"key\": \"/\", \"popup\": \"\\\\\\\\\"}," +
    "\n    {\"key\": \"-\", \"popup\": \"|\"}," +
    "\n    \"HOME\"," +
    "\n    \"UP\"," +
    "\n    \"END\"," +
    "\n    \"PGUP\"" +
    "\n  ]," +
    "\n  [" +
    "\n    \"TAB\"," +
    "\n    \"CTRL\"," +
    "\n    \"ALT\"," +
    "\n    \"SHIFT\"," +
    "\n    \"LEFT\"," +
    "\n    \"DOWN\"," +
    "\n    \"RIGHT\"," +
    "\n    \"PGDN\"" +
    "\n  ]" +
    "\n]"
)
