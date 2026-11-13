# Changelog

All notable changes are listed here, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
each version is in English, then in Russian.

## [Unreleased]

## [1.6.0] - 2026-11-14

### English

#### Added
- **Thrown-item decoys (`Q`), sound masking & expanded block acoustics.** Dropping an item (`Q`) now creates a material-scaled impact sound where it lands — bells and metal gear clatter far, stone and glass clink, and wool or feathers land softly — so you can distract guards and pull mobs away. Loud ambient and combat noises (thunder, explosions, bells, gunshots) dynamically mask quieter steps and block actions, wading in water or walking on rain-soaked ground amplifies footsteps, and 1,000+ vanilla and modded blocks now map across 11 acoustic materials.
- **Two-stage suspicion, hazard-safe pathing, horde fan-out & ambush reactions.** Unaware mobs hearing a faint sound near the outer edge of range pause and glance toward the source first (`?`) before advancing on a second cue; investigating mobs avoid stepping into lava, fire, magma, powder snow or open trapdoor/fence-gate drops; rallied allies fan out in a ring instead of stacking on one block (with `horde_alert_range` now defaulting to `8` blocks); and unseen long-range or dark ambushes make healthy mobs sweep in your direction while badly wounded mobs scramble for cover instead of snapping onto your exact coordinates. All four tactical behaviors can be toggled live on the *Senses & World* tab (`sound_cover_enabled`, `decoy_throw_enabled`, `safe_goal_filter`, `suspicion_enabled`).
- **Live HUD alert indicator.** The Stealth HUD now shows `[?]` (*Suspicious*) and `[!]` (*Searching*) badges whenever your actions alert nearby mobs, and computes exposure and status from whichever HUD meters you keep enabled.

#### Fixed
- **Scorched Guns 2 air guns & suppressed weapons.** Air and pneumatic weapons (*Scorched Guns 2* `IAirGun` and quiet guns), integrally suppressed firearms (*VSS Vintorez*, *AS Val*, *MP5SD*, *Honey Badger*, *Welrod*, *De Lisle*, *VSSK*), *Superb Warfare* suppressors/`bocek`/`taser`, and offhand firearms held while throwing items or shooting bows are now classified accurately instead of counting as loud unsilenced gunshots.
- **Cross-version & Folia stability (`1.16.5 – 26.3`).** Fixed command source resolution on `1.21 – 1.21.1`, `fallDistance` lookup on `1.21 – 1.21.4`, Forge `1.16.5` SRG client/tag lookups, Forge `1.21` and `1.21.6–1.21.7` HUD overlay fallback, Paper `1.21.3 – 26.3` `Biome` linkage in weather checks, and Folia region-thread safety.

### Русский

#### Добавлено
- **Приманки броском предмета (`Q`), маскировка звуков и расширенная акустика блоков.** Брошенный клавишей `Q` предмет создаёт звук при падении, зависящий от материала (колокола и металл гремят далеко, камень и стекло слышны средне, шерсть и перья падают тихо), позволяя отвлекать мобов и уводить их в сторону. Громкие звуки поблизости (гром, взрывы, колокол, выстрелы) маскируют тихие шаги и действия с блоками, ходьба в воде или по мокрой под дождём земле звучит отчётливее, а более 1000 ванильных и модовых блоков теперь распределены по 11 акустическим материалам.
- **Двухфазная настороженность, безопасные маршруты, рассредоточение орды и реакция на засаду.** Незнающие об игроке мобы на границе слышимости сначала замирают и оборачиваются на слабый звук (`?`), а идут на проверку при повторном шуме; на пути к цели мобы не заходят в лаву, огонь, магму, рыхлый снег или открытые люки; позванные союзники распределяются по кругу вместо толпы в одной точке (`horde_alert_range` по умолчанию теперь `8` блоков); а при скрытой атаке издалека или из темноты здоровые мобы прочёсывают сторону выстрела, тогда как тяжело раненые отступают вместо мгновенного наведения на точные координаты. Все четыре тактические механики включаются и выключаются на вкладке *Senses & World* (`sound_cover_enabled`, `decoy_throw_enabled`, `safe_goal_filter`, `suspicion_enabled`).
- **Индикатор тревоги на HUD.** На Stealth HUD теперь отображаются метки `[?]` (*Подозрение*) и `[!]` (*Поиск*), когда ваши действия встревожили мобов поблизости, а общий уровень заметности и статус рассчитываются только по включённым полоскам.

#### Исправлено
- **Пневматика Scorched Guns 2 и бесшумное оружие.** Пневматическое оружие (*Scorched Guns 2* `IAirGun` и тихие стволы), оружие с интегрированным глушителем (*ВСС «Винторез»*, *АС «Вал»*, *MP5SD*, *Honey Badger*, *Welrod*, *De Lisle*, *VSSK*), глушители и тихое оружие *Superb Warfare* (`bocek`, `taser`), а также огнестрел во второй руке при броске предметов или стрельбе из лука теперь распознаются корректно и не засчитываются как громкие выстрелы.
- **Стабильность между версиями и на Folia (`1.16.5 – 26.3`).** Исправлено разрешение команд на `1.21 – 1.21.1`, чтение `fallDistance` на `1.21 – 1.21.4`, SRG-имена на Forge `1.16.5`, запасной рендер HUD на Forge `1.21` и `1.21.6–1.21.7`, совместимость с `Biome` на Paper `1.21.3 – 26.3` при проверке погоды и потокобезопасность регионов на Folia.

## [1.5.0] - 2026-11-11

### English

#### Added
- **In-game settings screen.** Tune your Stealth HUD with a live interactive preview on the *Stealth HUD* tab, and adjust presets, hearing, firearms, voice chat, torchlight stealth, blood trails and world settings on the *Senses & World* tab. Open it from Mod Menu (Fabric and Quilt), *Mods → Config* (Forge and NeoForge), your own key in *Controls*, or `/mshud`.
- **Live sync between client and server.** When both sides have Mob Senses — including Paper, Purpur and Folia servers — your Stealth HUD automatically uses the active preset and multipliers of the server or dimension so its audible distance readout stays accurate. Server operators can adjust presets and sliders live on the *Senses & World* tab, or hide the client HUD for fair PvP and horror play.
- **Clickable `/mobsenses` replies and staff bypass.** `/mobsenses` chat replies now include clickable controls to switch presets and settings directly from chat, and servers can grant vanished moderators a bypass permission so mobs ignore their extra noise, held light and blood trails.
- **Forge 1.16.5 – 1.17.1 and more firearm mods.** The universal Forge and NeoForge file now covers every Minecraft version from 1.16.5 to 26.3, matching Fabric and Paper, and firearm and suppressor detection now also supports *Superb Warfare* and *Just Enough Guns*.

#### Fixed
- Small fixes.

### Русский

#### Добавлено
- **Экран настроек в игре.** Настраивайте индикатор скрытности с живым интерактивным предпросмотром на вкладке *Stealth HUD*, а пресеты, слух, оружие, голосовой чат, свет факела, след крови и параметры мира — на вкладке *Senses & World*. Открывается через Mod Menu (Fabric и Quilt), *Mods → Config* (Forge и NeoForge), свою клавишу в настройках управления или команду `/mshud`.
- **Живая синхронизация клиента и сервера.** Когда мод установлен с обеих сторон — в том числе при входе на серверы Paper, Purpur и Folia — индикатор скрытности сам учитывает активный пресет и множители сервера или текущего измерения для точного расчёта радиуса слышимости. Операторы сервера могут менять пресеты и ползунки прямо на вкладке *Senses & World*, а для честного PvP и хоррора сервер может скрыть клиентский HUD.
- **Кликабельные ответы `/mobsenses` и обход для персонала.** В ответах `/mobsenses` появились кликабельные кнопки для быстрого переключения пресетов и параметров прямо из чата, а модераторам в ванише можно выдать право обхода, чтобы мобы не реагировали на их дополнительный шум, свет в руке и след крови.
- **Forge 1.16.5 – 1.17.1 и новые оружейные моды.** Универсальный файл Forge и NeoForge теперь охватывает все версии Minecraft от 1.16.5 до 26.3 наравне с Fabric и Paper, а поддержка огнестрельного оружия и глушителей дополнена модами *Superb Warfare* и *Just Enough Guns*.

#### Исправлено
- Мелкие исправления.

## [1.4.0] - 2026-11-10

### English

#### Added
- **Redesigned Stealth & Noise HUD and `/mshud` command:** players can now switch between `tactical` (chamfered card with dynamic stealth eye, `30%`/`65%` threshold ticks and transient peak-hold markers) and `minimal` (slim bar strip) styles, see their estimated audible distance (`~Xm`) and `[Whisper]` vs `[Voice]` status, enjoy smooth auto-hide fading, and configure everything on any server without operator permissions via `/mshud` (`/mobsenseshud`).
- **Expanded loader & version coverage:** dedicated native builds for Fabric `1.19 – 1.20.6` (including `1.20.1`) and Forge `1.18 – 1.19.4` & `1.20.5 – 1.21.5` (including `1.21.1`), plus full client Stealth HUD and `/mshud` support on Forge `1.20.5 – 26.3`.

#### Fixed
- **Stealth, sight and hearing accuracy:** preset sight multipliers (`subtle`, `hardcore`), `darkness_stealth_enabled`, invisibility potions on the HUD, Swift Sneak on leggings, open fence gates/trapdoors passing sound, bullet wall-impact raycasts through tall grass/open doors, custom `scent_mobs` and `investigate_speed` in sight/scent investigation now all work accurately across Forge, NeoForge, Fabric and Paper/Folia.
- **Multiplayer & LAN polish:** LAN guests no longer trigger the host's HUD, debug particle traces are sent only to nearby operators, and Folia pathfinding runs safely on each mob's region thread.

### Русский

#### Добавлено
- **Обновлённый HUD скрытности и команда `/mshud`:** появились стили `tactical` (тактическая карточка с динамической иконкой глаза, рисками порогов `30%`/`65%` и удержанием пиков шума) и `minimal` (тонкая полоска), отображение радиуса слышимости (`~Xм`), раздельные метки `[Шёпот]` и `[Голос]`, плавное автоскрытие и клиентская команда `/mshud` (`/mobsenseshud`), доступная каждому игроку на любом сервере без прав оператора.
- **Расширенная поддержка версий и загрузчиков:** добавлены нативные сборки для Fabric `1.19 – 1.20.6` (включая `1.20.1`) и Forge `1.18 – 1.19.4` и `1.20.5 – 1.21.5` (включая `1.21.1`), а также полноценная работа клиентского HUD и `/mshud` на Forge `1.20.5 – 26.3`.

#### Исправлено
- **Точность скрытности, зрения и слуха:** множители зрения пресетов (`subtle`, `hardcore`), настройка `darkness_stealth_enabled`, учёт невидимости на HUD, зачарование «Проворство» на поножах, прохождение звука через открытые калитки и люки, трассировка выстрелов сквозь траву и открытые двери, пользовательский список `scent_mobs` и `investigate_speed` теперь работают точно на Forge, NeoForge, Fabric и Paper/Folia.
- **Мультиплеер и LAN:** действия гостей в мире по сети (LAN) больше не дёргают HUD хоста, частицы `/mobsenses debug` видят только ближайшие операторы, а поиск пути на Folia выполняется строго в потоке региона моба.

## [1.3.1] - 2026-11-10

### English

#### Added
- **Bullet impacts draw mobs where the shot lands:** firing at a distant wall now makes noise at the point of impact, so a silenced shot into a wall across the area can distract or lure nearby mobs away from you.
- **More settings and easier commands:** server owners can now fine-tune sight, darkness stealth, blood trails, custom scent-tracking mobs, through-wall hearing and horde size in `config/mobsenses-server.properties`, view settings by group with `/mobsenses get <category>`, and reset any option (or the whole config) to defaults with `/mobsenses reset`.
- **More `/mobsenses hud` options:** players can now adjust HUD opacity, screen offset and individual bars, or reset the HUD to defaults directly from chat.
- **Invisibility and small fixes:** potions of invisibility now hide you from mobs at a distance as long as you are not holding a light source. Small fixes.

### Русский

#### Добавлено
- **Звук попадания пули в стену:** выстрел в далёкую стену теперь создаёт шум в точке попадания, поэтому выстрелом с глушителем можно отвлечь или выманить мобов в сторону.
- **Больше настроек и удобные команды:** в `config/mobsenses-server.properties` добавлены настройки зрения, скрытности в темноте, следа крови, дополнительных мобов-ищеек, слышимости сквозь стены и размера орды. Посмотреть группу настроек теперь можно через `/mobsenses get <категория>`, а сбросить отдельный параметр или весь конфиг к стандартным значениям — командой `/mobsenses reset`.
- **Расширенная настройка `/mobsenses hud`:** прозрачность, сдвиг по экрану и отдельные полоски индикатора теперь можно менять или сбрасывать прямо из чата.
- **Невидимость и мелкие исправления:** зелье невидимости теперь скрывает игрока от взгляда мобов на расстоянии, если в руке нет источника света. Мелкие исправления.

## [1.3.0] - 2026-11-10

### English

#### Added
- **Detailed server configuration:** server owners can now adjust the hearing distance of every sound type (footsteps, breaking and placing blocks, doors, chests, combat, explosions, gunshots, suppressors and individual weapons), wall muffling, voice chat, sight, blood scent, mob investigation speed and custom listener or ignored mobs in `config/mobsenses-server.properties` or in-game with `/mobsenses set`.
- **Weather, Feather Falling and horde alerts:** rain and thunderstorms now help mask your sounds, boots with Feather Falling or Swift Sneak make footsteps quieter, and alerted mobs can optionally call nearby allies to investigate together.
- **HUD command and auto-hide:** players can now toggle the stealth HUD, switch between compact and full mode, move it on the screen or enable auto-hide when everything is quiet via `/mobsenses hud`.
- **Long-distance investigation:** mobs that hear a loud sound from far away now reliably walk the whole distance to investigate it.

### Русский

#### Добавлено
- **Подробная настройка сервера:** в `config/mobsenses-server.properties` и в команде `/mobsenses set` теперь можно отдельно настроить дальность каждого вида звука (шагов, ломания и установки блоков, дверей, сундуков, боя, взрывов, выстрелов, глушителей и конкретного оружия), глушение стенами, голосовой чат, зрение, запах крови, скорость расследования, а также списки мобов.
- **Погода, «Невесомость» и призыв орды:** дождь и гроза теперь приглушают звуки игрока, ботинки с «Невесомостью» или «Проворством» делают шаги тише, а встревоженные мобы при желании могут звать с собой ближайших сородичей.
- **Команда `/mobsenses hud` и автоскрытие:** игроки могут включать и выключать HUD скрытности, менять его положение, компактный режим и автоскрытие в тишине прямо командой `/mobsenses hud`.
- **Расследование далёких звуков:** мобы, услышавшие громкий шум издалека, теперь уверенно доходят до его источника даже с больших расстояний.

## [1.2.0] - 2026-11-09

### English

#### Added
- **Gun mod support (TaCZ, PointBlank, MrCrayfish's Gun Mod, Scorched Guns):** firing a gun carries far across the area and draws nearby mobs to investigate, while attaching a suppressor or silencer muffles the shot to close range.
- **Client HUD settings (`config/mobsenses-client.properties`):** you can now move the HUD to any screen corner or above the hotbar, adjust its offset and opacity, switch to compact mode, toggle individual bars, or hide it completely in the client config file without restarting the game.

### Русский

#### Добавлено
- **Поддержка оружейных модов (TaCZ, PointBlank, MrCrayfish's Gun Mod, Scorched Guns):** выстрелы из огнестрельного оружия разносятся далеко по округе и стягивают мобов к стрелку, а установленный глушитель снижает слышимость выстрела до ближней дистанции.
- **Файл настроек клиентского HUD (`config/mobsenses-client.properties`):** теперь в клиентском файле настроек (без команд и без перезапуска игры) можно перенести HUD в любой угол экрана или над хотбаром, задать отступы и прозрачность, включить компактный режим, скрыть отдельные полоски или полностью выключить HUD.

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
