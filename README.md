# Minecraft AI Companion
### AI Companions - Because everyone deserves in-game help

---

## About
This Minecraft mod allows players, especially new ones, to get real-time assistance. If you are confused about a mechanic or a location, simply ask your "Companion" for advice.
  - Example: If a player is in the Deep Dark, they can ask: "What can I do here?" and the AI will provide useful tips and warnings about the Warden.

## Requirements & Version
* **Game Version:** 1.21.1
* **Loader:** [Fabric Loader](https://fabricmc.net/)
* **Dependencies:** [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
* **Information**: This mod uses a self-hosted-AI. Active Internet connection and API key required (no subscription).
* **API Key**: This Mod requires an API key from [ai.cametendo.org](https://ai.cametendo.org). Steps on how to acquire an API will be listed further down in the README.

## Usage
There are three commands included in this mod:
- `/ai question`: This will let you communicate with the AI directly from the In-Game Chat
- `/ai spawn`: This will spawn an actual entity, your 'Companion' (multiple companions can be spawned). It will follow you around and can be interacted with by right-clicking.
- `/ai kill`: This will kill / remove all companions you spawned
- Note: Depending on the question the AI will take 1 - 3 minutes to respond. Please be patient while your companion "thinks"!

## AI Companion Entity 
The AI Companion doesn't just stand around, he too has his own features:

- `Chat-Window`: Right-clicking the Companion will open a chat window. In this, you will have a chat-interface that keeps your chat until you leave the world.
- `Follows you`: Instead of just standing around, the companion will ffollow you around wherever you go and walk around the world.
- `API-Key-Verification`: The first time you play this mod, you will need to use the companion to enter your API key.


## Installation
1. If not already done, install [Fabric 1.21.1](https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.1/fabric-installer-1.1.1.jar). (Click to download the Fabric Installer instantly)
2. Download the mod from the [Releases](https://github.com/Cametendo/minecraft-ai-companion-EMVs12-Project/releases) Tab.
3. Place the `.jar` file in your `%appdata%/.minecraft/mods` folder.
4. Launch the game in your preferred launcher with the Fabric 1.21.1 Profile ([List of Minecraft Launchers](https://github.com/TayouVR/MinecraftLauncherComparison)) <br>

**IMPORTANT**: This Installation Process is strictly for the offical version of Minecraft (including the official Launcher). We are not not responsible for any issues, data loss, or crashes that may occur when using third-party launchers or unofficial versions of the game. Support is only guaranteed for the official Fabric environment.

## API-Key
This mod requires an API key. To acquire an API key, follow these steps:
1. Create an account on [ai.cametendo.org](https://ai.cametendo.org)
2. Press your user profile (bottom-left corner) and switch to the tab "Account".
3. Find the option "API Keys" and press show. A very long line of dots should appear. This is your API key (hidden by default).
4. Copy it and start your game (or go back to it if it's already open)
5. Use /ai spawn to spawn your companion. Once spawned, right-click him and a new window should appear (this is very important). In the newly appeared text-field, enter your API key and press enter.
6. If everything worked, you can now send messages to the AI. Have fun!

## License & Credits
* **Authors:** [Cametendo](https://www.github.com/Cametendo), [ritonioz](https://www.github.com/ritonioz), [Adam237A](https://www.github.com/Adam237A)
* **License:** CC0 1.0 (Public Domain). Feel free to include this in any modpack! (Credits are appreciated but not required).

NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT
