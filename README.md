<p align="center"><img src="icon.png" width="128" alt="Mob Senses icon"></p>

<h1 align="center">Mob Senses</h1>

<p align="center"><b>English</b> · <a href="README.ru.md">Русский</a></p>

<p align="center">Mobs hear you, see your light, smell blood trails and, with Simple Voice Chat, hear your voice.<br>A server-side survival and stealth mod for Fabric, Quilt, Forge, NeoForge and Paper.</p>

<p align="center">
<a href="https://github.com/Shamanalle/mob-senses/releases"><img src="https://img.shields.io/github/v/release/Shamanalle/mob-senses?include_prereleases&logo=github&color=brightgreen" alt="Release"></a>
<a href="#versions-and-files"><img src="https://img.shields.io/badge/Minecraft-1.16.5%20%E2%80%93%2026.3-blue.svg" alt="Minecraft 1.16.5 – 26.3"></a>
<a href="#versions-and-files"><img src="https://img.shields.io/badge/loaders-Fabric%20%C2%B7%20Quilt%20%C2%B7%20Forge%20%C2%B7%20NeoForge%20%C2%B7%20Paper-6d4aff.svg" alt="Loaders"></a>
<a href="https://github.com/Shamanalle/mob-senses/actions/workflows/ingame.yml"><img src="https://img.shields.io/badge/in--game_tests-passing-brightgreen.svg" alt="In-game tests"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"></a>
</p>

Hostile mobs in vanilla notice you only within a fixed range. With Mob Senses they also use their senses:

- **Hearing.** Running is loud, sneaking is quiet, wool muffles your steps and gravel gives you away. Breaking and placing blocks, doors, chests, fights, arrows, explosions, bells, note blocks and eating make noise too. A mob that hears it walks to **where the noise came from**, looks around and goes back to its business. Stone walls muffle noise, glass and wood less, an open door lets it through.
- **Voice** (with [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)). Mobs hear you talk. A shout carries far, a whisper only next to them; loudness is measured against each player's own normal voice, so a quiet microphone gives no free stealth.
- **Light and stealth.** A torch or lantern in your hand makes you visible from afar, darkness and sneaking hide you.
- **Scent.** A badly wounded player (at or below half health) leaves a blood trail that zombies and spiders follow.
- **Stealth HUD.** Players with the mod on the client see a compact indicator of their current noise, light exposure, voice activity and blood trail.

It works like Zombie Awareness, for Minecraft 1.16.5 – 26.3 on Forge, NeoForge, Fabric, Quilt and Paper, with walls and voice chat taken into account.

<p align="center"><img src="docs/images/store-hearing.png" width="840" alt="Mobs hear noise and investigate"></p>

## Contents

- [Quick start](#quick-start)
- [What works where](#what-works-where)
- [Versions and files](#versions-and-files)
- [Commands](#commands)
- [Settings file](#settings-file)
- [Compatibility](#compatibility)
- [Questions and problems](#questions-and-problems)
- [Building](#building)

## Quick start

**Server**
1. Fabric, Quilt, Forge or NeoForge: put the mod's `.jar` into `mods/` (on Fabric and Quilt also [Fabric API](https://modrinth.com/mod/fabric-api)). Paper, Purpur, Folia or Spigot: put `mob-senses-bukkit-*.jar` into `plugins/`.
2. Start the server. Players join with any client, vanilla too: they need nothing.
3. Choose how keen the senses are with `/mobsenses preset subtle|default|hardcore`, or in the [settings file](#settings-file).

**Singleplayer**: put the same `.jar` into `.minecraft/mods/`. The world's own server runs it.

[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) is optional: without it everything but the voice works.

## What works where

| | Server with the mod | Server without the mod |
|---|---|---|
| **Player without the mod** (vanilla client) | Everything: mobs hear, see and smell | Vanilla |
| **Player with the mod** | Everything, plus the noise and stealth HUD | Vanilla, plus the client noise and light HUD |
| **Singleplayer** | Everything | — |

The senses live on the server. The client part draws the stealth and noise HUD.

<p align="center"><img src="docs/images/store-hud.png" width="840" alt="Stealth and noise HUD"></p>

## Versions and files

| You run | Minecraft | File |
|---|---|---|
| **Fabric, Quilt** | 1.16.5 – 1.21.11, 26.1 – 26.3 | `mob-senses-fabric-…+mc1.16.5-26.x.jar` + [Fabric API](https://modrinth.com/mod/fabric-api) |
| **Forge** | 1.16.5 – 1.21.11, 26.1 – 26.3 | `mob-senses-forge-neoforge-…+mc1.16.5-26.x.jar` |
| **NeoForge** | 1.20.1 – 1.21.11, 26.1 – 26.3 | the same file |
| **Paper, Purpur, Folia, Spigot** | 1.16.5 – 26.3 | `mob-senses-bukkit-….jar` |

One file serves every Minecraft version of its loader: it picks the right code when the game starts.

Java: what your Minecraft version needs (8 for 1.16.5, 16 for 1.17.1, 17 for 1.18 – 1.20.4, 21 for 1.20.5 – 1.21.11, 25 for 26.x; the plugin runs on Java 8 and newer).

## Commands

`/mobsenses` is for operators (level 2) and the console. On Paper the permission is `mobsenses.admin` (operators by default). Replies come in the player's language.

| Command | What it does |
|---|---|
| `/mobsenses` or `/mobsenses status` | Version, preset, multipliers, voice chat, how much noise the server heard and how many mobs reacted in the last minute |
| `/mobsenses preset <off\|subtle\|default\|hardcore>` | How keen the senses are; `off` makes mobs behave as in vanilla |
| `/mobsenses hearing <0-3>` | How far mobs hear, times the preset |
| `/mobsenses sight <0-3>` | How far mobs see you, times the preset |
| `/mobsenses voice <on\|off>` | Whether mobs hear Simple Voice Chat |
| `/mobsenses debug [on\|off]` | Particle traces for operators showing sound, light and scent paths |
| `/mobsenses world <world\|here> <preset\|inherit>` | A world's own preset (`inherit`: back to the server's) |
| `/mobsenses reload` | Read the settings file again |
| `/mobsenses help` | The list of commands |

| Preset | Hearing | Sight | For |
|---|---|---|---|
| Off | — | — | Vanilla mobs |
| Subtle | ×0.6 | ×0.8 | Builders' servers: mobs notice only real noise |
| Default | ×1 | ×1 | Survival |
| Hardcore | ×1.6 | ×1.3 | Horror and hardcore: every step counts |

<p align="center"><img src="docs/images/store-debug.png" width="840" alt="Debug particle traces"></p>

## Settings file

`config/mobsenses-server.properties` (Fabric, Forge, NeoForge) or `plugins/MobSenses/mobsenses-server.properties` (Paper). It is written with comments on the first start and read again when you save it, without a restart.

| Key | Default | Meaning |
|---|---|---|
| `preset` | `default` | `off`, `subtle`, `default` or `hardcore` |
| `hearing` | `1` | How far mobs hear, times the preset (0 – 3) |
| `sight` | `1` | How far mobs see the player, times the preset (0 – 3) |
| `voice` | `true` | Whether mobs hear players in Simple Voice Chat |
| `language` | `en_us` | Language of replies to the console and to players whose language is unknown |
| `world.<id>.preset` | — | A world's own preset. On the mod loaders the id is the dimension (`world.minecraft\:the_nether.preset=off`, the colon written as `\:`), on Paper the world's folder (`world.world_nether.preset=off`) |

**Client HUD settings (`config/mobsenses-client.properties`)** — written on the client and reloaded live whenever you save the file (no commands or restart needed):

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Show (`true`) or hide (`false`) the Stealth & Noise HUD |
| `position` | `top_left` | `top_left`, `top_right`, `bottom_left`, `bottom_right`, `top_center` or `hotbar` |
| `x`, `y` | `10`, `10` | Pixel offset from the chosen position |
| `opacity` | `100` | HUD opacity in percent (`10` – `100`) |
| `compact` | `false` | `true` shows only the status line without the meter bars |
| `show_noise`, `show_light`, `show_scent` | `true` | Show or hide individual bars when `compact=false` |

## Compatibility

- **Vanilla clients** join a server with the mod; the mod does not change the network protocol.
- **Gun mods (TaCZ / Timeless and Classics Zero, PointBlank, MrCrayfish's Gun Mod, Scorched Guns):** gunshots carry far across the area (`44` blocks) and attract mobs, while guns with a suppressor/silencer attachment are muffled (`16` blocks).
- **Custom and modded mobs:** data packs can add or remove mobs from the `#mobsenses:listeners` entity type tag.
- **Other AI mods** (In Control!, Enhanced AI, Improved Mobs and the like): Mob Senses adds its own low-priority investigation goal and yields immediately when a mob enters combat.
- **Simple Voice Chat**: optional; without it everything but the voice works.

## Questions and problems

- **How can I see what mobs hear?** Run `/mobsenses debug on` as an operator to see particle lines from each sound, light source or blood trail to alerted mobs, and `/mobsenses status` for a 1-minute counter.
- **A bug or an idea?** [Open an issue](https://github.com/Shamanalle/mob-senses/issues).

## Building

JDK 25 (and a JDK 17 for the Java 8 parts, found by Gradle's toolchains): `./gradlew build` puts the three release files into `build/libs/`. [CONTRIBUTING.md](CONTRIBUTING.md) explains where the code lives and how it is tested.

## License

[MIT](LICENSE).
