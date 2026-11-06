# Changelog

All notable changes are listed here, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
each version is in English, then in Russian.

## [Unreleased]

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
