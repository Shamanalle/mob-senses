# Contributing / Участие в разработке

**[English](#english)** · **[Русский](#русский)**

---

## English

### Setup

- **JDK 25**: it builds every module (the older targets are compiled with `--release 8`, `16`, `17` or `21`). Gradle's toolchains find or download the JDK 17 that the Java 8 parts need (`gradle/jabel.gradle`).
- Git.

```bash
git clone https://github.com/Shamanalle/mob-senses.git
cd mob-senses
./gradlew :common:test :packaging:test   # fast: the shared logic, translations, jar merging
./gradlew build                          # every target; the three release files in build/libs/
./gradlew :fabric-1.21:runServer         # or :fabric-26: a dev server with the mod
./gradlew :bukkit:build                  # the Paper / Purpur / Folia / Spigot plugin only
```

### Where code lives

| Directory | Compiled into | Contains |
|---|---|---|
| `common/` | every jar (Java 17 syntax, Java 8 API, no Minecraft classes) | the senses' logic, settings and presets (`ServerSettings`, `Preset`), `/mobsenses` (`AdminCommands`), server texts (`ServerText`), **translations and icon** |
| `common-j8/` | the universal jars and the plugin | the sources of `common/`, compiled to Java 8 bytecode with Jabel |
| `shared/mc-all/` | every Fabric, Forge and NeoForge module | `/mobsenses` on Brigadier (`MobSensesCommand`), client `/mshud` (`ClientHudCommand`), hearing/sight/scent dispatch (`ServerHearing`), wall occlusion (`BlockAcoustics`), investigation AI goal (`InvestigateGoal`), stealth HUD (`StealthHudOverlay`), the SLF4J log |
| `versions/fabric/<band>/` (`1.16`, `1.17`, `1.19`, `1.19.4`, `1.20`, `1.20.6`, `1.21`, `26`) | the Fabric / Quilt jar, one band each; the Gradle project is `:fabric-<band>` | entrypoint, the Mixin into `ServerLevel` (Fabric has no game event of its own), `Worlds`, `PlayerLanguage`, `AdminPermission`, `fabric.mod.json`. *Minecraft 26.x compatibility* (`.github/workflows/compat-26.yml`) checks the 26 band on every 26.x release |
| `versions/forge/<band>/` (`1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.21.1`, `1.21.10`, `1.21.11`, `26`), `versions/neoforge/<band>/` (`1.21`, `1.21.10`, `1.21.11`, `26`) | the Forge / NeoForge jar | entrypoints on Forge's and NeoForge's own `VanillaGameEvent`, `mods.toml` / `neoforge.mods.toml`. Forge `1.18.2 – 1.20.1` runs with SRG names, so those bands are remapped by `reobfJar` |
| `shared/gametest/` | `fabric-1.21`, `fabric-26` (client gametest) | plays singleplayer with the mod, tests mob hearing reactions and `/mobsenses` in two languages, and captures 4K store screenshots |
| `universal/` | the universal jars | the dispatchers that pick the band of the running Minecraft: `BandAdapter` and `BandMixins` (Fabric), `ForgeDispatcher` and `NeoForgeDispatcher` |
| `packaging/` | the build only | `MergeJars`: makes one jar out of the bands' jars (see below) |
| `bukkit/` | own jar (Java 8 bytecode) | the plugin: the server side through the Bukkit/Paper API, with Paper `Pathfinder`/`MobGoals` in `PaperPathfinder` and a pure-Spigot fallback. `src/modern/` holds what needs a newer API (game events since 1.17, Folia's scheduler) and is loaded only where that API exists |

**One file per loader.** Each band is built against its own Minecraft. `MergeJars` puts the common classes into the jar once and moves each band's glue to `com/kasper/mobsenses/b/<band>/`; the dispatchers pick the band when the game starts. A band's Mixin config becomes `mobsenses-<band>.mixins.json` with no mixins of its own: `BandMixins` hands Mixin the band's mixins only on its Minecraft, so a mixin built for another version (or another Java) is never read. `MergeJars` refuses a jar where moved glue would use something package-private of the common code, or where a class is newer than its band's Java.

Rules of thumb:
- Logic that does not need Minecraft goes into `common` and gets a unit test there.
- The world is only touched on the thread that owns it. Audio threads (voice chat) only measure and queue; the server tick acts.
- A failure of the mod never stops the game: catch it and report it with `ModLog.failure`, which logs each kind of problem once.
- The 1.21 module is compiled against 1.21.8 but runs on 1.20.5–1.21.11. Before using a new Minecraft method there, check that its signature is the same across the range, or go through an adapter or reflection, as `AdminPermission` does for `hasPermission`.
- The plugin uses the Bukkit API and isolates Paper `Pathfinder` / `MobGoals` in `PaperPathfinder` with a Spigot fallback, so it runs on Paper, Purpur, Folia, Spigot and Bukkit. Folia's scheduler is used only in `FoliaScheduling`, which is loaded only on Folia.
- Every side works without the other: a server without the client part, a client without the server part.

### Translations

Language files are in `common/src/main/resources/assets/mobsenses/lang/`: en_us, ru_ru, uk_ua, de_de, es_es, pt_br, zh_cn. Add every new key to **all** files. `LangConsistencyTest` fails if a key used in code is missing, if the languages have different keys, or if their placeholders differ. Server replies go through `ServerText` in the player's language.

### Documentation

The README is two files: `README.md` in English and `README.ru.md` in Russian. They have the same sections and are changed together. The README is a reference for players and server owners: what the mod does, how to install it, commands and every setting, without internals. The rest of the documentation (CHANGELOG, this file, the issue and pull request templates) is written in English first, followed by a complete Russian translation.

GitHub release notes are generated from `CHANGELOG.md` by `.github/scripts/release-notes.sh`. When the changelog changes on `main`, the *Sync release notes* workflow updates the notes of releases already published, on GitHub and Modrinth.

The repository description and topics live in `.github/about.json`. The *Repository about* workflow validates the file in pull requests and applies it when it changes on `main`. Applying needs the `REPO_ADMIN_TOKEN` secret, because the built-in workflow token cannot change repository settings.

### In-game tests

*In-game tests* (`.github/workflows/ingame.yml`) runs on every pull request and before every release:
- **Servers:** `.github/scripts/server-smoke.sh` starts Fabric, Forge, NeoForge, Paper and Folia servers with the mod, primes a TNT next to a loaded chunk and runs `/mobsenses` from the console (status, help, presets, a refused value, a typo, debug particles, a world's preset and its undo, reload). It fails on bare text keys, a missing answer, a setting that was not saved, no game event reaching the mod, a failed Mixin, or an exception from the mod. Locally: `bash .github/scripts/server-smoke.sh fabric 1.21.1 build/libs/mob-senses-fabric-*.jar`.
- **Client:** `./gradlew :fabric-26:runClientGameTest` (or `:fabric-1.21`) plays singleplayer with the mod, makes noise and runs `/mobsenses` as the player in English and Russian, and saves screenshots in `build/run/clientGameTest/screenshots/`.
- Logs and screenshots are attached to each run as artifacts.

### Releases

1. Set `mod_version` in `gradle.properties` and add its section to `CHANGELOG.md` (English, then Russian).
2. Merge into `main` and wait for the green build. The release runs the in-game tests again and stops when they fail.
3. *Actions → Publish Release → Run workflow* on `main`, or push the tag `vX.Y.Z`. The workflow builds every jar, creates the tag and the release, and takes the notes from `CHANGELOG.md`.
4. The same run then uploads the files to Modrinth and CurseForge (see below).

### Modrinth and CurseForge

The workflow *Publish to Modrinth & CurseForge* (`.github/workflows/publish.yml`) takes the files of a GitHub release and uploads them:
- **Modrinth:** the mod (one file for Fabric and Quilt, one for Forge and NeoForge) and the Paper / Purpur / Folia / Spigot plugin.
- **CurseForge:** the mod; the plugin goes to its own project in CurseForge's Bukkit Plugins section (`curseforge_plugin`).

The workflow *Sync Modrinth page* (`.github/workflows/modrinth-page.yml`) uploads `docs/modrinth.md` and the gallery images from `docs/gallery.json`.

### Pull requests

1. Branch off `main` (`feature/…`, `fix/…`).
2. Run `./gradlew :common:test :packaging:test` and `./gradlew build`.
3. Describe what changed and how you tested it in game (Minecraft and loader versions; client, server or both).
4. Add an entry to `CHANGELOG.md` under *Unreleased*, in English and in Russian. The changelog becomes the release notes on Modrinth and CurseForge: short plain items about what players and server owners notice, no internals and no advertising tone.

---

## Русский

### Подготовка

- **JDK 25**: им собираются все модули (старые версии компилируются с `--release 8`, `16`, `17` или `21`). JDK 17 для частей на Java 8 (`gradle/jabel.gradle`) Gradle находит или скачивает сам.
- Git.

```bash
git clone https://github.com/Shamanalle/mob-senses.git
cd mob-senses
./gradlew :common:test :packaging:test   # быстро: общая логика, переводы, склейка jar
./gradlew build                          # все версии; три файла релиза в build/libs/
./gradlew :fabric-1.21:runServer         # или :fabric-26: dev-сервер с модом
./gradlew :bukkit:build                  # только плагин для Paper / Purpur / Folia / Spigot
```

### Где какой код

| Папка | Куда попадает | Что внутри |
|---|---|---|
| `common/` | во все jar (синтаксис Java 17, API Java 8, без классов Minecraft) | логика чувств, настройки и пресеты (`ServerSettings`, `Preset`), `/mobsenses` (`AdminCommands`), тексты сервера (`ServerText`), **переводы и иконка** |
| `common-j8/` | в общие jar и плагин | исходники `common/`, собранные в байткод Java 8 через Jabel |
| `shared/mc-all/` | во все модули Fabric, Forge и NeoForge | `/mobsenses` на Brigadier (`MobSensesCommand`), клиентская команда `/mshud` (`ClientHudCommand`), обработка слуха/зрения/запаха (`ServerHearing`), затухание сквозь стены (`BlockAcoustics`), ИИ-цель расследования (`InvestigateGoal`), HUD скрытности (`StealthHudOverlay`), лог через SLF4J |
| `versions/fabric/<диапазон>/` (`1.16`, `1.17`, `1.19`, `1.19.4`, `1.20`, `1.20.6`, `1.21`, `26`) | jar для Fabric / Quilt, по сборке на диапазон; проект Gradle — `:fabric-<диапазон>` | точка входа, Mixin в `ServerLevel` (своего события для игровых событий у Fabric нет), `Worlds`, `PlayerLanguage`, `AdminPermission`, `fabric.mod.json`. *Minecraft 26.x compatibility* (`.github/workflows/compat-26.yml`) проверяет диапазон 26 на каждом релизе 26.x |
| `versions/forge/<диапазон>/` (`1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.21.1`, `1.21.10`, `1.21.11`, `26`), `versions/neoforge/<диапазон>/` (`1.21`, `1.21.10`, `1.21.11`, `26`) | jar для Forge / NeoForge | точки входа на собственном `VanillaGameEvent` Forge и NeoForge, `mods.toml` / `neoforge.mods.toml`. Forge `1.18.2 – 1.20.1` работает с именами SRG, поэтому эти сборки переименовывает `reobfJar` |
| `shared/gametest/` | `fabric-1.21`, `fabric-26` (client gametest) | играет в одиночном мире с модом, проверяет реакцию мобов на шум и `/mobsenses` на двух языках, делает 4K-скриншоты для магазинов |
| `universal/` | в общие jar | диспетчеры, которые выбирают диапазон запущенного Minecraft: `BandAdapter` и `BandMixins` (Fabric), `ForgeDispatcher` и `NeoForgeDispatcher` |
| `packaging/` | только в сборку | `MergeJars`: собирает один jar из jar диапазонов (см. ниже) |
| `bukkit/` | свой jar (байткод Java 8) | плагин: серверная часть через API Bukkit/Paper, с путём через Paper `Pathfinder`/`MobGoals` в `PaperPathfinder` и фоллбэком для Spigot. В `src/modern/` то, чему нужен более новый API (игровые события с 1.17, планировщик Folia); загружается только там, где этот API есть |

**Один файл на загрузчик.** Каждый диапазон собирается против своего Minecraft. `MergeJars` кладёт общие классы в jar один раз, а связующий код каждого диапазона переносит в `com/kasper/mobsenses/b/<диапазон>/`; диспетчеры выбирают диапазон при запуске игры. Конфиг Mixin диапазона становится `mobsenses-<диапазон>.mixins.json` без собственных миксинов: `BandMixins` отдаёт Mixin миксины диапазона только на его Minecraft, поэтому миксин для другой версии (или другой Java) никогда не читается. `MergeJars` откажется собирать jar, если перенесённый код использует что-то package-private из общего кода или если класс новее Java своего диапазона.

Правила:
- Логика, которой не нужен Minecraft, живёт в `common` и покрывается юнит-тестом там же.
- Мир трогаем только из потока, которому он принадлежит. Аудиопотоки (голосовой чат) только измеряют и кладут в очередь; действует тик сервера.
- Сбой мода никогда не останавливает игру: поймайте его и сообщите через `ModLog.failure`, который пишет каждый вид ошибки в лог один раз.
- Модуль 1.21 собирается против 1.21.8, но работает на 1.20.5–1.21.11. Прежде чем использовать там новый метод Minecraft, проверьте, что его сигнатура одинакова во всём диапазоне, или идите через адаптер или рефлексию, как `AdminPermission` для `hasPermission`.
- Плагин использует API Bukkit и изолирует Paper `Pathfinder` / `MobGoals` в `PaperPathfinder` с фоллбэком для Spigot, поэтому работает на Paper, Purpur, Folia, Spigot и Bukkit. Планировщик Folia используется только в `FoliaScheduling`, который загружается только на Folia.
- Каждая сторона работает без другой: сервер без клиентской части, клиент без серверной.

### Переводы

Файлы языков лежат в `common/src/main/resources/assets/mobsenses/lang/`: en_us, ru_ru, uk_ua, de_de, es_es, pt_br, zh_cn. Каждый новый ключ добавляйте во **все** файлы. `LangConsistencyTest` упадёт, если в коде есть ключ без перевода, если наборы ключей в языках различаются или если не совпадают плейсхолдеры. Ответы сервера идут через `ServerText` на языке игрока.

### Документация

README — это два файла: `README.md` на английском и `README.ru.md` на русском. В них одинаковые разделы, и меняются они вместе. README — справочник для игроков и владельцев серверов: что умеет мод, как его поставить, команды и все настройки, без внутреннего устройства. Остальная документация (CHANGELOG, этот файл, шаблоны issue и pull request) пишется сначала на английском, затем идёт полный перевод на русский.

Описания релизов на GitHub собираются из `CHANGELOG.md` скриптом `.github/scripts/release-notes.sh`. Когда журнал изменений меняется в `main`, workflow *Sync release notes* обновляет описания уже вышедших релизов на GitHub и Modrinth.

### Проверки в игре

*In-game tests* (`.github/workflows/ingame.yml`) запускается на каждый pull request и перед каждым релизом:
- **Серверы:** `.github/scripts/server-smoke.sh` запускает серверы Fabric, Forge, NeoForge, Paper и Folia с модом, поджигает TNT у загруженного чанка и выполняет `/mobsenses` из консоли (статус, справка, пресеты, отклонённое значение, опечатка, отладочные частицы, пресет мира и его отмена, перечитывание).
- **Клиент:** `./gradlew :fabric-26:runClientGameTest` (или `:fabric-1.21`) играет в одиночном мире с модом, шумит и выполняет `/mobsenses` от игрока на английском и русском, сохраняя скриншоты в `build/run/clientGameTest/screenshots/`.
- Логи и скриншоты прикладываются к каждому запуску как артефакты.

### Релизы

1. Укажите `mod_version` в `gradle.properties` и добавьте раздел этой версии в `CHANGELOG.md` (сначала английский, затем русский).
2. Слейте изменения в `main` и дождитесь зелёной сборки. Релиз ещё раз запускает проверки в игре и останавливается, если они не прошли.
3. *Actions → Publish Release → Run workflow* на `main` или запушьте тег `vX.Y.Z`. Workflow соберёт все jar, создаст тег и релиз, а описание возьмёт из `CHANGELOG.md`.
4. Затем тот же запуск загрузит файлы на Modrinth и CurseForge (см. ниже).

### Modrinth и CurseForge

Workflow *Publish to Modrinth & CurseForge* (`.github/workflows/publish.yml`) берёт файлы релиза на GitHub и загружает их:
- **Modrinth:** мод (один файл для Fabric и Quilt, один для Forge и NeoForge) и плагин для Paper / Purpur / Folia / Spigot.
- **CurseForge:** мод; плагин идёт в свой проект в разделе Bukkit Plugins (`curseforge_plugin`).

Workflow *Sync Modrinth page* (`.github/workflows/modrinth-page.yml`) выкладывает `docs/modrinth.md` и картинки галереи из `docs/gallery.json`.

### Pull request

1. Создайте ветку от `main` (`feature/…`, `fix/…`).
2. Запустите `./gradlew :common:test :packaging:test` и `./gradlew build`.
3. Опишите, что изменилось и как вы проверяли в игре (версии Minecraft и загрузчика; клиент, сервер или оба).
4. Добавьте запись в `CHANGELOG.md` в раздел *Unreleased* на английском и на русском. Журнал изменений становится описанием версии на Modrinth и CurseForge: короткие простые пункты о том, что заметят игроки и владельцев серверов, без внутреннего устройства и без рекламного тона.
