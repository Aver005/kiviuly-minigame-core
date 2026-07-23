# 05 — Хранилище и статистика

Статистика — единственная часть, где топология (single-server vs сеть) заставляет
менять реализацию. Поэтому она спрятана за интерфейсом `StatsService`, а бэкенд
выбирается конфигом. Игры и хаб про бэкенд не знают.

## Текущее состояние (что есть в «wars»)

Все три игры пишут в **локальный SQLite** `stats.db` через `StatsRepository`:
одна таблица `stats`, колонки `wins/loses/kills/played`, async-запись, чтение с
callback на main thread, whitelist колонок против инъекций. Умеет только
`findByName`. Лидербордов и top-N нет.

Проблема для сети: локальный SQLite у каждого плагина = статистика **не** сквозная.
Игрок на сервере skywars и на сервере sbw пишет в разные файлы; хаб не видит общего
профиля.

## Решение: `StatsService` + сменный бэкенд

```java
public interface StatsService {
    CompletableFuture<Profile> profile(UUID player);
    void record(UUID player, String gameId, StatDelta delta);          // async
    CompletableFuture<List<LeaderboardEntry>> top(String gameId, String stat, int n);
}
```

Два бэкенда, выбор — в `storage.yml`:

```yaml
# plugins/MgCore/storage.yml
backend: sqlite            # sqlite | mysql
sqlite:
  file: stats.db
mysql:
  host: 127.0.0.1
  port: 3306
  database: kiviuly
  user: mg
  password: "***"
  pool-size: 8
```

| Бэкенд | Когда | Плюсы | Минусы |
|---|---|---|---|
| `sqlite` | dev / single-server | ноль зависимостей (драйвер в Paper), работает сразу | не разделяется между серверами |
| `mysql` | сеть | сквозная статистика и профиль на всю сеть | нужен драйвер + пул (новая зависимость) |

`SqliteStatsBackend` — по сути текущий `StatsRepository`, обобщённый под мульти-игру.
`MySqlStatsBackend` — тот же контракт поверх общей БД.

## Схема БД (проект)

Мульти-игровая: статистика ключуется парой (игрок, игра), плюс произвольные
кастомные счётчики.

```sql
-- игроки (для отображения имени/last-seen)
CREATE TABLE players (
    uuid       CHAR(36) PRIMARY KEY,
    name       VARCHAR(16) NOT NULL,
    last_seen  BIGINT NOT NULL
);

-- статистика на (игрок, игра)
CREATE TABLE game_stats (
    uuid    CHAR(36) NOT NULL,
    game    VARCHAR(32) NOT NULL,     -- MinigameDescriptor.id: "skywars", "sbw", "escape"
    wins    INT NOT NULL DEFAULT 0,
    losses  INT NOT NULL DEFAULT 0,
    kills   INT NOT NULL DEFAULT 0,
    played  INT NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, game),
    INDEX idx_game_wins  (game, wins),
    INDEX idx_game_kills (game, kills)
);

-- произвольные счётчики игры (не хардкодим колонки под каждую игру)
CREATE TABLE game_stats_custom (
    uuid  CHAR(36) NOT NULL,
    game  VARCHAR(32) NOT NULL,
    stat  VARCHAR(48) NOT NULL,       -- "chests_opened", "quests_done" и т.п.
    value INT NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, game, stat),
    INDEX idx_game_stat (game, stat, value)
);

-- зарегистрированные игры (публикует mg-core при register) — хаб читает для селектора
CREATE TABLE games (
    id           VARCHAR(32) PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL,
    updated_at   BIGINT NOT NULL
);
```

Индексы `(game, wins)` / `(game, stat, value)` дают дешёвый `top(...)` для лидербордов.

> **Лидерборд** — то, чего сейчас нет ни в одной игре. `top("skywars","wins",10)` =
> `SELECT ... ORDER BY wins DESC LIMIT 10` по индексу. UI лидерборда живёт на хабе.

## Инкремент и запись

- `played` инкрементит **ядро** автоматически на входе в матч/на старте.
- Победы/поражения/убийства/кастом — начисляет игра из `onEnd`/`onPlayerEliminated`
  через `record(uuid, gameId, StatDelta)`.
- Запись — **async** (как сейчас), никакого SQL на main thread. Чтение
  (`profile`/`top`) — `CompletableFuture`, результат применяй на main thread.

```java
// в onEnd победителю:
core.stats().record(winner, "skywars",
    new StatDelta(/*wins*/1, /*losses*/0, /*kills*/0, /*played*/0, Map.of()));

// кастомный счётчик:
core.stats().record(p, "skywars",
    new StatDelta(0,0,0,0, Map.of("chests_opened", 3)));
```

## Инварианты хранилища

- Никаких строк SQL вне бэкендов `StatsService`. Игра знает только интерфейс.
- Имена колонок/кастом-статов — из whitelist/параметров, не из пользовательского
  ввода напрямую (как в текущем `StatsRepository`).
- `gameId` = `MinigameDescriptor.id`, стабилен во времени (переименование ломает
  историю).

## Зависимость MySQL и «ноль зависимостей»

Текущие игры гордятся нулём внешних зависимостей (драйвер SQLite берётся из Paper).
MySQL-бэкенд добавляет драйвер + пул (напр. HikariCP + MariaDB-драйвер). Решение —
**шейдить их только в `mg-core`**, не в игры: игра по-прежнему зависит лишь от
`mg-api`. Обсуждение — [08-decisions.md](08-decisions.md), ADR-6.

Дальше: план внедрения — [06-roadmap.md](06-roadmap.md).
