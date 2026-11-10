# Changelog

All notable changes are listed here, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
each version is in English, then in Russian.

## [Unreleased]

## [1.3.0] - 2026-11-10

### English

#### Added
- **Configurable firearm and suppressor detection distances (`config/mobsenses-server.properties`):** added `gunshot_range=44` (unsuppressed shots, `0` – `256` blocks), `gunshot_silenced_range=16` (suppressed/silenced shots, `0` – `256` blocks; set `0` to make suppressors completely silent) and `bullet_impact_range=20` (bullet ricochets and impacts).
- **Per-category sound ranges & wall muffling:** every sound category can now be tuned or disabled (`0` – `256` blocks) in `config/mobsenses-server.properties`: `step_range`, `sneak_step_multiplier`, `sprint_step_multiplier`, `block_break_range`, `block_place_range`, `door_range`, `container_range`, `combat_range`, `projectile_range`, `explosion_range`, `bell_range`, `note_block_range`, `eat_range` and `wall_muffling` (`0` = sound passes through walls, `1` = realistic, `2` = double muffling).
- **Voice, Sight, Scent & Mob AI settings:** added `voice_range`, `voice_whisper_multiplier`, `sight_range`, `held_light_enabled`, `darkness_stealth_enabled`, `scent_enabled`, `scent_health_threshold`, `scent_range`, `blood_particles`, `investigate_speed`, `urgent_investigate_speed`, `extra_listeners` and `ignored_mobs` (supports exact mob IDs and `namespace:*` wildcards), plus `show_voice` in `config/mobsenses-client.properties`.
- **Long-range mob pathfinding:** mobs investigating distant sounds (up to `256` blocks) now step through intermediate waypoints so they reliably walk across large distances beyond vanilla `FOLLOW_RANGE`.

### Русский

#### Добавлено
- **Настройка дальности слышимости выстрелов и глушителей (`config/mobsenses-server.properties`):** добавлены параметры `gunshot_range=44` (выстрелы без глушителя, `0` – `256` блоков), `gunshot_silenced_range=16` (выстрелы с глушителем, `0` – `256` блоков; `0` делает глушитель полностью бесшумным) и `bullet_impact_range=20` (попадания пуль и рикошеты).
- **Индивидуальная дальность всех категорий звуков и глушение стенами:** каждую категорию звука теперь можно тонко настроить или отключить (`0` – `256` блоков) в `config/mobsenses-server.properties`: `step_range`, `sneak_step_multiplier`, `sprint_step_multiplier`, `block_break_range`, `block_place_range`, `door_range`, `container_range`, `combat_range`, `projectile_range`, `explosion_range`, `bell_range`, `note_block_range`, `eat_range` и `wall_muffling` (`0` = стены не глушат звук, `1` = реалистично, `2` = двойное глушение).
- **Настройки голоса, зрения, запаха крови и ИИ мобов:** добавлены `voice_range`, `voice_whisper_multiplier`, `sight_range`, `held_light_enabled`, `darkness_stealth_enabled`, `scent_enabled`, `scent_health_threshold`, `scent_range`, `blood_particles`, `investigate_speed`, `urgent_investigate_speed`, а также списки `extra_listeners` и `ignored_mobs` (с поддержкой ID мобов и масок `namespace:*`) и `show_voice` в `config/mobsenses-client.properties`.
- **Дальняя навигация мобов:** мобы, идущие проверять далёкие звуки (вплоть до `256` блоков), теперь строят маршрут через промежуточные точки и уверенно доходят даже с расстояний, превышающих ванильный `FOLLOW_RANGE`.

## [1.2.0] - 2026-11-09

### English

#### Added
- **Gun mod support (TaCZ, PointBlank, MrCrayfish's Gun Mod, Scorched Guns):** firing a gun carries far across the area and draws nearby mobs to investigate, while attaching a suppressor or silencer muffles the shot to close range.
- **Client HUD settings file (`config/mobsenses-client.properties`):** you can now move the HUD to any screen corner or above the hotbar (`position=top_left`, `top_right`, `bottom_left`, `bottom_right`, `top_center`, `hotbar`), adjust `x` and `y` offsets, change `opacity`, switch to `compact=true` mode, toggle individual bars, or hide the HUD (`enabled=false`) directly in the config file without commands or a restart.

### Русский

#### Добавлено
- **Поддержка оружейных модов (TaCZ, PointBlank, MrCrayfish's Gun Mod, Scorched Guns):** выстрелы из огнестрельного оружия разносятся далеко по округе и стягивают мобов к стрелку, а установленный глушитель снижает слышимость выстрела до ближней дистанции.
- **Файл настроек клиентского HUD (`config/mobsenses-client.properties`):** теперь прямо в файле настроек (без команд и без перезапуска игры) можно перенести HUD в любой угол экрана или над хотбаром (`position=top_left`, `top_right`, `bottom_left`, `bottom_right`, `top_center`, `hotbar`), задать отступы `x` и `y`, прозрачность `opacity`, компактный режим `compact=true`, скрыть отдельные полоски или полностью выключить HUD (`enabled=false`).

## [1.1.0] - 2026-11-06

### English

#### Added
- **Visible blood trail:** badly wounded players (at or below half health) now leave small crimson blood droplets on the ground along the trail that zombies and spiders follow, so players without the client mod can also see when they are bleeding.

### Русский

#### Добавлено
- **Видимый след крови:** сильно раненные игроки (половина здоровья и меньше) теперь оставляют на земле небольшие капли крови по следу, за которым идут зомби и пауки, поэтому кровотечение видно даже без клиентского мода.

## [1.0.0] - 2026-11-06

### English

#### Added
- **Hearing and investigation:** hostile mobs (`#mobsenses:listeners`) hear footsteps (louder when running, quieter when sneaking, muffled on wool and carpet, louder on gravel and metal), breaking and placing blocks, doors, trapdoors, fence gates, chests, barrels, shulker boxes, combat, projectiles, explosions, bells, note blocks and eating. Stone and brick walls muffle sound heavily, wood and glass less, and open doors let sound through. Alerted mobs walk to where the sound came from, look around and return to their business.
- **Simple Voice Chat support:** mobs hear players speaking in voice chat. Loudness is measured relative to each player's own normal speaking voice, so a quiet microphone gives no unfair stealth advantage; whispering stays quiet and shouting carries far.
- **Light, stealth and blood scent:** holding a torch, lantern or glowstone makes you visible from farther away, while sneaking in the dark hides you and lets distant pursuers lose track of you. Badly wounded players (at or below half health) leave a scent trail that zombies and spiders follow.
- **Client stealth HUD:** players who install the mod on the client see a compact indicator of their current noise, light exposure, voice activity and blood scent.
- **Commands and settings (`/mobsenses`):** presets `off`, `subtle`, `default` and `hardcore`, `hearing` and `sight` multipliers, `voice on|off`, per-world presets (`world <world|here> <preset|inherit>`), live `reload`, `status` with 1-minute event and alerted-mob counters, and `/mobsenses debug [on|off]` particle traces for operators. Replies come in the player's language: English, Russian, Ukrainian, German, Spanish, Portuguese or Chinese.
- **Universal files for Minecraft 1.16.5 – 26.3:** one jar for Fabric and Quilt (`1.16.5 – 26.3`), one jar for Forge and NeoForge (`1.16.5 – 26.3`), and one plugin jar for Paper, Purpur, Folia and Spigot (`1.16.5 – 26.3`).

### Русский

#### Добавлено
- **Слух и расследование:** враждебные мобы (`#mobsenses:listeners`) слышат шаги (бег громче, шаг в приседе тише, шерсть и ковёр глушат шаги, а гравий и металл выдают), ломание и установку блоков, двери, люки, калитки, сундуки, бочки, шалкеры, бой, снаряды, взрывы, колокола, нотные блоки и еду. Каменные и кирпичные стены сильно глушат звук, дерево и стекло слабее, а открытые двери пропускают его. Встревоженный моб идёт к источнику шума, осматривается и возвращается к своим делам.
- **Поддержка Simple Voice Chat:** мобы слышат голос игроков в голосовом чате. Громкость отсчитывается от обычного голоса каждого игрока, поэтому тихий микрофон не даёт нечестной скрытности; шёпот слышно только вблизи, а крик — издалека.
- **Свет, скрытность и запах крови:** факел, фонарь или светокамень в руке делают игрока заметным издалека, а присед в темноте скрывает и позволяет оторваться от далёких преследователей. Сильно раненные игроки (половина здоровья и меньше) оставляют след, по которому идут зомби и пауки.
- **Клиентский HUD скрытности:** у игроков с модом на клиенте отображается компактный индикатор текущего шума, освещённости, активности голоса и запаха крови.
- **Команды и настройки (`/mobsenses`):** пресеты `off`, `subtle`, `default` и `hardcore`, множители `hearing` и `sight`, переключение `voice on|off`, пресеты для отдельных миров (`world <мир|here> <пресет|inherit>`), `reload` без перезапуска, `status` со счётчиками шума и встревоженных мобов за минуту, а также частицы `/mobsenses debug [on|off]` для операторов. Ответы приходят на языке игрока: английском, русском, украинском, немецком, испанском, португальском или китайском.
- **Универсальные файлы для Minecraft 1.16.5 – 26.3:** один jar для Fabric и Quilt (`1.16.5 – 26.3`), один jar для Forge и NeoForge (`1.16.5 – 26.3`) и один плагин для Paper, Purpur, Folia и Spigot (`1.16.5 – 26.3`).
