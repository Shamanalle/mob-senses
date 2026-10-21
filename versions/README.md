# Minecraft versions

One folder per loader and band of Minecraft versions: `versions/<loader>/<band>`. Its Gradle project is `:<loader>-<band>`, so `versions/fabric/1.21` is `:fabric-1.21` (`./gradlew :fabric-1.21:runServer`). Which Minecraft versions a band covers is in `gradle.properties`.

The bands are not released one by one: the root `build.gradle` merges every Fabric band into the Fabric / Quilt jar and every Forge and NeoForge band into the Forge / NeoForge jar. Most of the code is in `common/` and `shared/`; a folder here holds only what differs on its versions. [CONTRIBUTING.md](../CONTRIBUTING.md#where-code-lives) says what each one has.

---

*Одна папка на загрузчик и диапазон версий Minecraft: `versions/<загрузчик>/<диапазон>`. Её проект Gradle — `:<загрузчик>-<диапазон>`, так что `versions/fabric/1.21` — это `:fabric-1.21` (`./gradlew :fabric-1.21:runServer`). Какие версии Minecraft входят в диапазон, написано в `gradle.properties`.*

*По отдельности диапазоны не выпускаются: корневой `build.gradle` собирает все диапазоны Fabric в jar для Fabric / Quilt, а все диапазоны Forge и NeoForge — в jar для Forge / NeoForge. Основной код — в `common/` и `shared/`; в папке здесь только то, что отличается на её версиях. Что в какой — в [CONTRIBUTING.md](../CONTRIBUTING.md#где-какой-код).*
