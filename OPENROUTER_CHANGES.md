# OpenRouter Integration — Change Summary

## What was added

VSCodeX now supports **OpenRouter** as an AI provider in addition to the built-in
Gemini model. When an OpenRouter API key is configured, all AI features automatically
route through OpenRouter instead of Gemini.

---

## New / modified files

### New files
| File | Purpose |
|---|---|
| `app/.../core/ai/OpenRouter.kt` | OpenRouter REST API client (mirrors Gemini surface) |
| `app/.../core/ai/OpenRouterPrefs.kt` | Synchronous encrypted prefs helpers for API key + model |
| `app/.../ui/screens/settings/AiSettingsScreen.kt` | Settings screen: key input + model picker |

### Modified files
| File | Change |
|---|---|
| `core/common/.../app/BaseApplication.kt` | *(no change — `encryptedPrefs` already existed)* |
| `app/.../core/settings/Settings.kt` | Added `Settings.AI` object with DataStore keys |
| `app/.../ui/screens/SettingScreens.kt` | Added `SettingScreens.AI` route |
| `app/.../ui/screens/settings/SettingsScreen.kt` | Added "AI / OpenRouter" menu entry + NavHost route |
| `app/.../ui/screens/editor/EditorScreen.kt` | Uses OpenRouter for explainCode / importComponents when key is set; added `String?` state vars |
| `app/.../ui/screens/editor/ai/AiAgentScreen.kt` | Uses OpenRouter for chat when key is set; shows active provider name |
| `app/.../ui/components/ai/GenerateContentDialog.kt` | Uses OpenRouter for generateCode when key is set |
| `app/.../ui/screens/editor/ai/AiResponseSheet.kt` | Added `responseText: String` overload (no GenerateContentResponse needed) |
| `app/.../ui/screens/editor/ai/CodeExplanationSheet.kt` | Added `responseText: String` overload |
| `app/.../ui/screens/editor/ai/ImportComponentsSheet.kt` | Added `responseText: String` overload |

---

## How it works

1. Go to **Settings → AI / OpenRouter**
2. Enter your key from [openrouter.ai/keys](https://openrouter.ai/keys) (free account is enough)
3. Pick a model from the dropdown (all listed models are free-tier)
4. Tap **Save API Key** and **Save Model**

From that point every AI action (AI Agent chat, Explain Code, Generate Code, Import
Components) calls OpenRouter instead of Gemini. The key is stored using
`EncryptedSharedPreferences` (AES-256-GCM) on-device.

To switch back to Gemini, clear the API key field and save.

---

## Recommended free models for coding

| Model ID | Notes |
|---|---|
| `qwen/qwen3-235b-a22b:free` | Best all-round free coding model (default) |
| `deepseek/deepseek-r1:free` | Strong reasoning, great for complex code |
| `deepseek/deepseek-v3-base:free` | Fast and capable |
| `meta-llama/llama-4-maverick:free` | Good general coding |
| `microsoft/phi-4-reasoning:free` | Lightweight reasoning |

Full list: https://openrouter.ai/models?output_modalities=text&q=coding

---

## No build script changes needed

OpenRouter is called via plain `HttpURLConnection` — no new Gradle dependencies required.
