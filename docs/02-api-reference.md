# 02 — Справочник API (`mg-api`)

Контракты, против которых компилируются игры и хаб. Реализация — в `mg-core`.
**Этот документ отражает фактически собранный код** (после Фаз 0–1), а не проект.

Игровой плагин зависит только от `mg-api` (compileOnly) + `depend: [MgCore]` в
`plugin.yml`. Классы `mg-api` вложены в `MgCore.jar`, поэтому в рантайме их отдаёт
ядро через свой classloader — отдельный плагин для api не нужен.

## Состав модуля

```
ru.kiviuly.mg.api
├─ MgCore                 фасад платформы
├─ arena/  Arena, ArenaService, SetupMarkers
├─ game/   GamePhase, Match, MatchPlayer, MatchResult, Team,
│          Minigame (SPI), MinigameDescriptor
├─ data/   DataKey<T>
├─ stats/  StatsService (+ Row, LeaderboardEntry)
├─ net/    TransportService, QueueService
├─ util/   Msg, Items, Keys, DebugLog        ← общий тулкит
└─ menu/   Menu, AnvilInputMenu              ← общий GUI-каркас
```

> **Почему тулкит в api.** Фаза 1 показала: игро-коду нужны не только доменные
> контракты, но и `Msg` (i18n), `Items`, базовый `Menu`. Они статические и
> загружаются один раз (в jar ядра), поэтому разделяются всеми плагинами.

## MgCore — фасад

```java
public interface MgCore {
    void register(Minigame game);   // вызвать в onEnable игрового плагина
    ArenaService arenas();
    StatsService stats();
    TransportService transport();   // на одиночном сервере — no-op
}
```

Подключение игры:

```java
MgCore core = getServer().getServicesManager().load(MgCore.class);
if (core == null) { /* выключить плагин */ }
Msg.merge(this);                       // домешать свой messages.yml
core.register(new MyGame(this, core));
```

## Minigame — точка расширения

Абстрактный класс; **обязателен только `id()`**, остальное — с дефолтами.

| Хук | Когда | Дефолт |
|---|---|---|
| `String id()` | идентификатор игры (по нему статистика) | **абстрактный** |
| `String displayName()` | имя для HUD/сообщений | `id()` |
| `MinigameDescriptor descriptor()` | паспорт для реестра/селектора хаба | выводится из `id()`/`displayName()` |
| `onLobbyJoin(Match, Player)` | игрок вошёл в лобби | no-op |
| `allowLobbyPvp()` | разрешить урон в лобби | `false` |
| `onLobbyAttack(Match, victim, damager, damage)` | PvP-удар в лобби (разминка) | no-op |
| `onStart(Match)` | матч стартовал | no-op |
| `giveLoadout(Match, Player)` | стартовый набор каждому | no-op |
| `onTick(Match)` | каждую секунду матча | no-op |
| `onLethalDamage(Match, Player)` | летальный урон; `true` = выбить | `true` |
| `onPlayerEliminated(Match, MatchPlayer)` | игрок выбыл | no-op |
| `onPlayerRemoved(Match, UUID)` | игрок покинул сессию | no-op |
| `checkResult(Match)` | ядро зовёт на тике; не-null завершает матч | `m.defaultResult()` |
| `onEnd(Match, MatchResult)` | матч завершается | no-op |
| `onCleanup(Match)` | после отката/уборки | no-op |
| `scoreboardLines(Match, Player)` | доп. строки сайдбара | пусто |
| `onCommand(Player, String sub, String[])` | игро-подкоманда `/mg <sub>` | `false` |
| `tabComplete` / `helpLines` | подсказки/справка | пусто |
| `onReload()` | `/mg reload` | no-op |
| `onArenaCreated(Arena)` / `onArenaRemoved(String)` | арена создана/удалена | no-op |

## Match — контракт матча

Реализация — движок `GameSession` в ядре. Управляющие методы движка
(`addPlayer`/`removePlayer`/`forceStart`/`forceCleanup`) в контракт **не** входят.

```java
MgCore core();  Arena arena();  GamePhase phase();  boolean acceptsPlayers();
int elapsedSeconds();  int remainingSeconds();          // -1 = без лимита

Map<String,Object> data();                               // сырое состояние матча
<T> T get(DataKey<T> k);  <T> T get(DataKey<T> k, T def);  <T> void set(DataKey<T> k, T v);

Collection<MatchPlayer> players();  MatchPlayer player(UUID);  boolean hasPlayer(UUID);
Collection<Team> teams();  Team teamOf(UUID);            // FFA: по команде на игрока
List<Player> alivePlayers();  int aliveCount();  List<Player> onlinePlayers();

void rememberBlock(Block);  void rememberState(BlockState);  void trackEntity(Entity);
void eliminate(Player, boolean died);                    // игра может выбить сама
MatchResult defaultResult();
void broadcast(String key, TagResolver... resolvers);
```

## Arena — конфиг площадки

Конкретный класс (один файл `arenas/<id>.yml`), не интерфейс.

```java
String getId();  World getWorld();  Location getLobby();  List<Location> getSpawns();
int getMinPlayers/getMaxPlayers/getLobbyCountdownSeconds/getCountdownFullSeconds/getMatchDurationSeconds();
boolean isEnabled();                                     // + сеттеры ко всем
int getSetting(String key, int def);  void setSetting(String, int);   // игро-числа

// именованные группы точек с тегами (игро-разметка без правки контракта):
Map<Location,List<String>> spots(String group);
void addSpot(String group, Location, String tag);
boolean removeSpot(String group, Location);
Location spotAt(String group, Location);

Match getSession();  void setSession(Match);             // живой матч или null
```

> **`spots`** — механизм для игро-специфичной разметки. Пример: SkyWars хранит
> точки-сундуки как `spots("chest")`, где теги = id категорий лута. Маркеры любого
> типа ставит ядро (`SetupListener` → `addSpot(type, loc, tag)`), поэтому новый тип
> точек не требует правок ядра.

## ArenaService / StatsService

```java
interface ArenaService {
    Arena get(String id);  boolean exists(String id);
    Collection<Arena> all();  Set<String> ids();
    void save(Arena arena);                 // персист после игро-правок
    Match sessionOf(Player p);  boolean inGame(Player p);
    void join(Player p, Arena arena);  void leave(Player p);
}

interface StatsService {
    record Row(String name, int wins, int loses, int kills, int played) {}
    record LeaderboardEntry(UUID uuid, String name, int value, int rank) {}
    void recordMatch(UUID, String name, boolean won, int kills);   // async
    void add(UUID, String name, String column, int delta);
    void findByName(String name, Consumer<Row> callback);          // callback в main thread
    CompletableFuture<List<LeaderboardEntry>> top(String gameId, String stat, int n);
}
```

## Вспомогательные типы

```java
enum GamePhase { LOBBY, COUNTDOWN, RUNNING, ENDING }

class MatchPlayer { UUID getUuid(); String getName(); boolean isAlive(); int getKills(); … }

record MatchResult(List<UUID> winners) { of(UUID) / of(List) / draw() / hasWinner() }

interface Team { String id(); Component displayName(); TextColor color();
                 Set<UUID> members(); boolean isAlive(); }

record MinigameDescriptor(String id, Component displayName, ItemStack icon,
                          int minPlayers, int maxPlayers, boolean teamBased) {}

final class DataKey<T> { static <T> DataKey<T> of(String id, Class<T> type); String id(); Class<T> type(); }
```

Пример типизированного состояния:

```java
static final DataKey<Integer> ROUND = DataKey.of("round", Integer.class);
m.set(ROUND, 1);
int round = m.get(ROUND, 1);
```

## Тулкит (`util/`, `menu/`)

- **`Msg`** — общий каталог MiniMessage. Ядро зовёт `init(plugin)`, каждая игра —
  `merge(plugin)` (свой `messages.yml`). Поиск ключа идёт по каталогам в порядке
  добавления, поэтому ключи держи в неймспейсе игры (`skywars.*`).
  API: `raw/mm/get/getList/rawList/send/ph/phMm/phC/reload`.
- **`Items`** — сборка предметов, спец-предметы с PDC (`special/isSpecial/named/filler/flat/fromSpec`).
- **`Keys`** — `NamespacedKey` платформы (PDC-метки). Инициализирует ядро.
- **`DebugLog`** — категорированный лог (`Cat`), тумблер через `/mg debuglog`.
- **`Menu`** — базовый GUI (`InventoryHolder`, пагинация, `allowsInteraction`,
  `isProtectedSlot`). Клики маршрутизирует `MenuListener` **ядра** — игро-меню
  просто наследуют `Menu`, свой листенер регистрировать не нужно.
- **`AnvilInputMenu`** — ввод текста через наковальню (принимает любой `Plugin`).
- **`SetupMarkers`** — предметы-маркеры разметки точек арены.

## Сетевые контракты (заложены, реализация — Фаза 3+)

```java
interface TransportService { void send(Player, String server); void sendToGame(Player, String gameId); }
interface QueueService     { void enqueue(Player, String gameId); void dequeue(Player); int queued(String gameId); }
```

На одиночном сервере `transport()` — no-op (`LocalTransport`). `QueueService`
реализации пока не имеет (живёт на хабе).

## Чего ещё нет (осознанно)

- `MatchResult` до сих пор на `List<UUID>`, не на `Team` — перевод отложен до
  реального командного режима (ломающий рефактор движка).
- `Team` отдаётся движком как **FFA** (по команде на игрока); распределения 2v2 нет.
- Типизированных настроек `Arena` через `DataKey` нет — только `getSetting(String,int)`.
- `StatsService.top(gameId, …)` игнорирует `gameId` (хранилище одно-игровое до Фазы 2).

Как этим пользоваться на практике — [03-making-a-game.md](03-making-a-game.md).
