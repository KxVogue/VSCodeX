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

package io.vscodex.ai.terminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import io.vscodex.ai.activities.TerminalActivity
import io.vscodex.ai.app.drawables
import io.vscodex.ai.extensions.makePluralIf
import io.vscodex.ai.terminal.Session
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class TerminalService : Service() {

    private val sessions = hashMapOf<String, TerminalSession>()
    val sessionList = mutableStateListOf<String>()
    var currentSession = mutableStateOf("main")

    private val ACTION_EXIT = "io.vscodex.ai.action.ACTION_EXIT"
    private val NOTIFICATION_ID = 46536745
    private val CHANNEL_ID = "vsx_terminal_channel"

    // ── Binder ────────────────────────────────────────────────────────────────

    inner class TerminalBinder : Binder() {
        val service get() = this@TerminalService

        fun createSession(
            id: String,
            client: TerminalSessionClient,
            activity: TerminalActivity
        ): TerminalSession = Session.createSession(activity, client, id).also { session ->
            sessions[id] = session
            if (id !in sessionList) sessionList.add(id)
            updateNotification()
        }

        fun getSession(id: String): TerminalSession? = sessions[id]

        fun terminateSession(id: String) {
            sessions[id]?.finishIfRunning()
            sessions.remove(id)
            sessionList.remove(id)
            // Do NOT call stopSelf() here — the Activity may still be bound.
            // The service stops naturally when the last client unbinds.
            updateNotification()
        }
    }

    private val binder = TerminalBinder()
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            sessions.values.forEach { it.finishIfRunning() }
            sessions.clear()
            sessionList.clear()
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sessions.values.forEach { it.finishIfRunning() }
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Terminal Sessions", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "VSX terminal session notifications" }
        )
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, TerminalActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitIntent = PendingIntent.getService(
            this, NOTIFICATION_ID,
            Intent(this, TerminalService::class.java).apply { action = ACTION_EXIT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val count = sessions.size
        val contentText = if (count == 0) "No sessions running"
                          else "$count${" session" makePluralIf (count > 1)} running"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VSCodeX — Terminal")
            .setContentText(contentText)
            .setSmallIcon(drawables.terminal)
            .setContentIntent(openIntent)
            .addAction(NotificationCompat.Action.Builder(null, "Exit", exitIntent).build())
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }
}
