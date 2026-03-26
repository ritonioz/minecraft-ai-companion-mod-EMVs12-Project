# Minecraft AI Companion
### AI Companions - Because everyone deserves in-game help

---

## About
Minecraft AI Companion adds an AI-powered helper to Minecraft. You can ask questions directly in chat, or spawn a companion entity that follows you and opens its own chat window when you right-click it.

The mod is especially useful for newer players, but it also works as a general in-game assistant depending on the mode you choose.

Examples:
- "What can I do in the Deep Dark?"
- "How do I find Netherite?"
- "What does this structure do?"

## Requirements & Version
* **Minecraft Version:** 1.20.1
* **Java Version:** 17+
* **Loader:** [Fabric Loader](https://fabricmc.net/)
* **Dependency:** [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
* **Internet:** Required when using a remote AI provider
* **AI Backend:** Supports local or self-hosted providers such as Ollama, Open-WebUI, or a custom OpenAI-compatible endpoint

## Features
- Ask questions directly from in-game chat with `/ai question <message>`
- Spawn one or more AI companion entities with `/ai spawn`
- Right-click a companion to open a persistent chat window for the current world session
- Choose between different AI providers:
  Ollama, Open-WebUI, or a custom endpoint
- Fetch available models directly from the configured provider
- Switch between two AI behavior modes:
  `Casual` for a general assistant, or `Minecraft` to restrict answers to Minecraft-related topics
- Change the selected model later without redoing the full setup
- Delete only the stored API key, or reset the full AI configuration
- Stored API keys are saved locally in config files and are encrypted when possible

## Commands
The mod currently includes these commands:

- `/ai question <question>`
  Sends a question to the configured AI provider and returns the answer in chat.
- `/ai spawn`
  Spawns an AI companion entity at your position.
- `/ai kill`
  Removes all spawned AI companions.
- `/ai delete-key`
  Deletes the stored API key but keeps the rest of the provider configuration.
- `/ai delete-config`
  Deletes the full saved AI configuration so setup starts from scratch next time.
- `/ai model`
  Opens the model selection screen.
- `/ai change-mode`
  Opens the mode selection screen.

Note: Response time depends on your provider, model, and hardware. Local models or busy servers may take longer to answer.

## AI Companion Entity
The AI Companion is more than just a decoration:

- `Follow Owner`: The companion follows the player who spawned it.
- `Right-Click Chat`: Interacting with the entity opens the companion chat screen.
- `Persistent Session per World Join`: Companion chat history stays available until you leave the world/server.
- `Invulnerable`: The entity cannot be damaged through normal gameplay.
- `Custom Appearance`: The companion uses a custom player-style model and can temporarily switch appearance through a built-in easter egg.

## First-Time Setup
The first time you use an AI feature, the setup screen opens automatically.

You can configure:
- `Provider Preset`
  Choose `Ollama`, `Open-WebUI`, or `Custom`
- `Base URL`
  Example: `http://localhost:11434` for Ollama
- `API Key`
  Optional for local providers, required for protected remote providers
- `Model`
  Fetch available models from the provider, or enter one manually
- `Advanced API Path`
  Optional custom chat-completions path for OpenAI-compatible endpoints
- `Mode`
  `Casual` or `Minecraft`

After saving, the mod stores the configuration in the `config/` folder and reuses it automatically.

## Provider Notes
- `Ollama`
  Best for fully local use. Usually works with `http://localhost:11434` and supports model fetching from the setup screen.
- `Open-WebUI`
  Works with Open-WebUI-compatible chat completion endpoints and model lists.
- `Custom`
  Use this for any self-hosted or compatible provider with a custom base URL and optional custom API path.

If no custom API path is set, the client automatically tries standard chat-completions endpoints.

## Installation
1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.20.1 if you have not already done so.
2. Download the mod `.jar` from the [Releases](https://github.com/Cametendo/minecraft-ai-companion-EMVs12-Project/releases) page.
3. Place the `.jar` file into your Minecraft `mods` folder.
4. Make sure [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) is installed as well.
5. Start Minecraft with your Fabric 1.20.1 profile.

**Important:** Support is only guaranteed for the official Java Edition + Fabric environment. Third-party launchers or unofficial game versions may work, but are not officially supported.

## API Key
You only need an API key if your chosen AI provider requires one.

If you are using a hosted Open-WebUI instance such as ai.example.org, the general flow is:
1. Create an account on the provider website.
2. Open your account settings and generate or reveal an API key.
3. Copy the key.
4. In Minecraft, use any AI feature such as `/ai question <question>` or `/ai spawn`.
5. When the setup screen opens, paste the API key into the `API Key` field.
6. Choose the correct provider URL and model, then save the configuration.

If your provider does not require a key, you can leave that field empty.

## License & Credits
* **Authors:** [Cametendo](https://www.github.com/Cametendo), [ritonioz](https://www.github.com/ritonioz), [Adam237A](https://www.github.com/Adam237A)
* **License:** CC0 1.0 (Public Domain). Feel free to include this mod in any modpack. Credits are appreciated, but not required.

NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
