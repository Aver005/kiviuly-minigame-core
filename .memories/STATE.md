# STATE
> Living snapshot — the most-updated file. Last updated: 2026-07-24

2026-07-24 — Escape: часть C (движок), **кусок 4 в основном ЗАКРЫТ**. Все четыре репо
собираются зелёными. Кусок 4 оказался НЕ «сносом дубликатов», а **достройкой** —
список памяти был оптимистичен (см. ниже). Осталось: пара решений владельца + рантайм-тест.

## Инфраструктура (сверено 2026-07-24)

- **Пути сменились: `E:\Projects\Me\` → `c:\Work\Minecraft\`.** Репо-соседи: `kiviuly-mg-core`
  (платформа, ветка `develop` — её миграция здесь, своей `refactor/*` НЕТ), `escape-reborn`,
  `skywars-reborn`, `skyblockwars-reborn`, `mcmgp-template`. Тестовый сервер — `C:\Servers\escape-server`.
- **Миграция игр живёт на ветке `refactor/Migrate-to-MG-core`** (у КАЖДОГО игрового репо, не в `main`).
  У платформы такой ветки нет — она на `develop`. Чтобы видеть мигрированное состояние игры —
  переключись на эту ветку в её репо.

## Текущий фокус

**Кусок 4 фактически выполнен как ДОСТРОЙКА (не снос).** `./gradlew build` зелёный во всех
репо. Рантайм по-прежнему не проверялся — тест в самом конце (решение владельца).

Что сделано в куске 4 (escape-reborn @ `refactor/Migrate-to-MG-core`):
- **Перенос команд.** `EscapeGame` не реализовывал `onCommand`/`tabComplete` → ~60 escape-подкоманд
  были МЁРТВЫ в рантайме (собиралось, но `/escape addchest`, `kit`, `loot`, `chestsetup`, `contract*`,
  `theme*`, `trader*`, `debug`, escape-`gui`, `set eventinterval` и т.д. проваливались). `EscapeGame`
  теперь делегирует в `EscapeCommand.handle/tab` (репрофилирован из TabExecutor в делегат, НЕ удалён).
- **Фикс смерти.** escape-`onDamage` (HIGH) был мёртв (ядро гасит леталь раньше), а `dropInventory`+
  `setHealth(20)` жили только в нём → лут не падал, HP не восстанавливался. Перенёс в `handleDeath`,
  мёртвый `onDamage` удалён, debug-путь де-дублирован.
- **Прочее:** `onReload`→escape-reload; `onArenaRemoved`; фикс прав `esc.admin`→`escape.admin`
  (иначе операторы не проходили гейт ядра); убраны 2 мёртвых импорта `PlayerSnapshot`.

## Что работает [DONE]

- **Платформа** `mg-api` + `mg-core`: движок матча, реестр арен (по папкам игр),
  тулкит, меню, статистика, реестр игр, команды с привязкой к игре, права. Собирается.
- **SkyWars** — тонкий плагин, свой `/sw`, право `sw.admin`. Собирается.
- **SkyBlockWars** — то же, `/sbw`, `sbw.admin`. Собирается.
- **Escape, часть A (тулкит)** — общий из `mg-api`; PDC-ключи в `EscapeKeys`,
  `pointAssistantCompass` в `EscapeItems`; `DebugLog` оставлен свой (8 игровых Cat).
- **Escape, часть B (арена)** — свой `Arena` удалён; фасад `EscapeArena` (числа/точки
  через «карманы», живые представления наборов точек); контент в `EscapeArenaConfig` +
  `EscapeArenaConfigs`; киты глобальные, арена хранит список разрешённых id.
- **Escape, часть C, куски 1–3** — `GameSession`→`EscapeRules` (выпотрошен, 2200→~1900,
  вырезано 19 движковых методов), 11 хуков платформы через `EscapeGame extends Minigame`
  (id="escape", право "esc.admin"), правила в матче через `DataKey<EscapeRules>`, ростер
  три-Set→`match.players()`+`alive`. `ArenaManager` escape → тонкий фасад над `core.arenas()`.
  `EscapePlugin` регистрирует игру в MgCore, команда через `core.commandFor(game)`.
  `ProtectionListener` удалён (дубликат ядра); `GameListener`/`ChatListener` оставлены
  (игро-специфичны).

## Что осталось [WIP/решения владельца] — Escape

1. **«Дубликаты» из старого списка НЕ удаляются — они обоснованы** (как `DebugLog`/откат):
   - `command/EscapeCommand` — репрофилирован в делегат, НЕ дубликат (ядро не покрывает escape-подкоманды).
   - `arena/ArenaCheck` — жив, зовётся escape `enable`/`check` (escape-специфичная валидация).
   - `player/PlayerSnapshot` — нужен для setup-режима (`ChestSetupManager`), ядро покрывает только матч.
   - `arena/SetupMarkers` — escape-специфичный менеджер точек (finalspawn/trader/chest-facing/структуры),
     `mg-api`-версия узкая и generic; совпадает лишь именем.
   - `arena/ArenaManager` — тонкий фасад (мост `Match`→`EscapeRules`, save+игро-конфиг, busy-проверка).
     Снос = ~94 вызова на `core.arenas()` + переезд удобств. **Решение владельца** (рекомендация: оставить).
2. **Punch-list — ВОССТАНОВЛЕН** (2026-07-24, +2 аддитивных хука в `mg-api Minigame`):
   `onEmptyCommand(p)` → `/escape` без арг открывает escape-меню; `canJoin(arena,p)` → busy-проверка
   входа на ВСЕХ путях; escape-лог вернулся под `/escape esclog` (ядровый лог — `/escape debuglog`).
   **Остаётся только** бедный `/escape stats` (4 строки vs 12) — чинится вместе со сквозной статистикой (п.4).
3. **Оффлайн-страж rejoin — ГОТОВ** (2026-07-24): 3 аддитивных хука ядра `keepOnDisconnect`/
   `onPlayerDisconnect`/`onPlayerReconnect`; `GameSession.onDisconnect/onReconnect`; `GameListener`
   quit→onDisconnect, join→onReconnect. `EscapeGame` подключил готовые `OfflineGuards` (были написаны,
   но осиротели при миграции). Заодно устранён критбаг выбывания (тихий `Match.eliminate(UUID)` — см. BUGS
   RESOLVED): и обычная смерть, и оффлайн-таймаут теперь метят ядровый alive → матч завершается.
4. **Статистика — СВЕДЕНА в общий `StatsService`** (2026-07-24, Design B «generic-счётчики»): контракт хранит
   произвольные именованные счётчики (таблица `stat_counters` uuid+stat→value, ники в `stat_players`),
   `Row` несёт Map + `counter(k)`; добавлены `set`/`max`. Ядровый бэкенд мигрирует старую широкую `stats`.
   Escape пишет в `core.stats()` (свой `StatsRepository` удалён), богатый `/escape stats` восстановлен через
   новый хук `Minigame.statsLines`. Двойной счёт снят хуком `recordsOwnStats()` (escape=true → ядро не
   авто-пишет `recordMatch`). SkyWars/SBW не затронуты (им базу пишет ядро авто, `StatsService` не трогают).

## Проверено только компиляцией [?]

**Рантайм не запускался ни разу, ни одной игры.** Вся миграция подтверждена только
`./gradlew build`. Сквозной прогон на сервере — в самом конце (см. `PLANS.md`).

## Последствия, которые всплывут при первом запуске [?]

- **Старые арены Escape не загрузятся** — формат сменился с папки из 4 файлов на
  `plugins/Escape/arenas/<ID>.yml` + `game/<ID>.yml`. Решение (пересоздать vs переносчик)
  **не принято** — зависит от числа реально настроенных арен у владельца.
- **PDC-namespace общих ключей сменился** `escape:` → `mgcore:`: ранее выданные маркеры
  разметки и спец-предметы Escape перестанут распознаваться — перевыдать.
