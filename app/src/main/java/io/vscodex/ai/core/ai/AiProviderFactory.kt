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

/**
 * Single point where the active [AiProvider] is resolved.
 *
 * Call sites should use [active] instead of referencing [OpenRouter] or
 * [Gemini] directly, so that adding a third provider never requires
 * changes in UI code.
 */
object AiProviderFactory {
    /**
     * Returns [OpenRouter] when an API key is configured, [GeminiProvider]
     * otherwise.
     */
    fun active(): AiProvider =
        if (OpenRouter.isConfigured()) OpenRouter else GeminiProvider
}
