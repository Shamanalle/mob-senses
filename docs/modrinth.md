<p align="center"><img src="https://raw.githubusercontent.com/Shamanalle/mob-senses/main/icon.png" width="128" alt="Mob Senses icon"></p>

<h1 align="center">Mob Senses</h1>

<p align="center">Mobs hear your steps, blocks and gunshots, see your light, smell blood trails and, with Simple Voice Chat, hear your voice.<br>A server-side survival, horror and stealth mod for Fabric, Quilt, Forge, NeoForge and Paper.</p>

<p align="center">
<a href="https://github.com/Shamanalle/mob-senses/releases"><img src="https://img.shields.io/github/v/release/Shamanalle/mob-senses?include_prereleases&logo=github&color=brightgreen" alt="Release"></a>
<img src="https://img.shields.io/badge/Minecraft-1.16.5%20%E2%80%93%2026.3-blue.svg" alt="Minecraft 1.16.5 – 26.3">
<img src="https://img.shields.io/badge/loaders-Fabric%20%C2%B7%20Quilt%20%C2%B7%20Forge%20%C2%B7%20NeoForge%20%C2%B7%20Paper-6d4aff.svg" alt="Loaders">
<a href="https://github.com/Shamanalle/mob-senses/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"></a>
</p>

![Mob Senses — hearing, gunshots, voice chat, light and blood trails](https://raw.githubusercontent.com/Shamanalle/mob-senses/main/docs/images/store-demo.gif)

Hostile mobs in vanilla notice you only within a fixed range. With **Mob Senses** they use their senses:

- **Hearing & Wall Occlusion.** Running is loud, sneaking is quiet, wool muffles your steps and gravel gives you away. Breaking and placing blocks, doors, chests, fights, arrows, explosions, bells, note blocks and eating make noise too. A mob that hears it walks to **where the noise came from**, looks around and goes back to its business. Stone walls, rain, thunderstorms and *Feather Falling* / *Swift Sneak* boots muffle noise, glass and wood less, and an open door lets it through.
- **Gun Mods & Suppressors** (**TaCZ**, **Vic's Point Blank**, **MrCrayfish's Gun Mod**, **Scorched Guns**). Unsuppressed gunshots carry far across the area (`44` blocks) and draw nearby mobs, while attaching a suppressor or silencer muffles the shot (`16` blocks). Firing a silenced weapon at a distant wall creates noise where the bullet lands (`20` blocks) so you can distract or lure mobs away.
- **Voice Chat** (with [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)). Mobs hear you talk. A shout carries far, a whisper only next to them; loudness is measured against each player's own normal speaking voice, so a quiet microphone gives no free stealth.
- **Light & Stealth.** A torch or lantern in your hand makes you visible from afar; darkness, sneaking and invisibility potions hide you.
- **Blood Scent & Horde Alerts.** A badly wounded player (at or below half health) leaves a crimson blood trail that zombies and spiders follow, and alerted mobs can optionally call nearby allies to investigate together.
- **Customizable Stealth HUD.** Players who install the mod on the client see a compact indicator of their noise, light exposure, voice activity and blood scent — with adjustable position, opacity, compact mode and auto-hide when quiet.

Works like Zombie Awareness, for Minecraft **1.16.5 – 26.3** across **Fabric, Quilt, Forge, NeoForge, Paper, Purpur, Folia and Spigot**, with no external library mods required.

> **Modpacks:** You are free to include **Mob Senses** in any modpack.

![Mobs hear noise and investigate](https://raw.githubusercontent.com/Shamanalle/mob-senses/main/docs/images/store-hearing.png)

## Quick start

**Server**
1. Fabric, Quilt, Forge or NeoForge: put the mod's `.jar` into `mods/` (on Fabric and Quilt also [Fabric API](https://modrinth.com/mod/fabric-api)). Paper, Purpur, Folia or Spigot: put `mob-senses-bukkit-*.jar` into `plugins/`.
2. Start the server. Players join with any client, vanilla too: they need nothing.
3. Choose how keen the senses are with `/mobsenses preset subtle|default|hardcore`, tune any of the 45+ options in-game with `/mobsenses set`, or edit `mobsenses-server.properties`.

**Singleplayer:** put the same `.jar` into `.minecraft/mods/`.

[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) and gun mods are optional: everything works out of the box and integrates automatically when they are installed.

## What works where

| | Server with the mod | Server without the mod |
|---|---|---|
| **Player without the mod** (vanilla client) | Everything: mobs hear, see and smell | Vanilla |
| **Player with the mod** | Everything, plus the noise and stealth HUD | Vanilla, plus the client noise and light HUD |
| **Singleplayer** | Everything | — |

![Stealth HUD](https://raw.githubusercontent.com/Shamanalle/mob-senses/main/docs/images/store-hud.png)

## Versions and files

| You run | Minecraft | File |
|---|---|---|
| **Fabric, Quilt** | 1.16.5 – 1.21.11, 26.1 – 26.3 | `mob-senses-fabric-…+mc1.16.5-26.x.jar` + [Fabric API](https://modrinth.com/mod/fabric-api) |
| **Forge** | 1.16.5 – 1.21.11, 26.1 – 26.3 | `mob-senses-forge-neoforge-…+mc1.16.5-26.x.jar` |
| **NeoForge** | 1.20.1 – 1.21.11, 26.1 – 26.3 | the same file |
| **Paper, Purpur, Folia, Spigot** | 1.16.5 – 26.3 | `mob-senses-bukkit-….jar` |

One file serves every Minecraft version of its loader: it picks the right code when the game starts.

## Commands

`/mobsenses` is for operators (level 2) and the console, while `/mobsenses hud` works for every player. On Paper the permission is `mobsenses.admin`. Replies come in the player's language (English, Russian, Ukrainian, German, Spanish, Portuguese, Chinese).

| Command | What it does |
|---|---|
| `/mobsenses` or `/mobsenses status` | Version, preset, multipliers, voice chat, how much noise the server heard and how many mobs reacted in the last minute |
| `/mobsenses preset <off\|subtle\|default\|hardcore>` | How keen the senses are; `off` makes mobs behave as in vanilla |
| `/mobsenses hearing <0-3>` | How far mobs hear, times the preset |
| `/mobsenses sight <0-3>` | How far mobs see you, times the preset |
| `/mobsenses voice <on\|off>` | Whether mobs hear Simple Voice Chat |
| `/mobsenses set <option> [value\|default]` | View or change any server setting in-game with Tab-completion |
| `/mobsenses get <option\|category>` | Inspect a single setting or an entire category (`core`, `guns`, `sounds`, `stealth`, `voice`, `sight`, `scent`, `mobs`, `all`) |
| `/mobsenses reset <option\|all>` | Restore any setting or the entire config back to factory defaults |
| `/mobsenses hud <on\|off\|compact\|full\|auto_hide\|position\|opacity\|offset\|bar\|reset>` | Customize the client Stealth HUD directly from chat |
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

![Admin debug traces](https://raw.githubusercontent.com/Shamanalle/mob-senses/main/docs/images/store-debug.png)

## Compatibility

- **Modpacks:** free to include in any modpack; no external library mods required.
- **Vanilla clients** join a server with the mod; the mod does not change the network protocol.
- **Gun mods (TaCZ, Vic's Point Blank, MrCrayfish's Gun Mod, Scorched Guns):** automatic support for unsuppressed shots, suppressors/silencers, distant bullet wall impacts and per-weapon range overrides.
- **Custom and modded mobs:** configure via `extra_listeners`, `ignored_mobs` and `scent_mobs` or the `#mobsenses:listeners` entity type tag.
- **Other AI & sound mods** (In Control!, Enhanced AI, Improved Mobs, Sound Physics Remastered): Mob Senses adds a low-priority investigation goal that yields immediately when a mob enters combat.

---

<details>
<summary><b>🇷🇺 Русский</b></summary>

В обычном Minecraft враждебные мобы замечают вас только на фиксированном расстоянии. С **Mob Senses** у них появляются чувства:

- **Слух и преграды.** Бег громкий, шаг в приседе тихий, шерсть глушит шаги, а гравий выдаёт. Ломание и установка блоков, двери, сундуки, бой, стрелы, взрывы, колокола, нотные блоки и еда тоже шумят. Моб, который это услышал, идёт туда, **откуда шёл звук**, осматривается и возвращается к своим делам. Каменные стены, дождь, гроза и ботинки с «Невесомостью» или «Проворством» глушат шум, стекло и дерево — слабее, а открытая дверь пропускает.
- **Оружейные моды и глушители** (**TaCZ**, **Vic's Point Blank**, **MrCrayfish's Gun Mod**, **Scorched Guns**). Выстрелы без глушителя разносятся далеко (`44` блока) и стягивают мобов, а глушитель снижает слышимость (`16` блоков). Попадание пули в далёкую стену (`20` блоков) создаёт шум в точке удара — выстрелом с глушителем можно отвлечь и выманить мобов.
- **Голос** (с [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)). Мобы слышат, как вы говорите. Крик разносится далеко, шёпот — только рядом; громкость отсчитывается от обычного голоса самого игрока, поэтому тихий микрофон не даёт бесплатной скрытности.
- **Свет и скрытность.** Факел или фонарь в руке делает вас заметным издалека; темнота, присед и зелье невидимости прячут.
- **Запах крови и призыв орды.** Сильно раненый игрок (половина здоровья или меньше) оставляет за собой след крови, по которому идут зомби и пауки, а встревоженные мобы при желании могут звать ближайших сородичей.
- **Индикатор скрытности (HUD).** У игроков с модом на клиенте есть настраиваемый индикатор собственного шума, заметности на свету, голоса и следа крови с компактным режимом и автоскрытием в тишине.

![/mobsenses status](https://raw.githubusercontent.com/Shamanalle/mob-senses/main/docs/images/store-status.png)

</details>

---

<p align="center">
<a href="https://github.com/Shamanalle/mob-senses/issues">Issues</a> ·
<a href="https://github.com/Shamanalle/mob-senses">Source</a> ·
<a href="https://github.com/Shamanalle/mob-senses/blob/main/LICENSE">License: MIT</a>
</p>
