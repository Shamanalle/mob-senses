# Store pages / Страницы на площадках

The Modrinth project page is `docs/modrinth.md`: the whole page in English, with the Russian translation collapsed in a `<details>` block at the end. The *Modrinth page* workflow (`.github/workflows/modrinth-page.yml`) uploads `docs/modrinth.md` and the `**Summary:**` line below to Modrinth on every change pushed to `main` (needs `MODRINTH_TOKEN` with *Write projects* scope), and uploads `docs/hangar.md` to PaperMC Hangar when `"hangar"` in `.github/publish.json` and `HANGAR_TOKEN` (`edit_page` permission) are set. CurseForge cannot be updated via API: open the description editor's source view (`<>`) and paste `docs/curseforge.html` (Mod project) or `docs/curseforge-plugin.html` (Bukkit Plugins project) by hand. Every `<img>` in both CurseForge HTML files lives in `docs/images/curseforge/` (plus `docs/images/store-demo.gif`, 840×472) and is strictly `<= 840 px` wide, since CurseForge rejects any image `>= 850 px` wide. The gallery pictures are listed in `docs/gallery.json` (`banner.png` with `featured: true` first, each under 5 MiB); `NODE_PATH=$(npm root -g) node docs/gallery/make.js` renders the `1920x1080` gallery cards from `docs/images/ui-*.png` via `docs/gallery/page.html`, and `NODE_PATH=$(npm root -g) node docs/gallery/curseforge.js` renders `docs/images/social-preview.png` (`1280x640`), `docs/images/gallery/banner.png` (`1920x1080`), `docs/images/gallery/cover-plugin.png` (`1920x1080`), and all `840 px` CurseForge headings, facts strips and screenshot copies in `docs/images/curseforge/`.

Страница проекта на Modrinth — `docs/modrinth.md`: вся страница на английском, а русский перевод свёрнут в `<details>` в конце. Workflow *Modrinth page* (`.github/workflows/modrinth-page.yml`) выкладывает `docs/modrinth.md` и строку `**Summary:**` ниже на Modrinth при каждом изменении в `main` (нужен секрет `MODRINTH_TOKEN` с правом *Write projects*), а также обновляет страницу на PaperMC Hangar из `docs/hangar.md`, если заданы `"hangar"` в `.github/publish.json` и секрет `HANGAR_TOKEN` (`edit_page`). На CurseForge описание вставляется вручную в режиме исходного кода (`<>`): `docs/curseforge.html` для мода и `docs/curseforge-plugin.html` для плагина в разделе Bukkit Plugins. Все изображения в описаниях CurseForge лежат в `docs/images/curseforge/` (плюс `docs/images/store-demo.gif`, 840×472) и имеют ширину строго `<= 840 px`, так как CurseForge отклоняет картинки шириной `>= 850 px`. Картинки галереи перечислены в `docs/gallery.json` (`banner.png` с `featured: true` первой, каждая меньше 5 МиБ); `NODE_PATH=$(npm root -g) node docs/gallery/make.js` собирает карточки `1920x1080` из `docs/images/ui-*.png`, а `NODE_PATH=$(npm root -g) node docs/gallery/curseforge.js` рендерит `docs/images/social-preview.png` (`1280x640`), `docs/images/gallery/banner.png` (`1920x1080`), `docs/images/gallery/cover-plugin.png` (`1920x1080`) и все `840 px` плашки и копии в `docs/images/curseforge/`.

---

## English

**Name:** Mob Senses: Mobs Hear Sound, Guns, Light & Voice Chat

**Slugs:** Modrinth `mob-senses` · CurseForge Mod `mob-senses` · CurseForge Bukkit Plugin `mob-senses-plugin` · Hangar `mob-senses`

**Summary:** Mobs hear steps, blocks, gunshots with suppressors (TaCZ, Point Blank, CGM) & Simple Voice Chat through walls, spot torchlight vs darkness, and track blood trails. Stealth HUD & sleek 2-tab GUI for Fabric, Forge, NeoForge & Paper.

**Plugin summary:** Server plugin for Paper, Purpur, Folia & Spigot (1.16.5–26.3): mobs hear steps, blocks & Simple Voice Chat through walls, spot torchlight vs darkness and track blood trails — vanilla clients need nothing installed.

**Hangar summary (≤ 120 chars):** Mobs hear steps, blocks & Simple Voice Chat through walls, spot torchlight & track blood trails. Vanilla clients join!

**Categories**

| Site | Main | Additional |
|---|---|---|
| Modrinth (up to 3 shown) | Game Mechanics, Mobs, Adventure | Utility |
| CurseForge, mod | Mobs (main), Adventure and RPG, Server Utility | — |
| CurseForge, plugin (Bukkit Plugins) | Mechanics, Fun, Admin Tools | — |
| PaperMC Hangar | Gameplay | Admin Tools |

**Tags / Keywords:** `mob-ai`, `stealth`, `horror`, `survival`, `tacz`, `gunshots`, `suppressors`, `simple-voice-chat`, `occlusion`, `zombie-awareness`, `sound-physics`, `folia`

**Environment**

| Site | Client | Server |
|---|---|---|
| Modrinth | Optional (singleplayer, Stealth HUD & 2-tab GUI) | Optional (server AI; either or both work) |
| CurseForge (Mod) | Client and Server both ticked | |
| CurseForge (Plugin) / Hangar | Server only (vanilla clients join with nothing installed) | |

**Dependencies (Relations):**
- Fabric and Quilt: `fabric-api` (required); `modmenu` (optional).
- Optional dependencies: `simple-voice-chat`, `timeless-and-classics-zero` (TaCZ), `vics-point-blank`, `mrcrayfishs-gun-mod`, `scorched-guns`, `superb-warfare`, `just-enough-guns`, `sound-physics-remastered`.

**Icon:** `icon.png` in the repository root (512×512), generated by `docs/make-icon.py`.

**Links:** source https://github.com/Shamanalle/mob-senses · issues https://github.com/Shamanalle/mob-senses/issues · changelog https://github.com/Shamanalle/mob-senses/blob/main/CHANGELOG.md · license MIT.

## Русский

**Название:** Mob Senses: Mobs Hear Sound, Guns, Light & Voice Chat

**Краткое описание мода** (на площадках на английском, строка `**Summary:**` выше, укладывается в лимит 250 у CurseForge и 256 у Modrinth). Перевод: Мобы слышат шаги, блоки, выстрелы с глушителями (TaCZ, Point Blank, CGM) и Simple Voice Chat сквозь стены, замечают свет факела и идут по следу крови. Stealth HUD и лаконичное 2-вкладочное GUI для Fabric, Forge, NeoForge и Paper.

**Краткое описание плагина** (строка `**Plugin summary:**` выше для CurseForge Bukkit и SpigotMC; строка `**Hangar summary**` до 120 символов — для PaperMC Hangar). Перевод: Серверный плагин для Paper, Purpur, Folia и Spigot (1.16.5–26.3): мобы слышат шаги, блоки и Simple Voice Chat сквозь стены, видят факел в руке и идут по следу крови — игрокам не нужны моды.

**Категории:** Game Mechanics, Mobs, Adventure, Utility (Hangar: Gameplay, Admin Tools).

**Опциональные зависимости (Relations):** `simple-voice-chat`, `timeless-and-classics-zero` (TaCZ), `vics-point-blank`, `mrcrayfishs-gun-mod`, `scorched-guns`, `superb-warfare`, `just-enough-guns`, `sound-physics-remastered` (+ `modmenu` на Fabric).

**Иконка:** `icon.png` в корне репозитория (512×512).
