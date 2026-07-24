# LEARNINGS
> Gotchas & non-obvious facts that already cost someone time. Last updated: 2026-07-24

## Платформа

- **`mg-api` — это НЕ только контракты.** Там же лежит тулкит: `Msg`, `Items`, `Keys`,
  `DebugLog`, `Menu`, `AnvilInputMenu`, `SetupMarkers`. Так сделано намеренно: миграция
  первой игры показала, что игро-коду нужны не только `Match`/`Arena`, но и i18n/меню/
  предметы. Без этого игра не собирается против одного `mg-api`.
- **`MgCore.jar` вкладывает в себя классы `mg-api`** (`from(zipTree(...))` в
  `mg-core/build.gradle.kts`). Игры берут `mg-api` как `compileOnly` — в их jar его нет,
  в рантайме отдаёт ядро через свой classloader. Уберёшь вкладывание — получишь
  `ClassNotFoundException` у всех игр.
- **`Msg` — общий статический каталог.** Ядро зовёт `Msg.init(plugin)`, игра —
  **`Msg.merge(plugin)`**. Если игра вызовет `init`, она **сотрёт сообщения ядра**.
  Ключи держать в неймспейсе игры (`skywars.*`), иначе перекроют ядровые.
- **Игра НЕ должна регистрировать свой `MenuListener`.** Меню наследуют общий
  `mg-api Menu`, клики маршрутизирует `MenuListener` ядра. Свой листенер = клики
  обработаются **дважды**.
- **`arenas()`/`stats()` у `MgCorePlugin` возвращают КОНКРЕТНЫЕ типы**
  (`ArenaManager`/`StatsRepository`), а не интерфейсы — это ковариантное
  переопределение `MgCore`. Внутренний код ядра пользуется методами сверх контракта
  (`bind`, `create`, `delete`). Сузишь до интерфейса — ядро перестанет компилироваться.
- **Id арены НЕ глобально уникален.** Ключ — пара (игра, id): `ARENA1` может быть и у
  SkyWars, и у SkyBlockWars. `ArenaService.get(String)` возвращает арену только если id
  однозначен, иначе `null` — используй `get(gameId, id)` или `findById(id)`.
- **Арены лежат в папке КАЖДОЙ игры** (`plugins/<Игра>/arenas/<ID>.yml`), а не у ядра.
  Ядро узнаёт папку через `Minigame.dataFolder()`. В папке ядра только `_unowned`.
- **`MinigameCommand` умеет два режима**: `owner == null` — платформенная `/mg` (все
  арены, требует `mg.admin`), `owner != null` — команда игры (только её арены,
  делегирует подкоманды только ей). Один класс, разное поведение.

## Escape (миграция)

- **`EscapeRules` — это бывший `GameSession`**, выпотрошенный. Не ищи `GameSession` в
  Escape: класс переименован, движок вырезан, остались правила + 11 хуков.
- **`MatchPlayer` Escape переименован в `EscapePlayerData`** — иначе конфликт имён с
  платформенным `MatchPlayer` в файлах, где нужны оба.
- **Escape сознательно оставил СВОЙ `DebugLog`.** У него 8 игровых категорий
  (`CHEST`, `CONTRACT`, `THEME`, `SHOP`, `RESPAWN`, `GUARD`, `MECH`, `EVENT`), которых
  нет в платформенном enum. Тащить их в ядро неправильно, схлопывать в `GAME` — терять
  гранулярность. Дублирование диагностики здесь оправдано.
- **Escape сознательно оставил СВОЙ откат блоков** (`editedBlocks` + `onCleanup`).
  Он трёхпроходный и чинит стыки решёток/заборов через `fixConnectingNeighbors`;
  ядровой одношаговый `BlockState.update` оставляет щели, через которые пролезают.
  Ядро при этом всё равно откатывает то, что ломали игроки — это дополняет, не мешает.
- **Фасад `EscapeArena` отдаёт ЖИВЫЕ представления** наборов точек: `add`/`put`/`remove`
  пишут прямо в арену. Поэтому ~180 старых вызовов переехали без правки логики.
  После правок арену надо сохранить: `plugin.arenas().save(arena)`.
- **Булев флаг в «кармане чисел»** хранится как 0/1 (`dynamic-chests`) — карман
  типизирован как `Map<String,Integer>`.
- **Квоты торговцев** живут в том же кармане под префиксом `trader-quota.<ТИП>`.
- **Повороты сундуков** — отдельная группа точек `chest-facing` (ярлык = сторона),
  чтобы не мешаться с категориями лута в группе `chest`.

## Команды и события платформы (грабли миграции, 2026-07-24)

- **Игро-специфичные подкоманды — через `Minigame.onCommand`, НЕ через свой executor.**
  Команду ведёт `core.commandFor(game)` (ядровая `MinigameCommand`). Она сама обрабатывает
  каркас и отдаёт остальное в `owner.onCommand(p, sub, args)` (дефолт `return false`). Если игра
  не переопределит хук — её подкоманды МЁРТВЫ (собирается зелёным, в рантайме `unknown-sub`).
  Это и случилось с Escape: `EscapeGame` не имел `onCommand` → ~60 команд не работали.
- **Порядок ядровой `MinigameCommand`:** сначала САМА перехватывает `join/leave/stats/help`
  (до гейта прав) и `list/reload/save/debuglog` (после гейта) — их игра `onCommand`-ом НЕ
  перекроет. Потом зовёт `owner.onCommand` (тут игра может вернуть `true` и перехватить всё
  прочее, включая `create/enable/gui/set/...`). Возврат `false` → доводит ядро.
  Последствие: `stats` и ядровый `debuglog` у игры не переопределяемы. Пустая команда и вход —
  теперь переопределяемы новыми хуками `Minigame.onEmptyCommand(p)` и `canJoin(arena,p)` (2026-07-24);
  свой лог игра вешает на своё имя саб-команды (Escape — `/escape esclog`, ядровый — `/escape debuglog`).
- **`Minigame.adminPermission()` должен совпадать с plugin.yml.** Ядро гейтит `mg.admin ||
  adminPermission()`. Escape вернул `esc.admin`, а объявлен/используется `escape.admin` →
  операторы (default op на `escape.admin`) НЕ проходили. Правило: короткий узел должен быть ОБЪЯВЛЕН.
- **Летальный урон ведёт ХУК ядра, не листенер игры.** Ядро на `EntityDamageEvent` (NORMAL,
  ignoreCancelled) само определяет леталь, `setCancelled(true)` и зовёт `onLethalDamage` →
  правила. Свой `onDamage` на HIGH с `ignoreCancelled` НЕ сработает (ядро уже погасило).
  Всё, что делалось в нём (у Escape — `dropInventory`+`setHealth`), надо переносить в реализацию
  хука/`handleDeath`. Спектаторов ядро тоже делает неуязвимыми (`mp==null||!alive` → cancel).
- **Двойной откат блоков УПОРЯДОЧЕН верно:** ядро откатывает свои `editedBlocks` в `cleanup()`
  ДО `game.onCleanup`, поэтому escape-3-проход идёт последним и чинит стыки. Не переворачивай порядок.
- **Что осталось СВОИМ у Escape и почему (не дубликаты):** `EscapeCommand` (делегат подкоманд),
  `ArenaCheck` (escape-валидация), `PlayerSnapshot` (setup-режим), `SetupMarkers` (escape-точки),
  `ArenaManager` (фасад-мост `Match`→`EscapeRules`). Все живые и обоснованные.

## Статистика (Design B «generic-счётчики», 2026-07-24)

- **Модель — произвольные именованные счётчики, не фикс-колонки.** `StatsService.Row` несёт
  `Map<String,Integer> counters` + `counter(k)` (база wins/loses/kills/played — удобными методами).
  Бэкенд: `stat_counters(uuid,stat,value)` + `stat_players(uuid,name)`. Новый счётчик = просто новое
  имя в `add/set/max`, схему менять НЕ надо. Ложится на будущий MySQL.
- **Ядро САМО пишет базовый `recordMatch` в конце матча** (`GameSession.recordStats` для всех игроков).
  Простым играм (SkyWars/SBW) этого хватает — они `StatsService` вообще не трогают. Если игра пишет
  СВОЮ статистику в ту же БД (Escape: свои wins/loses/kills/ores/…), будет ЗАДВОЕНИЕ. Решение — хук
  `Minigame.recordsOwnStats()` → `true`: ядро тогда не авто-пишет. Escape так и делает.
- **Богатый `/<cmd> stats` — через хук `Minigame.statsLines(viewer, row)`** (движок печатает базу +
  игро-строки). `stats` ядро перехватывает до `onCommand`, поэтому иначе игре его не показать.
- **Escape больше НЕ держит свою `stats.db`** — пишет в `core.stats()`. Старый `stats/StatsRepository`
  удалён. `recordGameKills` разложен на `set("last_game_kills")`+`max("best_game_kills")`.

## Инструментальное

- **Не вставляй импорт через `sed '/^package/a ...'`** — `\n` в шаблоне вставляется
  литералом и ломает строку `package`. Уже наступали: пришлось чинить 30+ файлов awk-ом.
- **Bash heredoc рвётся на больших Java-вставках** с кавычками. Надёжнее записать
  вставку отдельным файлом (Write) и вставить коротким python-скриптом.
