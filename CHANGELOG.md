# Changelog

All notable changes are listed here, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
each version is in English, then in Russian.

## [Unreleased]

## [1.3.1] - 2026-11-10

### English

#### Added
- **TaCZ & kinetic bullet wall-impact acoustics (`bullet_impact_range`):** firearm shots (including suppressed shots from TaCZ `EntityKineticBullet`, PointBlank, CGM and Scorched Guns) now raycast along the bullet trajectory up to `64` blocks and emit a `mobsenses:bullet_impact` sound at the distant impact point (`>= 12` blocks away), allowing silenced shots fired at a distant wall to attract or distract mobs near where the bullet lands.
- **10 additional server config parameters (`config/mobsenses-server.properties`):**
  - `through_wall_hearing=true` (toggle whether loud sounds can penetrate solid walls at all) and `surface_sound_scaling=true` (toggle whether wool/metal/gravel block materials scale sound).
  - `held_light_bonus=0.5`, `darkness_min_visibility=0.2`, `sneak_sight_multiplier=0.6` and `sprint_sight_multiplier=1.15` for fine-tuning visual detection and torchlight stealth.
  - `scent_max_range=24`, `scent_duration_seconds=15` and `scent_mobs=` (custom mob IDs or `namespace:*` wildcards that track blood trails alongside zombies and spiders).
  - `horde_max_allies=4` (maximum nearby allies pulled per alerted mob when `horde_alert_range > 0`).
- **Command & HUD conveniences:**
  - `/mobsenses reset <option|all>` (and `/mobsenses set <option> default`) to restore any setting or the entire config to factory defaults.
  - `/mobsenses get <category>` (`core`, `guns`, `sounds`, `stealth`, `voice`, `sight`, `scent`, `mobs`, `all`) to inspect a whole group of settings at once.
  - `/mobsenses hud opacity <10..100>`, `/mobsenses hud offset <x> <y>`, `/mobsenses hud bar <noise|light|scent|voice> [on|off]` and `/mobsenses hud reset`.
- **Invisibility & cross-version polish:** invisible players (`Potion of Invisibility`) without a held light source now have `85%` reduced visual detection range; added legacy `1.17–1.19` redstone/piston/bell `GameEvent` IDs (`piston_extend`, `piston_contract`, `block_press`, `block_switch`, `ring_bell`, `shulker_open`) and full rain/thunder/`Feather Falling` muffling parity on Bukkit/Paper/Purpur/Folia.

### Русский

#### Добавлено
- **Звук попадания пули в стены для TaCZ (`bullet_impact_range`):** при выстреле из огнестрельного оружия (включая `EntityKineticBullet` из TaCZ, PointBlank, CGM и Scorched Guns) траектория пули просчитывается лучом до `64` блоков и создаёт событие `mobsenses:bullet_impact` в точке попадания (`>= 12` блоков от стрелка). Теперь выстрел с глушителем в дальнюю стену привлекает или отвлекает мобов возле места удара пули.
- **10 новых параметров тонкой настройки в `config/mobsenses-server.properties`:**
  - `through_wall_hearing=true` (разрешить или полностью запретить прохождение громких звуков сквозь сплошные стены) и `surface_sound_scaling=true` (влияние материала блока — шерсти, металла, гравия — на громкость шагов и ломания).
  - `held_light_bonus=0.5`, `darkness_min_visibility=0.2`, `sneak_sight_multiplier=0.6` и `sprint_sight_multiplier=1.15` для детальной настройки зрения мобов и заметности с факелом.
  - `scent_max_range=24`, `scent_duration_seconds=15` и `scent_mobs=` (дополнительные ID мобов или маски `namespace:*`, которые идут по следу крови наряду с зомби и пауками).
  - `horde_max_allies=4` (сколько максимум союзников подтягивает за собой каждый встревоженный моб при `horde_alert_range > 0`).
- **Удобства в командах `/mobsenses` и настройке HUD:**
  - `/mobsenses reset <параметр|all>` (а также `/mobsenses set <параметр> default`) для быстрого сброса одной настройки или всего конфига к заводским значениям.
  - `/mobsenses get <категория>` (`core`, `guns`, `sounds`, `stealth`, `voice`, `sight`, `scent`, `mobs`, `all`) для просмотра сразу всей группы настроек в чате.
  - `/mobsenses hud opacity <10..100>`, `/mobsenses hud offset <x> <y>`, `/mobsenses hud bar <noise|light|scent|voice> [on|off]` и `/mobsenses hud reset`.
- **Невидимость и полировка совместимости:** эффект невидимости (если в руке нет факела/фонаря) снижает дальность визуального обнаружения на `85%`; добавлены все идентификаторы редстоун-событий и поршней из `1.17–1.19` (`piston_extend`, `piston_contract`, `block_press`, `block_switch`, `ring_bell`, `shulker_open`) и полный паритет погодного заглушения и зачарования «Невесомость» в плагине для Bukkit/Paper/Purpur/Folia.

## [1.3.0] - 2026-11-10

### English

#### Added
- **Configurable firearm and suppressor detection distances (`config/mobsenses-server.properties`):** added `gunshot_range=44` (unsuppressed shots, `0` – `256` blocks), `gunshot_silenced_range=16` (suppressed/silenced shots, `0` – `256` blocks; set `0` to make suppressors completely silent), `bullet_impact_range=20` (bullet ricochets and impacts), plus optional per-weapon overrides `gun.<id>.range` and `gun.<id>.silenced_range` (e.g. `gun.tacz\:ai_awp.range=85`, `gun.glock.silenced_range=8`).
- **Per-category sound ranges, weather & stealth gear muffling:** every sound category can now be tuned or disabled (`0` – `256` blocks) in `config/mobsenses-server.properties`: `step_range`, `sneak_step_multiplier`, `sprint_step_multiplier`, `block_break_range`, `block_place_range`, `door_range`, `container_range`, `combat_range`, `projectile_range`, `explosion_range`, `bell_range`, `note_block_range`, `eat_range`, `wall_muffling` (`0` = sound passes through walls, `1` = realistic, `2` = double muffling), `rain_muffling` (`0.25`), `thunder_muffling` (`0.45`) and `feather_falling_muffling` (`0.2`).
- **Voice, Sight, Scent, Horde & Mob AI settings:** added `voice_range`, `voice_whisper_multiplier`, `sight_range`, `held_light_enabled`, `darkness_stealth_enabled`, `scent_enabled`, `scent_health_threshold`, `scent_range`, `blood_particles`, `investigate_speed`, `urgent_investigate_speed`, `investigation_seconds`, `max_alerted_mobs` (`1` – `64`), `horde_alert_range` (`0` – `64` blocks), `alert_cooldown_ms`, `extra_listeners` and `ignored_mobs` (supports exact mob IDs and `namespace:*` wildcards).
- **In-game `/mobsenses set` & `/mobsenses hud` commands + HUD `auto_hide`:** operators can now view or change any config setting in-game with Tab-completion via `/mobsenses set <option> [value]`, players can switch HUD position/mode via `/mobsenses hud <on|off|compact|full|auto_hide|position>`, and `config/mobsenses-client.properties` now includes `auto_hide` and `show_voice`.
- **Long-range mob pathfinding:** mobs investigating distant sounds (up to `256` blocks) now step through intermediate waypoints so they reliably walk across large distances beyond vanilla `FOLLOW_RANGE`.

### Русский

#### Добавлено
- **Настройка дальности слышимости выстрелов и глушителей (`config/mobsenses-server.properties`):** добавлены параметры `gunshot_range=44` (выстрелы без глушителя, `0` – `256` блоков), `gunshot_silenced_range=16` (выстрелы с глушителем, `0` – `256` блоков; `0` делает глушитель полностью бесшумным), `bullet_impact_range=20` (попадания пуль и рикошеты), а также индивидуальные оверрайды для конкретного оружия `gun.<id>.range` и `gun.<id>.silenced_range` (например, `gun.tacz\:ai_awp.range=85`, `gun.glock.silenced_range=8`).
- **Индивидуальная дальность всех категорий звуков, погода и экипировка скрытности:** каждую категорию звука теперь можно тонко настроить или отключить (`0` – `256` блоков) в `config/mobsenses-server.properties`: `step_range`, `sneak_step_multiplier`, `sprint_step_multiplier`, `block_break_range`, `block_place_range`, `door_range`, `container_range`, `combat_range`, `projectile_range`, `explosion_range`, `bell_range`, `note_block_range`, `eat_range`, `wall_muffling` (`0` = стены не глушат звук, `1` = реалистично, `2` = двойное глушение), а также маскировка звука дождём (`rain_muffling=0.25`), грозой (`thunder_muffling=0.45`) и снижение шума шагов от «Невесомости» и «Проворства» (`feather_falling_muffling=0.2`).
- **Настройки голоса, зрения, запаха крови, орды и ИИ мобов:** добавлены `voice_range`, `voice_whisper_multiplier`, `sight_range`, `held_light_enabled`, `darkness_stealth_enabled`, `scent_enabled`, `scent_health_threshold`, `scent_range`, `blood_particles`, `investigate_speed`, `urgent_investigate_speed`, `investigation_seconds`, `max_alerted_mobs` (`1` – `64`), `horde_alert_range` (`0` – `64` блока — цепная реакция орды), `alert_cooldown_ms`, а также списки `extra_listeners` и `ignored_mobs` (с поддержкой ID мобов и масок `namespace:*`).
- **Команды `/mobsenses set` и `/mobsenses hud` + автоскрытие HUD (`auto_hide`):** любую серверную настройку теперь можно посмотреть или изменить прямо из игры с автодополнением Tab через `/mobsenses set <параметр> [значение]`, клиентский HUD переключается командой `/mobsenses hud <on|off|compact|full|auto_hide|позиция>`, а в `config/mobsenses-client.properties` добавлены `auto_hide` и `show_voice`.
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
