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

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import io.vscodex.net.activities.TerminalActivity
import io.vscodex.net.ui.virtualkeys.SpecialButton
import io.vscodex.net.ui.virtualkeys.VirtualKeysView
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class TerminalBackend(
    val terminal: TerminalView,
    val activity: TerminalActivity
) : TerminalViewClient, TerminalSessionClient {

    private var currentFontSize: Float = DEFAULT_FONT_SIZE
    private val minFontSize = 8f
    private val maxFontSize = 72f
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val DEFAULT_FONT_SIZE = 23f
        private const val TAG = "TerminalBackend"
    }

    // ── TerminalSessionClient ─────────────────────────────────────────────────

    override fun onTextChanged(changedSession: TerminalSession) {
        terminal.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {
        // Callbacks from libterm can arrive on a background thread — post to main.
        mainHandler.post {
            val binder = activity.terminalBinder ?: return@post
            val currentId = binder.service.currentSession.value
            binder.terminateSession(currentId)
            val remaining = binder.service.sessionList
            if (remaining.isNotEmpty()) {
                changeSession(activity, remaining.last())
            }
            // If no sessions remain, leave the screen as-is (user can press Back).
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ClipboardUtils.copyText("Terminal", text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val clip = ClipboardUtils.getText()?.toString() ?: return
        if (clip.isNotEmpty() && terminal.mEmulator != null) {
            terminal.mEmulator.paste(clip)
        }
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE

    // Log — all params are nullable from the interface; guard with ?: ""
    override fun logError(tag: String?, message: String?)            { Log.e(tag ?: TAG, message ?: "") }
    override fun logWarn(tag: String?, message: String?)             { Log.w(tag ?: TAG, message ?: "") }
    override fun logInfo(tag: String?, message: String?)             { Log.i(tag ?: TAG, message ?: "") }
    override fun logDebug(tag: String?, message: String?)            { Log.d(tag ?: TAG, message ?: "") }
    override fun logVerbose(tag: String?, message: String?)          { Log.v(tag ?: TAG, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: TAG, message ?: "", e)
    }
    override fun logStackTrace(tag: String?, e: Exception?)          { Log.e(tag ?: TAG, "", e) }

    // ── TerminalViewClient ────────────────────────────────────────────────────

    /**
     * Pinch-to-zoom.
     * The Termux TerminalView passes a *multiplicative factor* (e.g. 1.05 or 0.95),
     * NOT an absolute size.  Multiply our tracked size by it, then clamp.
     */
    override fun onScale(scale: Float): Float {
        currentFontSize = (currentFontSize * scale).coerceIn(minFontSize, maxFontSize)
        terminal.setTextSize(currentFontSize.toInt())
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) { showSoftInput() }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        // Process finished: Enter closes the session (mirrors Termux behaviour).
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            val binder = activity.terminalBinder ?: return true
            binder.terminateSession(binder.service.currentSession.value)
            if (binder.service.sessionList.isEmpty()) {
                activity.finish()
            } else {
                changeSession(activity, binder.service.sessionList.last())
            }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    // Virtual key row state
    override fun readControlKey(): Boolean = vkView()?.readSpecialButton(SpecialButton.CTRL, true) == true
    override fun readAltKey(): Boolean    = vkView()?.readSpecialButton(SpecialButton.ALT,  true) == true
    override fun readShiftKey(): Boolean  = vkView()?.readSpecialButton(SpecialButton.SHIFT,true) == true
    override fun readFnKey(): Boolean     = vkView()?.readSpecialButton(SpecialButton.FN,   true) == true

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {
        if (terminal.mEmulator != null) {
            terminal.setTerminalCursorBlinkerState(true, true)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showSoftInput() {
        terminal.requestFocus()
        KeyboardUtils.showSoftInput(terminal)
    }

    private fun vkView(): VirtualKeysView? = (activity as Activity).findViewById(virtualKeysId)
}
