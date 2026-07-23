package ru.kiviuly.mg.arena;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.arena.ArenaService;
import ru.kiviuly.mg.game.GameSession;
import ru.kiviuly.mg.player.PlayerSnapshot;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Реестр арен (arenas/&lt;id&gt;.yml) + карта «игрок → активная сессия».
 * Точка входа игрока в игру: {@link #join}. Сессии создаются лениво на первый
 * вход и удерживаются в {@link Arena#getSession()}; карту игрок→сессия
 * поддерживают {@link #bind}/{@link #unbind} (их зовёт {@link GameSession}).
 */
public class ArenaManager implements ArenaService
{
    private final MgCorePlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, GameSession> playerSessions = new HashMap<>();

    public ArenaManager(MgCorePlugin plugin) {this.plugin = plugin;}

    private File dir()
    {
        File dir = new File(plugin.getDataFolder(), "arenas");
        if (!dir.exists()) {dir.mkdirs();}
        return dir;
    }

    private File fileOf(String id) {return new File(dir(), id + ".yml");}

    public void loadAll()
    {
        arenas.clear();
        File[] files = dir().listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {return;}
        for (File f : files)
        {
            String id = f.getName().substring(0, f.getName().length() - 4).toUpperCase();
            try
            {
                arenas.put(id, Arena.load(id, f));
            }
            catch (Exception e)
            {
                // Мир арены ещё/уже не загружен (unknown world при десериализации Location) и т.п. —
                // пропускаем эту арену, но НЕ роняем плагин. Подхватится по /mg reload, когда мир готов.
                plugin.getLogger().warning("Skipped arena " + id + " (load failed): " + e.getMessage());
            }
        }
    }

    public void save(Arena arena) {arena.save(fileOf(arena.getId()));}

    public void saveAll() {for (Arena a : arenas.values()) {save(a);}}

    public Arena create(String id, String worldName)
    {
        Arena arena = Arena.create(id, worldName, plugin.getConfig().getConfigurationSection("arena-defaults"));
        arenas.put(id, arena);
        save(arena);
        if (plugin.game() != null) {plugin.game().onArenaCreated(arena);} // материализуем игро-конфиг с дефолтами
        return arena;
    }

    public boolean delete(String id)
    {
        Arena arena = arenas.remove(id);
        if (arena == null) {return false;}
        if (arena.getSession() != null) {((GameSession) arena.getSession()).forceCleanup();}
        File f = fileOf(id);
        if (f.exists()) {f.delete();}
        if (plugin.game() != null) {plugin.game().onArenaRemoved(arena.getId());}
        return true;
    }

    public Arena get(String id) {return id == null ? null : arenas.get(id.toUpperCase());}
    public boolean exists(String id) {return id != null && arenas.containsKey(id.toUpperCase());}
    public Collection<Arena> all() {return arenas.values();}
    public Set<String> ids() {return arenas.keySet();}

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
            session = new GameSession(plugin, arena, plugin.game());
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

    /** Остановить все сессии (onDisable/reload). */
    public void stopAll()
    {
        for (Arena a : arenas.values())
        {
            if (a.getSession() != null) {((GameSession) a.getSession()).forceCleanup();}
        }
        playerSessions.clear();
    }
}
