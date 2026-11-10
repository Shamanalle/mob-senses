# Store pages / Страницы на площадках

The projects on Modrinth and CurseForge are created by hand; this page has everything the forms ask for. The Modrinth page (`docs/modrinth.md`) and gallery (`docs/gallery.json` + `docs/images/store-*.png`) are synced automatically by `.github/workflows/modrinth-page.yml`. For CurseForge, paste `docs/curseforge.html` (mod project) or `docs/curseforge-plugin.html` (Bukkit plugin project) into the description editor in source view (`<>`).

Проекты на Modrinth и CurseForge создаются вручную; здесь всё, что спрашивают формы. Страница на Modrinth (`docs/modrinth.md`) и галерея (`docs/gallery.json` + `docs/images/store-*.png`) выкладываются автоматически через `.github/workflows/modrinth-page.yml`. На CurseForge вставьте `docs/curseforge.html` (проект мода) или `docs/curseforge-plugin.html` (проект плагина Bukkit) в редактор описания в режиме исходного кода (`<>`).

---

## English

**Name:** Mob Senses: Mobs Hear Sound, Guns, Light & Voice Chat

**Summary:** Mobs hear steps, blocks, TaCZ gunshots (with suppressors!) & Simple Voice Chat through walls, see torchlight and smell blood trails. Zombie awareness, stealth & horror survival for Forge, NeoForge, Fabric & Paper.

(216 characters: within CurseForge's 250 and Modrinth's 256.)

**Why these words.** Both sites search the name and the summary first, and Modrinth has no free-form tags. People look for this kind of mod with "mobs hear", "zombie awareness", "TaCZ", "gunshots", "suppressors", "stealth", "noise", "voice chat", "blood trails" and "horror"; those are in the name, the summary and the first paragraph. The brand comes first and the keywords after a colon, as in "Simple Voice Chat: Voice Physics". Loaders and versions are not allowed in the name, and another mod's name is not used there either: "Zombie Awareness" is a common search phrase, so the summary has "zombie awareness" as a description, and the page itself honestly says "works like Zombie Awareness, for current versions and more loaders".

**Categories**

| Site | Main | Additional |
|---|---|---|
| Modrinth (up to 3 shown) | Game Mechanics, Mobs, Adventure | Utility (server admin tools) |
| CurseForge, mod | Mobs (main), Adventure and RPG, Server Utility | — |
| CurseForge, plugin (Bukkit Plugins) | Mechanics, Fun | — |

Check the names in the form: both sites rename categories from time to time.

**Icon:** `icon.png` in the repository root (512×512), made by `docs/make-icon.py`.

**Environment**

| Site | Client | Server |
|---|---|---|
| Modrinth | Optional (singleplayer and the stealth HUD) | Required |
| CurseForge | Client and Server both ticked: the same file runs singleplayer and the HUD | |

**Dependencies (Relations):**
- Fabric and Quilt: `fabric-api` (required).
- Optional dependencies (attached automatically by `publish.yml` so Mob Senses appears in each project's *Dependents* tab): `simple-voice-chat`, `timeless-and-classics-zero` (TaCZ), `vics-point-blank`, `mrcrayfishs-gun-mod`, `scorched-guns`, `sound-physics-remastered`.

**Links:** source https://github.com/Shamanalle/mob-senses · issues https://github.com/Shamanalle/mob-senses/issues · license MIT.

**Upload checklist**
1. Modrinth → *Create a project*, type *Mod*, the name and summary above, slug `mob-senses`. CurseForge → *Minecraft → Mods → Create project*, same name and summary; the plugin: *Minecraft → Bukkit Plugins → Create project*, the same name and summary (with "Paper, Purpur, Folia and Spigot" instead of "Forge, NeoForge, Fabric and Paper").
2. License MIT, the links above, the icon, the categories and the environment from the tables.
3. Put the project ids into `.github/publish.json` (`modrinth`, `curseforge`, `curseforge_plugin`), and add the `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` secrets (rights in `CONTRIBUTING.md`).
4. *Actions → Sync Modrinth page → Run workflow* uploads `docs/modrinth.md` and the gallery images from `docs/gallery.json`.
5. *Actions → Publish to Modrinth & CurseForge → Run workflow* with the release tag uploads the files; until then the stores are skipped with a warning.
6. CurseForge has no API for the gallery: add the pictures from `docs/images/store-*.png` in the project's *Images* tab by hand.

## Русский

**Название:** Mob Senses: Mobs Hear Sound, Guns, Light & Voice Chat

**Краткое описание** (на площадках только на английском, как выше). Перевод: Мобы слышат шаги, блоки, выстрелы TaCZ (с глушителями!) и Simple Voice Chat сквозь стены, видят свет факелов и чуют след крови. Внимательные зомби, скрытность и хоррор-выживание для Forge, NeoForge, Fabric и Paper.

**Зависимости (Relations):**
- Fabric и Quilt: `fabric-api` (обязательно).
- Опциональные зависимости (автоматически привязываются через `publish.yml`, чтобы Mob Senses отображался во вкладке *Relations → Dependents* у этих модов): `simple-voice-chat`, `timeless-and-classics-zero` (TaCZ), `vics-point-blank`, `mrcrayfishs-gun-mod`, `scorched-guns`, `sound-physics-remastered`.
