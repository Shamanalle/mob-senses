# Store pages / Страницы на площадках

The projects on Modrinth and CurseForge are created by hand; this page has everything the forms ask for. The pages themselves (`docs/modrinth.md`, `docs/curseforge.html`, `docs/curseforge-plugin.html`), the gallery and the banner come with the store pages phase, once there are screenshots of mobs reacting; until then a short description is enough.

Проекты на Modrinth и CurseForge создаются вручную; здесь всё, что спрашивают формы. Сами страницы (`docs/modrinth.md`, `docs/curseforge.html`, `docs/curseforge-plugin.html`), галерея и баннер появятся на этапе страниц магазинов, когда будут скриншоты реагирующих мобов; до тех пор хватит короткого описания.

---

## English

**Name:** Mob Senses: Mobs Hear Sound, Light & Voice Chat

**Summary:** Mobs hear you: steps, blocks, doors, fights and explosions, muffled by walls. Zombie awareness, stealth and horror survival for Forge, NeoForge, Fabric and Paper. Works server-side; with Simple Voice Chat mobs hear your voice too.

(234 characters: within CurseForge's 250 and Modrinth's 256.)

**Why these words.** Both sites search the name and the summary first, and Modrinth has no free-form tags. People look for this kind of mod with "mobs hear", "zombie awareness", "stealth", "noise", "voice chat" and "horror"; those are in the name, the summary and the first paragraph. The brand comes first and the keywords after a colon, as in "Simple Voice Chat: Voice Physics". Loaders and versions are not allowed in the name, and another mod's name is not used there either: "Zombie Awareness" is a common search phrase, so the summary has "zombie awareness" in lower case as a description, and the page itself may honestly say "works like Zombie Awareness, for current versions and more loaders".

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
| Modrinth | Optional (singleplayer and, later, the HUD) | Required |
| CurseForge | Client and Server both ticked: the same file runs singleplayer | |

**Dependencies:** Fabric and Quilt: Fabric API (required). Every loader and the plugin: Simple Voice Chat (optional; mobs hear voices only with it).

**Links:** source https://github.com/Shamanalle/mob-senses · issues https://github.com/Shamanalle/mob-senses/issues · license MIT.

**Short description for now** (Modrinth body / CurseForge description, until the full page arrives):

> Mobs hear you. Running is loud, sneaking is quiet, wool muffles your steps and gravel gives you away; breaking blocks, doors, fights and explosions make noise too, and walls muffle it. Mobs walk to where the noise came from, look around and go back to their business. With Simple Voice Chat they hear your voice: a shout carries far, a whisper only up close.
>
> A server-side mod: players need nothing, vanilla clients can join. One file for Fabric and Quilt, one for Forge and NeoForge, a plugin for Paper, Purpur, Folia and Spigot.
>
> **Early development:** the first builds set up commands and settings; mobs start reacting in 0.1.0.

**Upload checklist**
1. Modrinth → *Create a project*, type *Mod*, the name and summary above, slug `mob-senses`. CurseForge → *Minecraft → Mods → Create project*, same name and summary; the plugin: *Minecraft → Bukkit Plugins → Create project*, name `Mob Senses`, the same summary without "Forge, NeoForge, Fabric and".
2. License MIT, the links above, the icon, the categories and the environment from the tables.
3. Put the project ids into `.github/publish.json` (`modrinth`, `curseforge`, `curseforge_plugin`), and add the `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` secrets (rights in `CONTRIBUTING.md`).
4. *Actions → Publish to Modrinth & CurseForge → Run workflow* with the release tag uploads the files; until then the stores are skipped with a warning.
5. CurseForge has no API for the gallery: add the pictures from `docs/images/gallery/` in the project's *Images* tab by hand (once they exist).

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
| Modrinth | Optional (одиночная игра, позже HUD) | Required |
| CurseForge | Отметить и Client, и Server: тот же файл работает в одиночной игре | |

**Зависимости:** Fabric и Quilt: Fabric API (обязательно). Все загрузчики и плагин: Simple Voice Chat (по желанию; голос мобы слышат только с ним).

**Ссылки:** исходники https://github.com/Shamanalle/mob-senses · ошибки https://github.com/Shamanalle/mob-senses/issues · лицензия MIT.

**Короткое описание на время** — текст на английском выше, в блоке цитаты.

**Чек-лист загрузки**
1. Modrinth → *Create a project*, тип *Mod*, название и краткое описание выше, slug `mob-senses`. CurseForge → *Minecraft → Mods → Create project*, те же название и описание; плагин: *Minecraft → Bukkit Plugins → Create project*, название `Mob Senses`, то же описание без «Forge, NeoForge, Fabric and».
2. Лицензия MIT, ссылки выше, иконка, категории и окружение из таблиц.
3. Впишите ID проектов в `.github/publish.json` (`modrinth`, `curseforge`, `curseforge_plugin`) и добавьте секреты `MODRINTH_TOKEN` и `CURSEFORGE_TOKEN` (права — в `CONTRIBUTING.md`).
4. *Actions → Publish to Modrinth & CurseForge → Run workflow* с тегом релиза загрузит файлы; до этого площадки пропускаются с предупреждением.
5. У CurseForge нет API для галереи: картинки из `docs/images/gallery/` добавляются вручную во вкладке *Images* проекта (когда они появятся).
