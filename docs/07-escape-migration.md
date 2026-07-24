# 07 — Миграция escape

> **СТАТУС: ВЫПОЛНЕНО** (2026-07-24, ветка игры `refactor/Migrate-to-MG-core`).
> Escape собирается и работает на платформенном движке; все 4 репо зелёные. Рантайм-
> прогон — в самом конце (решение владельца). Актуальную живую сводку держит
> `.memories/` (приоритет над этим документом). Ниже — исходный план + фактические отклонения.
>
> **Итог и отклонения от плана:**
> - `GameSession` **переименован в `EscapeRules`** (выпотрошен, ~19 движковых методов вырезано),
>   а НЕ удалён — это «правила» матча, живут по объекту на матч через `DataKey`. `EscapeGame extends
>   Minigame` раздаёт хуки в `EscapeRules`.
> - **Осознанно оставлены СВОИМИ** (не дубликаты): `DebugLog` (8 игровых категорий), трёхпроходный
>   откат блоков (чинит стыки решёток), `PlayerSnapshot` (setup-режим), `SetupMarkers` (escape-точки),
>   `ArenaManager` (тонкий фасад-мост `Match`→`EscapeRules`), `ChatChannel`.
> - **Команды** идут через `core.commandFor`; ~60 игро-подкоманд — через `EscapeGame.onCommand`→`EscapeCommand`.
> - **Статистика** — в общий `StatsService`, но модель сменилась на **generic-счётчики** (не `gameId`-
>   scoped колонки); escape помечен `recordsOwnStats()` (пишет свои счётчики сам). См. `05-storage-and-stats`.
> - **Оффлайн-стражи** подключены через хуки `keepOnDisconnect`/`onPlayerDisconnect`/`onPlayerReconnect`.
> - Package root игры оставлен `me.aver005.escape` (ренейм на `ru.kiviuly.escape` — не делали, косметика).

`escape-reborn` — старшее поколение и самый трудный кусок. В отличие от «wars» у него
не было шва `Minigame`: вся игра — один класс `GameSession` на **2149 строк** со
service-locator через `EscapePlugin`. Задача была — подложить под него общий core, не
потеряв богатые механики.

## Что в escape уникального (не трогаем логику, только переносим под шов)

Механики, которых нет в «wars» и которые должны выжить:

- `contract/*` — бумажные контракты, прогресс в PDC предмета.
- `trader/*` — торговцы-жители.
- `modifier/*` — модификаторы матча, выбираемые голосованием.
- `game/Themes` + `theme/*` — NPC-квесты («темы»).
- `game/RespawnBlocks` / `RespawnBlock` / `RespawnTier` — персональные респ-блоки.
- `game/GameEvent` — 12 случайных событий (enum с методами жизненного цикла).
- `loot/*` — весовые категории лута, миграция.
- `game/ChatChannel` — каналы чата (лобби/игра/спектейт).
- ~24 меню, editor-меню, `game/OfflineGuards` (AFK-моб при выходе).

## Что у escape уже совпадает с моделью core (переносится на общее)

| escape сейчас | Заменяется на из `mg-core` |
|---|---|
| `game/GameSession.Phase {WAITING, COUNTDOWN, RUNNING, ENDING}` | `GamePhase` (WAITING→LOBBY) |
| `game/MatchPlayer` (kills/quests/trades/ores…) | `MatchPlayer` + `DataKey`-счётчики в `Match` |
| `player/PlayerSnapshot` (snapshots/<uuid>.yml) | `PlayerSnapshot` ядра (тот же приём) |
| `arena/ArenaManager` + `sessionByPlayer` | `ArenaManager` / `ArenaService` ядра |
| `stats/StatsRepository` (SQLite) | `StatsService` (sqlite→mysql), `gameId="escape"` |
| `util/Msg` / `util/Keys` / `util/DebugLog` | те же утилиты, но из `mg-core` (один владелец) |
| `menu/Menu` + `MenuListener` | меню-фреймворк ядра |
| откат мира (`editedBlocks`, tracked entities/drops в `cleanup`) | `rememberBlock`/`trackEntity` ядра |
| BossBar-таймер, `ChatChannel` | HUD ядра + каналы (кандидат в core-фичу) |
| лобби = фаза WAITING + set `lobby` + `Arena.getLobby()` | лобби-фаза ядра |
| три роли-сета `lobby`/`playing`/`spectators` | `Match.players()` + `MatchPlayer.alive` + спектейт ядра |

## Стратегия: распилить `GameSession` на «движок» и «правила»

Сейчас `GameSession` смешивает две вещи:

1. **Инфраструктуру матча** — фазы, отсчёт, main-tick, роли, снапшоты, откат,
   таймер, cleanup. → это **уже есть в `mg-core`**, выбрасываем дубликат.
2. **Правила Escape** — генерация мира матча (`placeChests/placeContracts/
   spawnTraders/placeOres/placeTables`), зарплата, финальная битва, MVP, события,
   модификаторы. → это переезжает в **`EscapeGame extends Minigame`** и вспомогательные
   классы.

Отображение методов монолита на хуки:

| `GameSession.*` (escape) | Куда в новой модели |
|---|---|
| `startMatch()` мир-ген (`placeChests`…) | `EscapeGame.onStart(m)` |
| выдача кита/вилки/компаса/респ-блока | `EscapeGame.giveLoadout(m, p)` |
| `tick()` (зарплата, события, глоу-фаза, объявления) | `EscapeGame.onTick(m)` |
| `finalBattle()` (remaining==0) | внутри `onTick` по `remainingSeconds()==0` |
| `eliminate/handleDeath/finishElimination` + kill-credit | `onLethalDamage` + `onPlayerEliminated`, kill-credit в `Match.data` |
| `finish()` (MVP, запись побед) | `onEnd(m, result)` + `StatsService.record` |
| `cleanup()` (rollback, restore, снятие сущностей) | инфраструктура ядра + `onCleanup(m)` для escape-специфики (динамич. сундуки) |
| `startCountdown()` | отсчёт ядра (убрать) |

Escape-специфичное состояние (`editedBlocks`-детали динамических сундуков, кулдауны,
голосование модификаторов) → `Match.data()` через `DataKey`, а не поля класса.

## Порядок (внутри фазы 4)

1. **Подключить core как зависимость**, не удаляя старый код: `depend: [MgCore]`,
   `compileOnly(mg-api)`. Escape пока работает по-старому.
2. **Создать `EscapeGame extends Minigame`** с пустыми хуками и `descriptor()`
   (`id="escape"`).
3. **Переносить по одному сабсистему**, начиная с самого автономного:
   лут → контракты → торговцы → модификаторы → темы → респ-блоки → события. Каждый
   раз: вынести из `GameSession` в отдельный класс, дергать из хуков `EscapeGame`,
   состояние — в `Match.data()`.
4. **Переключить инфраструктуру на ядро**: снапшоты, откат, отсчёт, HUD, чат,
   статистика, меню — убрать escape-копии, звать core.
5. **Удалить выпотрошённый `GameSession`** и service-locator-обвязку `EscapePlugin`,
   оставив тонкий bootstrap + регистрацию `EscapeGame`.
6. **Сверить с эталоном**: старый escape jar рядом, прогон полного матча —
   генерация, зарплата, события, финальная битва, MVP, откат мира 1:1.

## Риски именно для escape

- **`ChatChannel` и respawn-блоки** могут оказаться достаточно общими, чтобы стать
  фичей core, а не escape-специфики. Решить при переносе (кандидаты в `mg-core`).
- **Богатый мир-ген в `cleanup`** (динамические сундуки, дропы, refill-таски) —
  самый хрупкий откат; переносить осторожно, сверять список tracked-объектов.
- **`GameEvent` как enum с логикой** — переложить на escape-сторону, ядро о нём не знает.
- **Объём.** 2149 строк + 24 меню + 6 листенеров. Это самая долгая фаза; делать
  инкрементально, держа игру запускаемой после каждого перенесённого сабсистема.

## Критерий готовности

Escape запускается поверх `mg-core`; `GameSession`-монолита нет; все уникальные
механики сохранены; откат мира и снапшоты — общие из ядра; статистика пишется в
общую БД под `gameId="escape"` и видна в сквозном профиле на хабе.
