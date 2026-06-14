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

package io.vscodex.net.core.ai

import io.vscodex.net.app.BaseApplication

private const val PREF_KEY_API_KEY       = "openrouter_api_key"
private const val PREF_KEY_MODEL         = "openrouter_model"
private const val PREF_KEY_SYSTEM_PROMPT = "openrouter_system_prompt"
private const val PREF_KEY_TEMPERATURE   = "openrouter_temperature"
private const val PREF_KEY_MAX_TOKENS    = "openrouter_max_tokens"

/**
 * Default model: DeepSeek V4 Flash via OpenRouter.
 * https://openrouter.ai/deepseek/deepseek-v4-flash
 */
const val DEFAULT_OPENROUTER_MODEL = "deepseek/deepseek-v4-flash"

const val DEFAULT_SYSTEM_PROMPT =
    "You are an expert coding assistant embedded in VSCodeX, a mobile Android IDE. " +
    "Be concise, precise, and always return well-formatted code when relevant."

const val DEFAULT_TEMPERATURE = 0.7f
const val DEFAULT_MAX_TOKENS  = 4096

data class OpenRouterConfig(
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val maxTokens: Int     = DEFAULT_MAX_TOKENS
)

fun BaseApplication.openRouterPrefs(): Pair<String, String> {
    val key   = encryptedPrefs.getString(PREF_KEY_API_KEY, "") ?: ""
    val model = encryptedPrefs.getString(PREF_KEY_MODEL, DEFAULT_OPENROUTER_MODEL)
                ?: DEFAULT_OPENROUTER_MODEL
    return key to model
}

fun BaseApplication.openRouterConfig(): OpenRouterConfig {
    val key    = encryptedPrefs.getString(PREF_KEY_API_KEY, "") ?: ""
    val model  = encryptedPrefs.getString(PREF_KEY_MODEL, DEFAULT_OPENROUTER_MODEL)
                 ?: DEFAULT_OPENROUTER_MODEL
    val prompt = encryptedPrefs.getString(PREF_KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT)
                 ?: DEFAULT_SYSTEM_PROMPT
    val temp   = encryptedPrefs.getFloat(PREF_KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
    val tokens = encryptedPrefs.getInt(PREF_KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS)
    return OpenRouterConfig(key, model, prompt, temp, tokens)
}

fun BaseApplication.saveOpenRouterApiKey(key: String) {
    encryptedPrefs.edit().putString(PREF_KEY_API_KEY, key.trim()).apply()
}

fun BaseApplication.saveOpenRouterModel(model: String) {
    encryptedPrefs.edit().putString(PREF_KEY_MODEL, model.trim()).apply()
}

fun BaseApplication.saveOpenRouterSystemPrompt(prompt: String) {
    encryptedPrefs.edit().putString(PREF_KEY_SYSTEM_PROMPT, prompt.trim()).apply()
}

fun BaseApplication.saveOpenRouterTemperature(temperature: Float) {
    encryptedPrefs.edit().putFloat(PREF_KEY_TEMPERATURE, temperature.coerceIn(0f, 2f)).apply()
}

fun BaseApplication.saveOpenRouterMaxTokens(maxTokens: Int) {
    encryptedPrefs.edit().putInt(PREF_KEY_MAX_TOKENS, maxTokens.coerceIn(256, 8192)).apply()
}
