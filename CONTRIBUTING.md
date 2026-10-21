# Contributing / Участие в разработке

**[English](#english)** · **[Русский](#русский)**

---

## English

### Setup

- **JDK 25**: it builds every module (the older targets are compiled with `--release 8`, `17` or `21`). Gradle's toolchains find or download the JDK 17 that the Java 8 parts need (`gradle/jabel.gradle`).
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
| `shared/mc-all/` | every Fabric, Forge and NeoForge module | `/mobsenses` on Brigadier (`MobSensesCommand`), the SLF4J log |
| `versions/fabric/<band>/` (`1.21`, `26`) | the Fabric / Quilt jar, one band each; the Gradle project is `:fabric-<band>` | entrypoint, the Mixin into `ServerLevel.gameEvent` (Fabric has no game event of its own), `Worlds`, `PlayerLanguage`, `AdminPermission`, `fabric.mod.json`. *Minecraft 26.x compatibility* (`.github/workflows/compat-26.yml`) checks the 26 band on every 26.x release |
| `versions/forge/1.20.1/`, `versions/neoforge/1.21/` | the Forge / NeoForge jar | entrypoints on Forge's and NeoForge's own `VanillaGameEvent`, `mods.toml` / `neoforge.mods.toml`. Forge 1.20.1 runs with SRG names, so that band is remapped by `reobfJar` |
| `shared/gametest/` | `fabric-1.21`, `fabric-26` (client gametest) | plays singleplayer with the mod and runs `/mobsenses` in two languages |
| `universal/` | the universal jars | the dispatchers that pick the band of the running Minecraft: `BandAdapter` and `BandMixins` (Fabric), `ForgeDispatcher` and `NeoForgeDispatcher` |
| `packaging/` | the build only | `MergeJars`: makes one jar out of the bands' jars (see below) |
| `bukkit/` | own jar (Java 8 bytecode) | the plugin: the server side through the Bukkit API only. `src/modern/` holds what needs a newer API (game events since 1.17, Folia's scheduler) and is loaded only where that API exists |

**One file per loader.** Each band is built against its own Minecraft. `MergeJars` puts the common classes into the jar once and moves each band's glue to `com/kasper/mobsenses/b/<band>/`; the dispatchers pick the band when the game starts. A band's Mixin config becomes `mobsenses-<band>.mixins.json` with no mixins of its own: `BandMixins` hands Mixin the band's mixins only on its Minecraft, so a mixin built for another version (or another Java) is never read. `MergeJars` refuses a jar where moved glue would use something package-private of the common code, or where a class is newer than its band's Java.

Rules of thumb:
- Logic that does not need Minecraft goes into `common` and gets a unit test there.
- The world is only touched on the thread that owns it. Audio threads (voice chat, later) only measure and queue; the server tick acts.
- A failure of the mod never stops the game: catch it and report it with `ModLog.failure`, which logs each kind of problem once.
- The 1.21 module is compiled against 1.21.8 but runs on 1.21–1.21.11. Before using a new Minecraft method there, check that its signature is the same on every 1.21.x release (for example with the Yarn mappings of each version), or go through an adapter or reflection, as `AdminPermission` does for `hasPermission`. Known unstable ones: `CommandSourceStack.hasPermission(int)` (1.21.11), `Entity.level()` and `Entity.position()` (1.21.9), `Camera.getPosition()` (1.21.11), `GuiGraphics.drawString` (1.21.6), the `KeyMapping` constructor (1.21.9, 1.21.11). NeoForge and Forge from 1.20.5 on run with Mojang's names, so there the 1.21.11 rename of `ResourceLocation` to `Identifier` matters too.
- The plugin uses only the Bukkit API (no Paper-only classes, no NMS), so it runs on Paper, Purpur, Spigot and Bukkit. Folia's scheduler is used only in `FoliaScheduling`, which is loaded only on Folia.
- Every side works without the other: a server without the client part, a client without the server part.

### Translations

Language files are in `common/src/main/resources/assets/mobsenses/lang/`: en_us, ru_ru, uk_ua, de_de, es_es, pt_br, zh_cn. Add every new key to **all** files. `LangConsistencyTest` fails if a key used in code is missing, if the languages have different keys, or if their placeholders differ. Server replies go through `ServerText` in the player's language.

### Documentation

The README is two files: `README.md` in English and `README.ru.md` in Russian. They have the same sections and are changed together. The README is a reference for players and server owners: what the mod does, how to install it, commands and every setting, without internals. The rest of the documentation (CHANGELOG, this file, the issue and pull request templates) is written in English first, followed by a complete Russian translation.

GitHub release notes are generated from `CHANGELOG.md` by `.github/scripts/release-notes.sh`. When the changelog changes on `main`, the *Sync release notes* workflow updates the notes of releases already published, on GitHub and Modrinth.

The repository description and topics live in `.github/about.json`. The *Repository about* workflow validates the file in pull requests and applies it when it changes on `main`. Applying needs the `REPO_ADMIN_TOKEN` secret, because the built-in workflow token cannot change repository settings. It is set up once:
1. GitHub → *Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token*.
2. *Repository access*: *Only select repositories* → this repository. *Permissions → Repository permissions → Administration*: *Read and write*. Nothing else is needed.
3. In this repository: *Settings → Secrets and variables → Actions → New repository secret*, name `REPO_ADMIN_TOKEN`, value: the token.
4. Run *Actions → Repository about → Run workflow* once, or change `.github/about.json`. When the token expires, generate a new one and update the secret.

### In-game tests

*In-game tests* (`.github/workflows/ingame.yml`) runs on every pull request and before every release:
- **Servers:** `.github/scripts/server-smoke.sh` starts Fabric, Forge, NeoForge, Paper and Folia servers with the mod, primes a TNT next to a loaded chunk and runs `/mobsenses` from the console (status, help, presets, a refused value, a typo, a world's preset and its undo, reload). It fails on bare text keys, a missing answer, a setting that was not saved, no game event reaching the mod, a failed Mixin, or an exception from the mod. Locally: `bash .github/scripts/server-smoke.sh fabric 1.21.1 build/libs/mob-senses-fabric-*.jar`.
- **Client:** `./gradlew :fabric-26:runClientGameTest` (or `:fabric-1.21`) plays singleplayer with the mod, makes noise and runs `/mobsenses` as the player in English and Russian, and saves screenshots in `build/run/clientGameTest/screenshots/`. CI runs it under `xvfb-run` with Mesa's software OpenGL and Vulkan; the 1.21 client runs on Java 21.
- Logs and screenshots are attached to each run as artifacts.
- **Network failures:** a download that fails on the way (a timeout, a reset connection, HTTP 429 / 5xx) is tried once more and leaves a warning on the run: the build and the client before the game starts (`.github/scripts/net-retry.sh`, the second time with Maven Central through Google's mirror), the server's installer and first start. A failing test, a compile error and a missing file (404) fail at once.

### Releases

1. Set `mod_version` in `gradle.properties` and add its section to `CHANGELOG.md` (English, then Russian).
2. Merge into `main` and wait for the green build. The release runs the in-game tests again and stops when they fail.
3. *Actions → Publish Release → Run workflow* on `main`, or push the tag `vX.Y.Z`. The workflow builds every jar, creates the tag and the release, and takes the notes from `CHANGELOG.md`.
4. The same run then uploads the files to Modrinth and CurseForge (see below). To upload an existing release again: *Actions → Publish to Modrinth & CurseForge → Run workflow* with its tag.

### Modrinth and CurseForge

The workflow *Publish to Modrinth & CurseForge* (`.github/workflows/publish.yml`) takes the files of a GitHub release and uploads them:
- **Modrinth:** the mod (one file for Fabric and Quilt, one for Forge and NeoForge) and the Paper / Purpur / Folia / Spigot plugin.
- **CurseForge:** the mod; the plugin goes to its own project in CurseForge's Bukkit Plugins section (`curseforge_plugin`).

A store is skipped with a warning while its project id or token is missing. Setup, once:
1. Create the projects by hand: Modrinth → *Create a project* (type *Mod*); CurseForge → *Minecraft → Mods → Create project*, and for the plugin *Minecraft → Bukkit Plugins → Create project*. Both sites review new projects; the first upload can go into the project while it waits. Name, summary, categories and the icon are in `docs/store-description.md`.
2. Put the project ids into `.github/publish.json`: on Modrinth the *Project ID* from the project's menu, on CurseForge the *Project ID* number on the project page.
3. Tokens: Modrinth → *Settings → Personal access tokens*, scopes *Create versions*, *Read projects*, *Write versions* (lets *Sync release notes* update the changelog of Modrinth versions) and *Write projects* (lets the Modrinth page and gallery be uploaded); CurseForge → *Account → API tokens*. Add them as repository secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` (*Settings → Secrets and variables → Actions*). Tokens never go into files or chats.

### Pull requests

1. Branch off `main` (`feature/…`, `fix/…`).
2. Run `./gradlew :common:test :packaging:test` and `./gradlew build`.
3. Describe what changed and how you tested it in game (Minecraft and loader versions; client, server or both).
4. Add an entry to `CHANGELOG.md` under *Unreleased*, in English and in Russian. The changelog becomes the release notes on Modrinth and CurseForge: short plain items about what players and server owners notice, no internals and no advertising tone.

---

## Русский

### Подготовка

- **JDK 25**: им собираются все модули (старые версии компилируются с `--release 8`, `17` или `21`). JDK 17 для частей на Java 8 (`gradle/jabel.gradle`) Gradle находит или скачивает сам.
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
| `shared/mc-all/` | во все модули Fabric, Forge и NeoForge | `/mobsenses` на Brigadier (`MobSensesCommand`), лог через SLF4J |
| `versions/fabric/<диапазон>/` (`1.21`, `26`) | jar для Fabric / Quilt, по сборке на диапазон; проект Gradle — `:fabric-<диапазон>` | точка входа, Mixin в `ServerLevel.gameEvent` (своего события для игровых событий у Fabric нет), `Worlds`, `PlayerLanguage`, `AdminPermission`, `fabric.mod.json`. *Minecraft 26.x compatibility* (`.github/workflows/compat-26.yml`) проверяет диапазон 26 на каждом релизе 26.x |
| `versions/forge/1.20.1/`, `versions/neoforge/1.21/` | jar для Forge / NeoForge | точки входа на собственном `VanillaGameEvent` Forge и NeoForge, `mods.toml` / `neoforge.mods.toml`. Forge 1.20.1 работает с именами SRG, поэтому эту сборку переименовывает `reobfJar` |
| `shared/gametest/` | `fabric-1.21`, `fabric-26` (client gametest) | играет в одиночном мире с модом и выполняет `/mobsenses` на двух языках |
| `universal/` | в общие jar | диспетчеры, которые выбирают диапазон запущенного Minecraft: `BandAdapter` и `BandMixins` (Fabric), `ForgeDispatcher` и `NeoForgeDispatcher` |
| `packaging/` | только в сборку | `MergeJars`: собирает один jar из jar диапазонов (см. ниже) |
| `bukkit/` | свой jar (байткод Java 8) | плагин: серверная часть только через API Bukkit. В `src/modern/` то, чему нужен более новый API (игровые события с 1.17, планировщик Folia); загружается только там, где этот API есть |

**Один файл на загрузчик.** Каждый диапазон собирается против своего Minecraft. `MergeJars` кладёт общие классы в jar один раз, а связующий код каждого диапазона переносит в `com/kasper/mobsenses/b/<диапазон>/`; диспетчеры выбирают диапазон при запуске игры. Конфиг Mixin диапазона становится `mobsenses-<диапазон>.mixins.json` без собственных миксинов: `BandMixins` отдаёт Mixin миксины диапазона только на его Minecraft, поэтому миксин для другой версии (или другой Java) никогда не читается. `MergeJars` откажется собирать jar, если перенесённый код использует что-то package-private из общего кода или если класс новее Java своего диапазона.

Правила:
- Логика, которой не нужен Minecraft, живёт в `common` и покрывается юнит-тестом там же.
- Мир трогаем только из потока, которому он принадлежит. Аудиопотоки (голосовой чат, позже) только измеряют и кладут в очередь; действует тик сервера.
- Сбой мода никогда не останавливает игру: поймайте его и сообщите через `ModLog.failure`, который пишет каждый вид ошибки в лог один раз.
- Модуль 1.21 собирается против 1.21.8, но работает на 1.21–1.21.11. Прежде чем использовать там новый метод Minecraft, проверьте, что его сигнатура одинакова во всех 1.21.x (например, по маппингам Yarn каждой версии), или идите через адаптер или рефлексию, как `AdminPermission` для `hasPermission`. Известные нестабильные: `CommandSourceStack.hasPermission(int)` (1.21.11), `Entity.level()` и `Entity.position()` (1.21.9), `Camera.getPosition()` (1.21.11), `GuiGraphics.drawString` (1.21.6), конструктор `KeyMapping` (1.21.9, 1.21.11). NeoForge и Forge с 1.20.5 работают с именами Mojang, так что там важно ещё переименование `ResourceLocation` в `Identifier` в 1.21.11.
- Плагин использует только API Bukkit (без классов, которые есть только в Paper, и без NMS), поэтому работает на Paper, Purpur, Spigot и Bukkit. Планировщик Folia используется только в `FoliaScheduling`, который загружается только на Folia.
- Каждая сторона работает без другой: сервер без клиентской части, клиент без серверной.

### Переводы

Файлы языков лежат в `common/src/main/resources/assets/mobsenses/lang/`: en_us, ru_ru, uk_ua, de_de, es_es, pt_br, zh_cn. Каждый новый ключ добавляйте во **все** файлы. `LangConsistencyTest` упадёт, если в коде есть ключ без перевода, если наборы ключей в языках различаются или если не совпадают плейсхолдеры. Ответы сервера идут через `ServerText` на языке игрока.

### Документация

README — это два файла: `README.md` на английском и `README.ru.md` на русском. В них одинаковые разделы, и меняются они вместе. README — справочник для игроков и владельцев серверов: что умеет мод, как его поставить, команды и все настройки, без внутреннего устройства. Остальная документация (CHANGELOG, этот файл, шаблоны issue и pull request) пишется сначала на английском, затем идёт полный перевод на русский.

Описания релизов на GitHub собираются из `CHANGELOG.md` скриптом `.github/scripts/release-notes.sh`. Когда журнал изменений меняется в `main`, workflow *Sync release notes* обновляет описания уже вышедших релизов на GitHub и Modrinth.

Описание и темы (topics) репозитория лежат в `.github/about.json`. Workflow *Repository about* проверяет этот файл в pull request и применяет его, когда файл меняется в `main`. Для применения нужен секрет `REPO_ADMIN_TOKEN`, потому что встроенный токен workflow не может менять настройки репозитория. Настраивается один раз:
1. GitHub → *Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token*.
2. *Repository access*: *Only select repositories* → этот репозиторий. *Permissions → Repository permissions → Administration*: *Read and write*. Больше ничего не нужно.
3. В этом репозитории: *Settings → Secrets and variables → Actions → New repository secret*, имя `REPO_ADMIN_TOKEN`, значение — токен.
4. Один раз запустите *Actions → Repository about → Run workflow* или измените `.github/about.json`. Когда срок токена истечёт, создайте новый и обновите секрет.

### Проверки в игре

*In-game tests* (`.github/workflows/ingame.yml`) запускается на каждый pull request и перед каждым релизом:
- **Серверы:** `.github/scripts/server-smoke.sh` запускает серверы Fabric, Forge, NeoForge, Paper и Folia с модом, поджигает TNT у загруженного чанка и выполняет `/mobsenses` из консоли (статус, справка, пресеты, отклонённое значение, опечатка, пресет мира и его отмена, перечитывание). Падает, если ответ — голые ключи текстов, нужного ответа нет, настройка не сохранилась, ни одно игровое событие не дошло до мода, Mixin не применился или мод бросил исключение. Локально: `bash .github/scripts/server-smoke.sh fabric 1.21.1 build/libs/mob-senses-fabric-*.jar`.
- **Клиент:** `./gradlew :fabric-26:runClientGameTest` (или `:fabric-1.21`) играет в одиночном мире с модом, шумит и выполняет `/mobsenses` от игрока на английском и русском, сохраняя скриншоты в `build/run/clientGameTest/screenshots/`. В CI тест идёт под `xvfb-run` с программными OpenGL и Vulkan из Mesa; клиент 1.21 запускается на Java 21.
- Логи и скриншоты прикладываются к каждому запуску как артефакты.
- **Сбои сети:** загрузка, оборвавшаяся по дороге (тайм-аут, сброс соединения, HTTP 429 / 5xx), повторяется один раз, и в запуске остаётся предупреждение: сборка и клиент до старта игры (`.github/scripts/net-retry.sh`, во второй раз Maven Central берётся с зеркала Google), установщик сервера и его первый запуск. Упавший тест, ошибка компиляции и отсутствующий файл (404) валят задание сразу.

### Релизы

1. Укажите `mod_version` в `gradle.properties` и добавьте раздел этой версии в `CHANGELOG.md` (сначала английский, затем русский).
2. Слейте изменения в `main` и дождитесь зелёной сборки. Релиз ещё раз запускает проверки в игре и останавливается, если они не прошли.
3. *Actions → Publish Release → Run workflow* на `main` или запушьте тег `vX.Y.Z`. Workflow соберёт все jar, создаст тег и релиз, а описание возьмёт из `CHANGELOG.md`.
4. Затем тот же запуск загрузит файлы на Modrinth и CurseForge (см. ниже). Чтобы загрузить уже вышедший релиз заново: *Actions → Publish to Modrinth & CurseForge → Run workflow* с его тегом.

### Modrinth и CurseForge

Workflow *Publish to Modrinth & CurseForge* (`.github/workflows/publish.yml`) берёт файлы релиза на GitHub и загружает их:
- **Modrinth:** мод (один файл для Fabric и Quilt, один для Forge и NeoForge) и плагин для Paper / Purpur / Folia / Spigot.
- **CurseForge:** мод; плагин идёт в свой проект в разделе Bukkit Plugins (`curseforge_plugin`).

Площадка пропускается с предупреждением, пока для неё нет ID проекта или токена. Настраивается один раз:
1. Создайте проекты вручную: Modrinth → *Create a project* (тип *Mod*); CurseForge → *Minecraft → Mods → Create project*, а для плагина *Minecraft → Bukkit Plugins → Create project*. Оба сайта проверяют новые проекты; первую загрузку можно сделать, пока проект ждёт проверки. Название, краткое описание, категории и иконка — в `docs/store-description.md`.
2. Впишите ID проектов в `.github/publish.json`: на Modrinth — *Project ID* из меню проекта, на CurseForge — число *Project ID* на странице проекта.
3. Токены: Modrinth → *Settings → Personal access tokens*, права *Create versions*, *Read projects*, *Write versions* (позволяет *Sync release notes* обновлять описания версий на Modrinth) и *Write projects* (позволяет выкладывать страницу и галерею на Modrinth); CurseForge → *Account → API tokens*. Добавьте их как секреты репозитория `MODRINTH_TOKEN` и `CURSEFORGE_TOKEN` (*Settings → Secrets and variables → Actions*). Токены никогда не пишутся в файлы и чаты.

### Pull request

1. Создайте ветку от `main` (`feature/…`, `fix/…`).
2. Запустите `./gradlew :common:test :packaging:test` и `./gradlew build`.
3. Опишите, что изменилось и как вы проверяли в игре (версии Minecraft и загрузчика; клиент, сервер или оба).
4. Добавьте запись в `CHANGELOG.md` в раздел *Unreleased* на английском и на русском. Журнал изменений становится описанием версии на Modrinth и CurseForge: короткие простые пункты о том, что заметят игроки и владельцы серверов, без внутреннего устройства и без рекламного тона.
