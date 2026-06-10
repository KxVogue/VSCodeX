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

package io.vscodex.ai.core.ai

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

/**
 * Persists AI chat sessions per file path using SharedPreferences.
 *
 * Each file's history is stored as a JSON array of [PersistedMessage] under
 * a key derived from the file's absolute path.  History is capped at
 * [MAX_MESSAGES] to avoid unbounded growth.
 *
 * Keys use SHA-256 (truncated to 16 hex chars) instead of [String.hashCode]
 * to avoid the ~4-billion-value collision space of a 32-bit hash.
 */
object AiChatHistory {

    private const val PREFS_NAME   = "ai_chat_history"
    private const val MAX_MESSAGES = 80

    private val gson     = Gson()
    private val listType = object : TypeToken<List<PersistedMessage>>() {}.type

    data class PersistedMessage(
        val content:     String,
        val isUser:      Boolean,
        val isError:     Boolean = false,
        val timestampMs: Long    = System.currentTimeMillis()
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Derives a stable, collision-resistant SharedPreferences key from a file
     * path using the first 16 hex characters of its SHA-256 digest.
     *
     * Unlike [String.hashCode] (32-bit int, ~4 B values), this gives a
     * 64-bit keyspace — sufficient to make accidental collisions negligible
     * across typical project sizes.
     */
    private fun keyForFile(filePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(filePath.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "history_${hex.take(16)}"
    }

    fun load(context: Context, filePath: String): List<PersistedMessage> {
        val json = prefs(context).getString(keyForFile(filePath), null) ?: return emptyList()
        return runCatching<List<PersistedMessage>> {
            gson.fromJson(json, listType) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, filePath: String, messages: List<PersistedMessage>) {
        val capped = if (messages.size > MAX_MESSAGES) messages.takeLast(MAX_MESSAGES) else messages
        prefs(context).edit()
            .putString(keyForFile(filePath), gson.toJson(capped))
            .apply()
    }

    fun clear(context: Context, filePath: String) {
        prefs(context).edit().remove(keyForFile(filePath)).apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
