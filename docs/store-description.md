# Store pages / Страницы на площадках

The projects on Modrinth and CurseForge are created by hand; this page has everything the forms ask for. The Modrinth page (`docs/modrinth.md`) and gallery (`docs/gallery.json` + `docs/images/store-*.png`) are synced automatically by `.github/workflows/modrinth-page.yml`. For CurseForge, paste `docs/curseforge.html` (mod project) or `docs/curseforge-plugin.html` (Bukkit plugin project) into the description editor in source view (`<>`).

Проекты на Modrinth и CurseForge создаются вручную; здесь всё, что спрашивают формы. Страница на Modrinth (`docs/modrinth.md`) и галерея (`docs/gallery.json` + `docs/images/store-*.png`) выкладываются автоматически через `.github/workflows/modrinth-page.yml`. На CurseForge вставьте `docs/curseforge.html` (проект мода) или `docs/curseforge-plugin.html` (проект плагина Bukkit) в редактор описания в режиме исходного кода (`<>`).

---

## English

**Name:** Mob Senses: Mobs Hear Sound, Light & Voice Chat

**Summary:** Mobs hear you: steps, blocks, doors, fights and explosions, muffled by walls. Zombie awareness, stealth and horror survival for Forge, NeoForge, Fabric and Paper. Works server-side; with Simple Voice Chat mobs hear your voice too.

(234 characters: within CurseForge's 250 and Modrinth's 256.)

**Why these words.** Both sites search the name and the summary first, and Modrinth has no free-form tags. People look for this kind of mod with "mobs hear", "zombie awareness", "stealth", "noise", "voice chat" and "horror"; those are in the name, the summary and the first paragraph. The brand comes first and the keywords after a colon, as in "Simple Voice Chat: Voice Physics". Loaders and versions are not allowed in the name, and another mod's name is not used there either: "Zombie Awareness" is a common search phrase, so the summary has "zombie awareness" in lower case as a description, and the page itself honestly says "works like Zombie Awareness, for current versions and more loaders".

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

**Dependencies:** Fabric and Quilt: Fabric API (required). Every loader and the plugin: Simple Voice Chat (optional; mobs hear voices only with it).

**Links:** source https://github.com/Shamanalle/mob-senses · issues https://github.com/Shamanalle/mob-senses/issues · license MIT.

**Upload checklist**
1. Modrinth → *Create a project*, type *Mod*, the name and summary above, slug `mob-senses`. CurseForge → *Minecraft → Mods → Create project*, same name and summary; the plugin: *Minecraft → Bukkit Plugins → Create project*, the same name and the same summary (with "Paper, Purpur, Folia and Spigot" instead of "Forge, NeoForge, Fabric and Paper").
2. License MIT, the links above, the icon, the categories and the environment from the tables.
3. Put the project ids into `.github/publish.json` (`modrinth`, `curseforge`, `curseforge_plugin`), and add the `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` secrets (rights in `CONTRIBUTING.md`).
4. *Actions → Sync Modrinth page → Run workflow* uploads `docs/modrinth.md` and the gallery images from `docs/gallery.json`.
5. *Actions → Publish to Modrinth & CurseForge → Run workflow* with the release tag uploads the files; until then the stores are skipped with a warning.
6. CurseForge has no API for the gallery: add the pictures from `docs/images/store-*.png` in the project's *Images* tab by hand.

## Русский

**Название:** Mob Senses: Mobs Hear Sound, Light & Voice Chat

**Краткое описание** (на площадках только на английском, как выше). Перевод: Мобы вас слышат: шаги, блоки, двери, бой и взрывы, приглушённые стенами. Внимательные зомби, скрытность и хоррор-выживание для Forge, NeoForge, Fabric и Paper. Работает на сервере; с Simple Voice Chat мобы слышат и ваш голос.

**Почему такие слова.** Обе площадки ищут прежде всего по названию и краткому описанию, а у Modrinth нет свободных тегов. Такой мод ищут словами «mobs hear», «zombie awareness», «stealth», «noise», «voice chat» и «horror»; они есть в названии, кратком описании и первом абзаце. Сначала бренд, ключевые слова после двоеточия, как в «Simple Voice Chat: Voice Physics». Загрузчики и версии в названии запрещены, чужой мод в названии тоже не ставим: «Zombie Awareness» — частый запрос, поэтому в кратком описании есть «zombie awareness» строчными как описание, а на самой странице можно честно написать «работает как Zombie Awareness, для современных версий и большего числа загрузчиков».

**Категории**

| Площадка | Основные | Дополнительные |
|---|---|---|
| Modrinth (показываются до 3) | Game Mechanics, Mobs, Adventure | Utility (инструменты для админов) |
| CurseForge, мод | Mobs (основная), Adventure and RPG, Server Utility | — |
| CurseForge, плагин (Bukkit Plugins) | Mechanics, Fun | — |

Сверьте названия в форме: обе площадки время от времени переименовывают категории.

**Иконка:** `icon.png` в корне репозитория (512×512), её рисует `docs/make-icon.py`.

**Окружение**

| Площадка | Клиент | Сервер |
|---|---|---|
| Modrinth | Optional (одиночная игра и HUD скрытности) | Required |
| CurseForge | Отметить и Client, и Server: тот же файл работает в одиночной игре и рисует HUD | |

**Зависимости:** Fabric и Quilt: Fabric API (обязательно). Все загрузчики и плагин: Simple Voice Chat (по желанию; голос мобы слышат только с ним).

**Ссылки:** исходники https://github.com/Shamanalle/mob-senses · ошибки https://github.com/Shamanalle/mob-senses/issues · лицензия MIT.

**Чек-лист загрузки**
1. Modrinth → *Create a project*, тип *Mod*, название и краткое описание выше, slug `mob-senses`. CurseForge → *Minecraft → Mods → Create project*, те же название и описание; плагин: *Minecraft → Bukkit Plugins → Create project*, то же самое название и описание (с «Paper, Purpur, Folia and Spigot» вместо «Forge, NeoForge, Fabric and Paper»).
2. Лицензия MIT, ссылки выше, иконка, категории и окружение из таблиц.
3. Впишите ID проектов в `.github/publish.json` (`modrinth`, `curseforge`, `curseforge_plugin`) и добавьте секреты `MODRINTH_TOKEN` и `CURSEFORGE_TOKEN` (права — в `CONTRIBUTING.md`).
4. *Actions → Sync Modrinth page → Run workflow* загрузит `docs/modrinth.md` и картинки галереи из `docs/gallery.json`.
5. *Actions → Publish to Modrinth & CurseForge → Run workflow* с тегом релиза загрузит файлы; до этого площадки пропускаются с предупреждением.
6. У CurseForge нет API для галереи: картинки из `docs/images/store-*.png` добавляются вручную во вкладке *Images* проекта.
