# MAP
> `path → purpose` for every meaningful file/dir. The "where is X" index.
> Last updated: 2026-07-24

## Этот репозиторий (платформа)

```
docs/                      человекочитаемая документация (частично устарела — см. PLANS)
mg-api/                    контракты + общий тулкит (jar, compileOnly для игр)
mg-core/                   Paper-плагин: движок платформы
```

### mg-api — `mg-api/src/main/java/ru/kiviuly/mg/api/`

| Путь | Назначение |
|---|---|
| `MgCore.java` | фасад: `register`, `arenas`, `stats`, `transport`, `commandFor` |
| `game/Minigame.java` | точка расширения: ~22 хука, `id`, `adminPermission`, `dataFolder` |
| `game/Match.java` | контракт матча для игры |
| `game/GamePhase.java` | LOBBY / COUNTDOWN / RUNNING / ENDING |
| `game/MatchPlayer.java` | игрок в матче (uuid, name, alive, kills) |
| `game/MatchResult.java` | итог матча (список победителей — пока UUID) |
| `game/Team.java` | команда (движок отдаёт FFA-заглушку) |
| `game/MinigameDescriptor.java` | паспорт игры для реестра/селектора хаба |
| `arena/Arena.java` | конфиг арены + «карманы» `settings` и `spots` |
| `arena/ArenaService.java` | реестр арен: поиск, вход/выход, сохранение |
| `arena/SetupMarkers.java` | предметы-маркеры разметки точек |
| `data/DataKey.java` | типизированный ключ состояния матча |
| `stats/StatsService.java` | контракт статистики (+ `Row`, `LeaderboardEntry`) |
| `net/TransportService.java`, `net/QueueService.java` | сетевые контракты (реализаций нет) |
| `util/Msg.java` | общий каталог сообщений (init ядром, merge играми) |
| `util/Items.java`, `util/Keys.java`, `util/DebugLog.java` | предметы, PDC-ключи, диагностика |
| `menu/Menu.java`, `menu/AnvilInputMenu.java` | GUI-каркас |

### mg-core — `mg-core/src/main/java/ru/kiviuly/mg/`

| Путь | Назначение |
|---|---|
| `MgCorePlugin.java` | bootstrap, реестр игр, `gameFor(arena)`, публикация в ServicesManager |
| `game/GameSession.java` | **движок матча**: фазы, тикер, ростер, откат, HUD, статистика |
| `game/TemplateGame.java` | игра-заглушка, чтобы ядро работало «пустым» |
| `arena/ArenaManager.java` | реестр арен, раскладка по папкам игр, legacy-миграция |
| `arena/ArenaCheck.java` | валидатор арены перед включением |
| `command/MinigameCommand.java` | команда: платформенная (`owner==null`) и игровая |
| `listener/GameListener.java` | смерть→спектейт, откат блоков, лобби-PvP, вход/выход |
| `listener/{Protection,Chat,Setup}Listener.java` | защита мира, чат арены, разметка точек |
| `menu/MenuListener.java` | маршрутизация кликов ВСЕХ меню платформы и игр |
| `menu/Arena*Menu.java` | админ-GUI арен |
| `player/PlayerSnapshot.java` | сохранение/восстановление игрока вокруг матча |
| `stats/StatsRepository.java` | SQLite-бэкенд `StatsService` |
| `ui/GameScoreboard.java`, `ui/GameBossBar.java` | HUD матча |
| `net/LocalTransport.java` | no-op транспорт для одиночного сервера |

## Соседние репозитории

### `../skywars-reborn` — SkyWars ✅

`game/SkyWarsGame.java` — правила (капсулы, разминка в лобби, сундуки, киты);
`listener/SkyWarsListener.java`; `kit/`, `loot/`; `menu/` — игро-меню.
Точки сундуков хранит в `arena.spots("chest")`.

### `../skyblockwars-reborn` — SkyBlockWars ✅

Вся игра в пакете `sbw/`: `SkyBlockWarsGame`, `SbwState`, `ArenaGameConfig`
(игро-конфиг арены в `game/<ID>.yml`), `SbwListener`, `RingSpawns`, `epoch/`, `menu/`.

### `../escape-reborn` — Escape ⏳ (переезд не закончен)

| Путь | Назначение |
|---|---|
| `game/EscapeRules.java` | **бывший `GameSession`**: правила + 11 хуков платформы |
| `game/EscapeGame.java` | адаптер `Minigame`: раздаёт хуки в `EscapeRules` |
| `game/EscapePlayerData.java` | бывший `MatchPlayer` Escape (13 полей) |
| `arena/EscapeArena.java` | фасад: типизированный доступ к «карманам» арены |
| `arena/EscapeArenaConfig(s).java` | игровой конфиг арены: киты/контракты/фразы |
| `arena/ArenaManager.java` | **[WIP]** ещё создаёт сессии сам — под снос |
| `command/EscapeCommand.java`, `listener/*`, `menu/*` | **[WIP]** зовут удалённые методы |
| `game/{RespawnBlocks,OfflineGuards,Themes,GameEvent,ChatChannel}.java` | подсистемы игры |
| `contract/`, `theme/`, `trader/`, `modifier/`, `kit/`, `loot/` | контент игры |
| `util/{DebugLog,EscapeKeys,EscapeItems}.java` | своя диагностика + свои PDC-ключи/хелперы |
