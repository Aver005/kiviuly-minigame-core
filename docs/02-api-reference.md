# 02 — Справочник API (`mg-api`)

Контракты, против которых компилируются игры и хаб. Реализация — в `mg-core`/`mg-hub`.
Сигнатуры ниже — **проектные**: имена согласованы с текущими «wars», отличия помечены.
Пока это дизайн, не финальный код.

Пакеты:

```
ru.kiviuly.mg.api
├─ arena/     Arena, ArenaService
├─ game/      Match, MatchPlayer, MatchResult, GamePhase, Team, Minigame, MinigameDescriptor
├─ data/      DataKey
├─ stats/     StatsService, Profile, StatDelta, LeaderboardEntry
├─ net/       TransportService, QueueService
└─ MgCore     фасад
```

## Жизненный цикл

```java
public enum GamePhase { LOBBY, COUNTDOWN, RUNNING, ENDING }
```

Без изменений относительно текущего `GamePhase`.

## Arena

Статический конфиг площадки. Игро-независимый.

```java
public interface Arena {
    String id();
    World world();
    Location lobby();
    List<Location> spawns();
    int minPlayers();
    int maxPlayers();

    // типизированные game-specific настройки (замена stringly-typed settings-мапы)
    <T> T setting(DataKey<T> key);
    <T> T setting(DataKey<T> key, T def);
    <T> void setSetting(DataKey<T> key, T value);

    Optional<Match> match();   // живой матч на этой арене, если есть
}
```

> **Отличие от «wars»:** сейчас `getSetting(String, int)` возвращает только `int` из
> `Map<String,Integer>`. `DataKey<T>` даёт любой тип и compile-time проверку.

## Match

Один матч. Реализация в `mg-core` — `GameSession` (движок). Игровой код видит
`Match` как контракт.

```java
public interface Match {
    Arena arena();
    GamePhase phase();
    long elapsedSeconds();
    long remainingSeconds();          // при match-duration > 0

    Collection<MatchPlayer> players();
    List<Player> alivePlayers();
    int aliveCount();

    Collection<Team> teams();          // ← НОВОЕ; в FFA — по команде на игрока
    Team teamOf(UUID player);

    // инварианты отката (см. 01-architecture.md)
    void rememberBlock(Block block);
    void trackEntity(Entity entity);

    // типизированное состояние матча (замена Map<String,Object> data())
    <T> T get(DataKey<T> key);
    <T> T get(DataKey<T> key, T def);
    <T> void set(DataKey<T> key, T value);

    MatchResult defaultResult();       // последняя живая команда / ничья по таймауту
    MgCore core();
}
```

## MatchPlayer

Состояние конкретного игрока в конкретном матче.

```java
public interface MatchPlayer {
    UUID uuid();
    String name();
    boolean alive();
    int kills();
    Team team();                       // ← НОВОЕ
    Optional<Player> online();         // Bukkit-игрок, если онлайн
}
```

Свои per-player счётчики держи в `Match.set(key, ...)` под ключом с UUID, не в полях
`Minigame`.

## Team ← НОВОЕ

Первоклассная команда. В FFA-играх каждый игрок — своя команда из одного (движок
создаёт их автоматически), так что игровой код работает единообразно.

```java
public interface Team {
    String id();
    Component displayName();
    TextColor color();
    Set<UUID> members();
    boolean isAlive();                 // жива, пока жив хоть один участник
}
```

## MatchResult

Итог матча. Победители — команды (в FFA команда из одного).

```java
public record MatchResult(List<Team> winners) {
    public static MatchResult of(Team winner)      { return new MatchResult(List.of(winner)); }
    public static MatchResult draw()               { return new MatchResult(List.of()); }
    public boolean hasWinner()                     { return !winners.isEmpty(); }
}
```

> **Отличие от «wars»:** сейчас `winners` — `List<UUID>`. Переводим на `List<Team>`;
> для FFA обёртка тривиальна.

## Minigame — шов расширения

Абстрактный класс, который наследует каждая игра. Логика **без состояния** — всё
состояние в `Match`. Одна инстанция на плагин.

```java
public abstract class Minigame {

    public abstract MinigameDescriptor descriptor();  // id, имя, иконка, min/max, папка арен

    // --- лобби / старт ---
    public void onLobbyJoin(Match m, Player p) {}
    public boolean allowLobbyPvp(Match m) { return false; }
    public void assignTeams(Match m) {}                // по умолчанию FFA (по команде на игрока)
    public void onStart(Match m) {}
    public void giveLoadout(Match m, Player p) {}

    // --- ход матча ---
    public void onTick(Match m) {}
    public boolean onLethalDamage(Match m, Player p) { return true; } // true = выбыл
    public void onPlayerEliminated(Match m, MatchPlayer mp) {}
    public MatchResult checkResult(Match m) { return m.defaultResult(); }

    // --- финал / уборка ---
    public void onEnd(Match m, MatchResult r) {}
    public void onCleanup(Match m) {}

    // --- HUD / команды ---
    public List<Component> scoreboardLines(Match m, Player viewer) { return List.of(); }
    public List<String> onCommand(CommandSender s, String[] args) { return null; }
    public List<String> tabComplete(CommandSender s, String[] args) { return List.of(); }
    public void onReload() {}
}
```

Полный список хуков и порядок вызовов — в
[03-making-a-game.md](03-making-a-game.md) и таблице жизненного цикла
[01-architecture.md](01-architecture.md#жизненный-цикл-матча).

## MinigameDescriptor ← НОВОЕ

Паспорт игры: по нему `mg-core` заводит арены и команды, а хаб рисует карточку в
селекторе.

```java
public record MinigameDescriptor(
    String id,                 // "skywars" — стабилен, по нему идёт статистика
    Component displayName,
    ItemStack icon,            // для селектора хаба
    int minPlayers,
    int maxPlayers,
    String arenasFolder,       // где искать arenas/*.yml этой игры
    boolean teamBased
) {}
```

## DataKey ← НОВОЕ

Типизированный ключ — замена строковых ключей в `Arena.settings` и `Match.data()`.

```java
public final class DataKey<T> {
    public static <T> DataKey<T> of(String id, Class<T> type) { ... }
    public String id();
    public Class<T> type();
}

// объявляется один раз рядом с игрой:
public final class SkyWarsKeys {
    public static final DataKey<Integer> CHEST_REFILL = DataKey.of("chest-refill", Integer.class);
    public static final DataKey<Boolean> BORDER_ON    = DataKey.of("border-on", Boolean.class);
}

// использование — с compile-time типом:
int refill = m.arena().setting(SkyWarsKeys.CHEST_REFILL, 30);
m.set(SkyWarsKeys.BORDER_ON, true);
```

## StatsService

Абстракция хранилища статистики. Бэкенд (SQLite/MySQL) прозрачен для игры.
Подробно — [05-storage-and-stats.md](05-storage-and-stats.md).

```java
public interface StatsService {
    CompletableFuture<Profile> profile(UUID player);
    void record(UUID player, String gameId, StatDelta delta);          // async
    CompletableFuture<List<LeaderboardEntry>> top(String gameId, String stat, int n); // ← НОВОЕ
}

public record StatDelta(int winsDelta, int lossesDelta, int killsDelta, int playedDelta,
                        Map<String,Integer> customDeltas) {}
public record Profile(UUID player, String name, Map<String, GameStats> perGame) {}
public record LeaderboardEntry(UUID player, String name, int value, int rank) {}
```

## TransportService / QueueService ← НОВОЕ (хаб/сеть)

Межсерверный слой. На single-server — no-op заглушки. Подробно —
[04-cross-server.md](04-cross-server.md).

```java
public interface TransportService {
    void send(Player p, String serverName);        // отправить на конкретный сервер сети
    void sendToGame(Player p, String gameId);       // отправить на любой сервер с этой игрой
}

public interface QueueService {
    void enqueue(Player p, String gameId);          // в очередь на матч
    void dequeue(Player p);
    int queued(String gameId);
}
```

## MgCore — фасад

Точка входа для игрового плагина. Публикуется в `ServicesManager`.

```java
public interface MgCore {
    void register(Minigame game);       // вызвать в onEnable игры
    ArenaService arenas();
    StatsService stats();
    TransportService transport();       // на игровом сервере доступен для «вернуть в хаб»
    Msg messages();                     // фасад i18n (не статик-синглтон)
}
```

Дальше: как этим пользоваться на практике — [03-making-a-game.md](03-making-a-game.md).
