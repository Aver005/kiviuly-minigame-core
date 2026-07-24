package ru.kiviuly.mg.arena;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.arena.ArenaService;
import ru.kiviuly.mg.api.game.Minigame;
import ru.kiviuly.mg.api.util.Msg;
import ru.kiviuly.mg.game.GameSession;
import ru.kiviuly.mg.player.PlayerSnapshot;

/**
 * Реестр арен + карта «игрок → активная сессия».
 *
 * <p>Арена принадлежит игре, поэтому ключ — ПАРА (игра, id): одинаковые id в разных
 * мини-играх допустимы. На диске это раскладка {@code arenas/<игра>/<ID>.yml}
 * (арены без владельца — в {@code arenas/_unowned/}). Старые плоские файлы
 * {@code arenas/<ID>.yml} подхватываются и переносятся в новую раскладку.</p>
 *
 * <p>Точка входа игрока: {@link #join}. Сессии создаются лениво и живут в
 * {@link Arena#getSession()}; карту игрок→сессия ведут {@link #bind}/{@link #unbind}.</p>
 */
public class ArenaManager implements ArenaService
{
    /** Папка для арен без игры-владельца. */
    private static final String UNOWNED = "_unowned";

    private final MgCorePlugin plugin;
    /** игра -> (id арены -> арена); порядок регистрации сохраняется. */
    private final Map<String, Map<String, Arena>> byGame = new LinkedHashMap<>();
    private final Map<UUID, GameSession> playerSessions = new HashMap<>();

    public ArenaManager(MgCorePlugin plugin) {this.plugin = plugin;}

    private static String key(String gameId) {return gameId == null || gameId.isBlank() ? UNOWNED : gameId;}

    private static String key(Arena arena) {return key(arena.getGameId());}

    /** Папка арен ядра — только для арен без игры-владельца (и legacy-файлов). */
    private File root()
    {
        File dir = new File(plugin.getDataFolder(), "arenas");
        if (!dir.exists()) {dir.mkdirs();}
        return dir;
    }

    /**
     * Папка арен игры: {@code plugins/<ИграПлагин>/arenas/}. Каждая мини-игра владеет
     * своими данными; в папке ядра лежат только арены без владельца.
     */
    private File dirOf(String gameId)
    {
        Minigame game = plugin.gameById(gameId);
        File dir = game != null
            ? new File(game.dataFolder(), "arenas")
            : new File(root(), UNOWNED);
        if (!dir.exists()) {dir.mkdirs();}
        return dir;
    }

    private File fileOf(Arena arena) {return new File(dirOf(arena.getGameId()), arena.getId() + ".yml");}

    // ===== загрузка / сохранение =====

    public void loadAll()
    {
        byGame.clear();

        // 1. арены каждой зарегистрированной игры — из ЕЁ папки: plugins/<Игра>/arenas/
        for (Minigame game : plugin.games())
        {
            loadFrom(new File(game.dataFolder(), "arenas"), game.id());
        }

        // 2. арены без владельца — в папке ядра
        loadFrom(new File(root(), UNOWNED), null);

        // 3. legacy: плоские arenas/<ID>.yml в папке ядра — загрузить и перенести
        //    в папку игры-владельца (или в _unowned).
        File[] flat = root().listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (flat != null)
        {
            for (File f : flat)
            {
                Arena a = loadFile(f, arenaIdOf(f));
                if (a == null) {continue;}
                index(a);
                save(a);                 // запишется уже в папку игры
                if (f.delete()) {plugin.getLogger().info("Arena " + a.getId() + " moved to " + key(a) + " folder");}
            }
        }
    }

    /** Загрузить все арены из папки, проставив владельца {@code gameId} (null = без владельца). */
    private void loadFrom(File dir, String gameId)
    {
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {return;}
        for (File f : files)
        {
            Arena a = loadFile(f, arenaIdOf(f));
            if (a == null) {continue;}
            // Владельца задаёт папка, в которой лежит файл (файл может её не знать).
            a.setGameId(gameId);
            index(a);
        }
    }

    private static String arenaIdOf(File f)
    {
        return f.getName().substring(0, f.getName().length() - 4).toUpperCase();
    }

    private Arena loadFile(File f, String id)
    {
        try {return Arena.load(id, f);}
        catch (Exception e)
        {
            // Мир арены ещё не загружен (unknown world при десериализации Location) и т.п. —
            // пропускаем арену, но НЕ роняем плагин. Подхватится по /mg reload.
            plugin.getLogger().warning("Skipped arena " + id + " (load failed): " + e.getMessage());
            return null;
        }
    }

    private void index(Arena a)
    {
        byGame.computeIfAbsent(key(a), k -> new LinkedHashMap<>()).put(a.getId(), a);
    }

    public void save(Arena arena) {arena.save(fileOf(arena));}

    public void saveAll() {for (Arena a : all()) {save(a);}}

    // ===== создание / удаление =====

    public Arena create(String id, String worldName) {return create(id, worldName, null);}

    /** Создать арену, закрепив её за игрой {@code gameId} (null = без владельца). */
    public Arena create(String id, String worldName, String gameId)
    {
        Arena arena = Arena.create(id, worldName, plugin.getConfig().getConfigurationSection("arena-defaults"));
        arena.setGameId(gameId);
        index(arena);
        save(arena);
        Minigame owner = plugin.gameFor(arena);
        if (owner != null) {owner.onArenaCreated(arena);} // материализуем игро-конфиг с дефолтами
        return arena;
    }

    /** Удалить арену конкретной игры. */
    public boolean delete(String gameId, String id)
    {
        Map<String, Arena> map = byGame.get(key(gameId));
        Arena arena = map == null || id == null ? null : map.remove(id.toUpperCase());
        if (arena == null) {return false;}
        if (arena.getSession() != null) {((GameSession) arena.getSession()).forceCleanup();}
        File f = fileOf(arena);
        if (f.exists()) {f.delete();}
        Minigame owner = plugin.gameFor(arena);
        if (owner != null) {owner.onArenaRemoved(arena.getId());}
        return true;
    }

    /** Удалить арену по id, если он однозначен во всех играх. */
    public boolean delete(String id)
    {
        Arena a = get(id);
        return a != null && delete(a.getGameId(), a.getId());
    }

    // ===== поиск =====

    /** Арена конкретной игры (точное разрешение). */
    @Override
    public Arena get(String gameId, String arenaId)
    {
        Map<String, Arena> map = byGame.get(key(gameId));
        return map == null || arenaId == null ? null : map.get(arenaId.toUpperCase());
    }

    /** Все арены с таким id во всех играх (для разрешения неоднозначности). */
    @Override
    public List<Arena> findById(String arenaId)
    {
        List<Arena> out = new ArrayList<>();
        if (arenaId == null) {return out;}
        String needle = arenaId.toUpperCase();
        for (Map<String, Arena> map : byGame.values())
        {
            Arena a = map.get(needle);
            if (a != null) {out.add(a);}
        }
        return out;
    }

    /** Арена по id, если он однозначен; при конфликте — null (нужен slug игры). */
    @Override
    public Arena get(String id)
    {
        List<Arena> found = findById(id);
        return found.size() == 1 ? found.get(0) : null;
    }

    @Override
    public boolean exists(String id) {return !findById(id).isEmpty();}

    @Override
    public boolean exists(String gameId, String arenaId) {return get(gameId, arenaId) != null;}

    @Override
    public Collection<Arena> all()
    {
        List<Arena> out = new ArrayList<>();
        for (Map<String, Arena> map : byGame.values()) {out.addAll(map.values());}
        return out;
    }

    @Override
    public Set<String> ids()
    {
        Set<String> out = new LinkedHashSet<>();
        for (Arena a : all()) {out.add(a.getId());}
        return out;
    }

    @Override
    public Collection<Arena> all(String gameId)
    {
        Map<String, Arena> map = byGame.get(key(gameId));
        return map == null ? List.of() : new ArrayList<>(map.values());
    }

    @Override
    public Set<String> ids(String gameId)
    {
        Set<String> out = new LinkedHashSet<>();
        for (Arena a : all(gameId)) {out.add(a.getId());}
        return out;
    }

    // ===== игрок ↔ сессия =====

    public GameSession sessionOf(Player p) {return playerSessions.get(p.getUniqueId());}
    public boolean inGame(Player p) {return playerSessions.containsKey(p.getUniqueId());}

    /** Зовётся сессией при добавлении игрока. */
    public void bind(UUID uuid, GameSession session) {playerSessions.put(uuid, session);}

    /** Зовётся сессией при выходе игрока. */
    public void unbind(UUID uuid) {playerSessions.remove(uuid);}

    /**
     * Вход игрока в арену. Проверяет доступность, создаёт сессию при необходимости
     * и добавляет игрока в лобби. Сообщения игроку отправляет сам.
     */
    public void join(Player p, Arena arena)
    {
        if (inGame(p)) {Msg.send(p, "game.already-in-game"); return;}
        if (!arena.isEnabled()) {Msg.send(p, "game.arena-disabled", Msg.ph("arena", arena.getId())); return;}
        Minigame game = plugin.gameFor(arena);
        if (game != null && !game.canJoin(arena, p)) {return;} // игра сама объяснила отказ
        if (arena.getWorld() == null) {Msg.send(p, "errors.world-not-loaded"); return;}
        if (arena.getSpawns().isEmpty() || arena.getLobby() == null)
        {
            Msg.send(p, "game.arena-not-ready", Msg.ph("arena", arena.getId()));
            return;
        }
        if (PlayerSnapshot.exists(plugin, p.getUniqueId())) {Msg.send(p, "game.snapshot-exists"); return;}

        GameSession session = (GameSession) arena.getSession();
        if (session == null)
        {
            Minigame owner = plugin.gameFor(arena);
            if (owner == null) {Msg.send(p, "game.arena-not-ready", Msg.ph("arena", arena.getId())); return;}
            session = new GameSession(plugin, arena, owner);
            arena.setSession(session);
        }
        if (!session.acceptsPlayers())
        {
            Msg.send(p, "game.match-in-progress", Msg.ph("arena", arena.getId()));
            return;
        }
        session.addPlayer(p);
    }

    /** Выход игрока по своей воле (/mg leave или дисконнект). */
    public void leave(Player p)
    {
        GameSession session = sessionOf(p);
        if (session == null) {return;}
        session.removePlayer(p, true);
    }

    @Override
    public boolean forceStart(Arena arena)
    {
        GameSession session = (GameSession) arena.getSession();
        return session != null && session.forceStart();
    }

    @Override
    public void stop(Arena arena)
    {
        GameSession session = (GameSession) arena.getSession();
        if (session != null) {session.forceCleanup();}
    }

    /** Остановить все сессии (onDisable/reload). */
    public void stopAll()
    {
        for (Arena a : all())
        {
            if (a.getSession() != null) {((GameSession) a.getSession()).forceCleanup();}
        }
        playerSessions.clear();
    }
}
