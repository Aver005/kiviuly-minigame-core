# 05 — Хранилище и статистика

Статистика — единственная часть, где топология (single-server vs сеть) заставляет
менять реализацию. Поэтому она спрятана за интерфейсом `StatsService`, а бэкенд
выбирается конфигом. Игры и хаб про бэкенд не знают.

> **РЕАЛИЗОВАНО (2026-07-24, SQLite single-server): Design B «generic-счётчики».**
> Модель — произвольные именованные счётчики на игрока (`wins/loses/kills/played` — просто
> базовые имена, escape добавляет `ores_mined`/`quests_completed`/…). Ниже секции «проект»
> оставлены как контекст, но фактический контракт/схема — в блоках ниже с пометкой «факт».
> Ядро САМО пишет `recordMatch` в конце матча; игра, что ведёт свою статистику, помечается
> `Minigame.recordsOwnStats()` → `true` (иначе задвоение). Богатый вывод `/<cmd> stats` — через
> хук `Minigame.statsLines`. MySQL-бэкенд (сквозная сеть, ключ по `gameId`) — будущее, роадмап.

## Текущее состояние (что есть в «wars»)

Все три игры пишут в **локальный SQLite** `stats.db` через `StatsRepository`:
одна таблица `stats`, колонки `wins/loses/kills/played`, async-запись, чтение с
callback на main thread, whitelist колонок против инъекций. Умеет только
`findByName`. Лидербордов и top-N нет.

Проблема для сети: локальный SQLite у каждого плагина = статистика **не** сквозная.
Игрок на сервере skywars и на сервере sbw пишет в разные файлы; хаб не видит общего
профиля.

## Решение: `StatsService` + сменный бэкенд

**Факт (реализованный контракт):**

```java
public interface StatsService {
    void recordMatch(UUID uuid, String name, boolean won, int kills);   // база: +played +wins/loses +kills
    void add(UUID uuid, String name, String column, int delta);         // произвольный счётчик += delta
    void set(UUID uuid, String name, String column, int value);
    void max(UUID uuid, String name, String column, int value);         // рекорды: col = max(col, value)
    void findByName(String name, Consumer<Row> callback);               // Row: name + Map<String,Integer> counters
    CompletableFuture<List<LeaderboardEntry>> top(String gameId, String stat, int n);
}
```

`Row` несёт карту счётчиков + удобные `wins()/loses()/kills()/played()` и `counter(k)`.

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

## Схема БД

**Факт (SQLite, реализовано):** единая модель счётчиков — БЕЗ фикс-колонок под игру. Любой
счётчик (базовый или игро-специфичный) — строка в `stat_counters`. Whitelist не нужен: `stat` —
параметризованное ЗНАЧЕНИЕ, не имя колонки.

```sql
CREATE TABLE stat_players  (uuid TEXT PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE stat_counters (uuid TEXT NOT NULL, stat TEXT NOT NULL,
                            value INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (uuid, stat));
```

`add`/`set`/`max` — upsert через `ON CONFLICT(uuid,stat) DO UPDATE`. `findByName` собирает Map
счётчиков join-ом. Ядро при открытии мигрирует старую широкую таблицу `stats` (wins/loses/kills/
played) в эту модель и дропает её. Пока БД одно-игровая, `gameId` в `top(...)` игнорируется.

**Проект (MySQL, сеть — будущее):** к `stat_counters` добавится колонка `game` (ключ станет
`(uuid, game, stat)`), плюс таблицы `players(last_seen)` и `games` (реестр для селектора хаба),
индексы `(game, stat, value)` под дешёвый `top`. Контракт `StatsService` менять не придётся —
только второй бэкенд.

> **Лидерборд** — то, чего сейчас нет ни в одной игре. `top("skywars","wins",10)` =
> `SELECT ... ORDER BY wins DESC LIMIT 10` по индексу. UI лидерборда живёт на хабе.

## Инкремент и запись

- **База (`played/wins/loses/kills`)** — ядро пишет `recordMatch` для каждого игрока в конце
  матча (`GameSession.recordStats`). Простым играм (SkyWars/SBW) этого хватает — они `StatsService`
  вообще не трогают.
- **Игра со своей статистикой** помечается `recordsOwnStats()`→`true` (ядро тогда НЕ авто-пишет —
  иначе задвоение) и сама начисляет всё через `add/set/max` (Escape так и делает).
- Запись — **async**, никакого SQL на main thread. Чтение (`findByName`/`top`) — callback/
  `CompletableFuture`, результат применяй на main thread.

```java
// простой счётчик (из любого хука/листенера игры):
core.stats().add(p.getUniqueId(), p.getName(), "chests_opened", 3);

// рекорд лучшего матча:
core.stats().max(uuid, name, "best_game_kills", kills);
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
