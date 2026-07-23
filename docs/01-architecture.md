# 01 — Архитектура

Как устроена платформа и почему. Правила конкретной игры сюда не входят — они
живут в наследнике `Minigame` (см. [03-making-a-game.md](03-making-a-game.md)).
Package root: `ru.kiviuly.mg`.

## Слои и модули

Один Gradle-мультимодуль отдаёт четыре артефакта. Зависимости идут строго в одну
сторону — всё смотрит на `mg-api`, `mg-api` не знает ни о ком.

```
                 ┌───────────────────────────┐
                 │          mg-api            │  чистые интерфейсы, без реализации
                 │  Arena, Match, MatchPlayer,│  (compileOnly для игр и хаба)
                 │  Team, MatchResult,        │
                 │  Minigame(SPI), DataKey,   │
                 │  StatsService, Transport…  │
                 └─────────────▲─────────────┘
        ┌──────────────────────┼───────────────────────┐
        │                      │                        │
 ┌──────┴──────┐        ┌──────┴──────┐          ┌──────┴──────┐
 │   mg-core   │        │   mg-hub    │          │  игры       │
 │ Paper-плагин│        │ Paper-плагин│          │ skywars,    │
 │ на игровом  │        │ на хабе     │          │ sbw, escape │
 │ сервере     │        │             │          │ (depend:    │
 │ (движок)    │        │             │          │  MgCore)    │
 └─────────────┘        └─────────────┘          └─────────────┘
        │                                                │
        └────────────── реализует ◄──── регистрирует ────┘
                    Minigame через ServicesManager

 ┌─────────────┐
 │  mg-proxy   │  Velocity-плагин: маршрутизация/очередь (опционально, фаза 3)
 └─────────────┘
```

| Модуль | Артефакт | Где крутится | Ответственность |
|---|---|---|---|
| `mg-api` | jar | нигде (compile-time) | Интерфейсы и контракты. Ничего Bukkit-тяжёлого, минимум зависимостей. Игры и хаб компилируются против него. |
| `mg-core` | Paper-плагин | каждый **игровой** сервер | Движок: `ArenaManager`, `GameSession`, снапшоты, меню, HUD, откат мира, `StatsService`-провайдер, реестр `Minigame`. Вынутый из «wars» mcmgp-template. |
| `mg-hub` | Paper-плагин | сервер-**хаб** | Селектор игр, показ сквозного профиля, PvP/дуэли в лобби, `TransportService` (отправка на игровой сервер). |
| `mg-proxy` | Velocity-плагин | **прокси** | Авторитет очереди/маршрутизации, presence. Опционально; на старте можно обойтись plugin-messaging. |

**Почему runtime-плагин, а не shaded-библиотека.** Требование «одна статистика и
один профиль на всю сеть» требует единственного владельца хранилища. Плагин-core
даёт это и попутно решает проблему коллизии статических утилит (`Msg`/`Keys`/
`DebugLog`): владелец один — `mg-core`, игры берут инстанс через фасад, а не тащат
свою копию (сегодня в двух «wars» это дублирующиеся static-синглтоны).

## Механизм соединения (SPI через Bukkit ServicesManager)

Игра **не** зависит от `mg-core` по коду — только от `mg-api`. В рантайме они
находят друг друга через `ServicesManager` (идиоматичный Bukkit-способ обмена
сервисами между плагинами), без shading.

`mg-core` на `onEnable` публикует фасад:

```java
// mg-core
MgCore facade = new CoreRuntime(this);
getServer().getServicesManager()
    .register(MgCore.class, facade, this, ServicePriority.Normal);
```

Игровой плагин на `onEnable` его забирает и регистрирует свою игру:

```java
// skywars-reborn (plugin.yml: depend: [MgCore])
public final class SkyWarsPlugin extends JavaPlugin {
    @Override public void onEnable() {
        MgCore core = getServer().getServicesManager().load(MgCore.class);
        if (core == null) { getLogger().severe("MgCore not found"); setEnabled(false); return; }
        core.register(new SkyWarsGame(this));   // отдаёт Minigame + MinigameDescriptor
    }
}
```

Дальше `mg-core` сам: грузит арены этой игры, гоняет `GameSession`, роутит её
команды, пишет её итоги в общий `StatsService`. `MinigameDescriptor` (id, имя,
иконка, min/max, папка арен) публикуется в общую БД → **хаб автоматически видит
игру в селекторе**, не имея её кода.

> `depend: [MgCore]` в `plugin.yml` игры гарантирует порядок загрузки: core уже
> включён к моменту `onEnable` игры, сервис доступен.

## Несущая модель

Три понятия — как в текущих «wars», но подняты до интерфейсов в `mg-api`
(сигнатуры — в [02-api-reference.md](02-api-reference.md)):

- **`Arena`** — статическая конфигурация одной площадки. Знает только общее: мир,
  лобби, точки спавна, `min/max-players`, тайминги. Числа самой игры хранит в
  типизированных `DataKey`-ключах (замена нынешней stringly-typed `settings`-мапы).
  Один файл на арену: `arenas/<id>.yml`.
- **`Match`** (реализация — `GameSession`) — один матч. Создаётся при первом входе
  игрока в лобби, умирает в `cleanup()`. Владеет всем, что живёт и умирает с
  матчем: `MatchPlayer`-ы, `GamePhase`, таймеры, набор изменённых блоков (откат),
  заспавненные сущности, `data()` — типизированное состояние игры, команды (`Team`).
- **`Minigame`** — правила твоей игры. Логика **без состояния**: хуки получают
  `Match` и работают через его API. Одна инстанция на плагин, регистрируется через
  `MgCore.register(...)`.

Разделение: **ядро ведёт матч, `Minigame` решает, что этот матч значит.**

### Что добавляем к текущей модели

| Новое | Зачем | Сейчас в «wars» |
|---|---|---|
| `Team` (первокласс) | командные режимы, дуэли в лобби, `MatchResult` по командам | нет; победители — просто `List<UUID>` |
| `DataKey<T>` | типобезопасные `Arena`-настройки и `Match.data()` | `Map<String,Integer>` / `Map<String,Object>` по строкам |
| `StatsService` c `top()` + MySQL | сквозные лидерборды на всю сеть | только `findByName` из локального SQLite |
| `TransportService` / `QueueService` | межсерверная отправка и матчмейкинг | нет |
| `MinigameDescriptor` | регистрация игры + карточка в селекторе хаба | id/displayName зашиты в `Minigame` |

## Жизненный цикл матча

Не меняется относительно текущего движка «wars» — переносим как есть. Одна
`BukkitTask` на 1 Гц гоняет и отсчёт, и матч через `switch(phase)`.

```
   join  (первый игрок создаёт GameSession)
        │
        ▼
   ┌──────────┐   набран min-players     ┌────────────┐
   │  LOBBY   │ ───────────────────────► │  COUNTDOWN │
   │ ожидание │                          │   отсчёт   │
   └──────────┘ ◄── игроков стало < min ─└────────────┘
        ▲         (отсчёт отменён)              │ отсчёт вышел
        │                                       │ (короче при полном лобби)
        │                                       ▼
        │                                 ┌────────────┐
        │  return-to-lobby:               │  RUNNING   │
        │  restore игроков из снапшота     │  матч идёт │
        │                                 └────────────┘
        │                                       │ checkResult(m) != null
        │                                       ▼
        │                                 ┌────────────┐
        └──────────────────────────────  │  ENDING    │
           cleanup():                     │  итоги +   │
           - откат изменённых блоков       │  cleanup   │
           - удаление tracked-сущностей    └────────────┘
           - restore всех снапшотов
```

Что делает движок на переходах и какие хуки зовёт (`m` — `Match`):

| Фаза / переход | Движок | Хук `Minigame` |
|---|---|---|
| Вход в `LOBBY` | снапшот игрока (`save`+`clear`), телепорт в лобби | `onLobbyJoin(m, p)` |
| `LOBBY → COUNTDOWN` | набран `min-players`; при полном лобби берёт `countdown-full-seconds` | — |
| `COUNTDOWN → RUNNING` | телепорт на спавны, `SURVIVAL`, инвентарь очищен, разбивка по `Team` | `onStart(m)`, затем `giveLoadout(m, p)` каждому |
| `RUNNING` (тик 1 Гц) | таймер, обновление HUD | `onTick(m)`; `checkResult(m)` |
| гибель/выбывание | fake death → spectator | `onPlayerEliminated(m, mp)` |
| `RUNNING → ENDING` | зафиксирован `MatchResult` | `onEnd(m, result)` |
| `ENDING` (cleanup) | откат блоков, удаление сущностей, `restore` игроков, запись статистики | `onCleanup(m)` |

`checkResult(m)` вызывается ядром и **завершает матч, как только вернёт не-null**.
По умолчанию — `m.defaultResult()`: последняя живая команда (или игрок), либо ничья
по таймауту. Переопредели, если условие победы другое.

## Инварианты (обещания платформы)

Ровно те же, что держат текущие «wars» — переносятся в `mg-core` и остаются
обязательными для игр:

1. **Откат мира.** Перед ЛЮБЫМ изменением блока — `match.rememberBlock(block)`.
   Любая заспавненная сущность — `match.trackEntity(entity)`. `cleanup()` откатывает
   блоки и удаляет сущности. Не запомнил — не откатится.
2. **Целость игрока.** Вход в матч = `PlayerSnapshot.save` + `clear`. Любой выход
   (leave / гибель / cleanup / восстановление при заходе на сервер) заканчивается
   `restore`. Игрок не уносит игровые предметы и не теряет свои. Файл снапшота на
   диске = страховка от краша.
3. **Тексты — только из `messages.yml`** через `Msg` (MiniMessage). Ни одной строки
   для игрока в Java.
4. **Данные предметов/сущностей — только PDC** (`util/Keys`), никогда не парсить
   имена/лор.
5. **Вся игровая логика — main thread.** Async только у хранилища (`StatsService`).
   Никаких async-обращений к Bukkit API.

## Многопоточность

Игровая логика — main thread. Async — только запись/чтение статистики (`StatsService`)
и сетевые обращения (транспорт/очередь на хабе). Folia не поддерживается.

## Где что лежит (runtime, игровой сервер)

```
plugins/MgCore/
├── config.yml            общие числа/флаги ядра
├── messages.yml          тексты ядра (MiniMessage)
├── arenas/<id>.yml       по файлу на арену (генерик-часть)
├── snapshots/<uuid>.yml  снапшоты игроков в матче
├── storage.yml           бэкенд статистики: sqlite | mysql (+ креды)
└── logs/debug-*.log      выгрузки debuglog

plugins/SkyWars/          тонкий плагин-игра
├── messages.yml          тексты игры
├── game/<ARENA>.yml      game-specific конфиг арены (если нужен)
└── kits.yml, loot.yml    контент игры
```

Разбор конкретных интерфейсов — [02-api-reference.md](02-api-reference.md).
Сеть, хаб и транспорт — [04-cross-server.md](04-cross-server.md).
