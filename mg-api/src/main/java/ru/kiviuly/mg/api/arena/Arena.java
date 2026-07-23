package ru.kiviuly.mg.api.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import ru.kiviuly.mg.api.game.Match;

/**
 * Арена: игро-независимая конфигурация одной площадки. Ядро знает только общие
 * вещи — мир, лобби, спавны, лимиты игроков и таймингов. Числовые настройки самой
 * ИГРЫ хранятся в обобщённой мапе {@code settings} (getSetting/setSetting), чтобы
 * добавлять их без правки этого класса. Один файл на арену: arenas/&lt;id&gt;.yml.
 *
 * <p>Ссылку на живую сессию арена держит как {@link Match} (api-контракт), не зная
 * конкретный движок ядра.</p>
 */
public class Arena
{
    private final String id;

    private String displayNameRaw;
    private String descriptionRaw = "";
    private String worldName;
    private boolean enabled = false;
    private int minPlayers;
    private int maxPlayers;
    private int lobbyCountdownSeconds;
    private int countdownFullSeconds;
    private int matchDurationSeconds;   // 0 = без лимита времени
    private Location lobby;
    private final List<Location> spawns = new ArrayList<>();
    private final Map<String, Integer> settings = new LinkedHashMap<>(); // игро-специфичные числа
    // Именованные группы точек с тегами: группа -> (локация -> список строк-тегов).
    // Обобщение под игро-специфичную разметку (напр. сундуки: группа "chest", теги = категории лута).
    private final Map<String, Map<Location, List<String>>> spotGroups = new LinkedHashMap<>();

    private Match session; // runtime: живой матч на этой арене (null = простаивает)

    public Arena(String id)
    {
        this.id = id;
        this.displayNameRaw = id;
    }

    /** Новая арена со значениями по умолчанию из config.yml (arena-defaults). */
    public static Arena create(String id, String worldName, ConfigurationSection defaults)
    {
        Arena a = new Arena(id);
        a.worldName = worldName;
        a.minPlayers = defaults != null ? defaults.getInt("min-players", 2) : 2;
        a.maxPlayers = defaults != null ? defaults.getInt("max-players", 12) : 12;
        a.lobbyCountdownSeconds = defaults != null ? defaults.getInt("lobby-countdown-seconds", 20) : 20;
        a.countdownFullSeconds = defaults != null ? defaults.getInt("countdown-full-seconds", 5) : 5;
        a.matchDurationSeconds = defaults != null ? defaults.getInt("match-duration-seconds", 600) : 600;
        return a;
    }

    // ===== persistence =====

    public static Arena load(String id, File file)
    {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Arena a = new Arena(id);
        a.displayNameRaw = cfg.getString("display-name", id);
        a.descriptionRaw = cfg.getString("description", "");
        a.worldName = cfg.getString("world");
        a.enabled = cfg.getBoolean("enabled", false);
        a.minPlayers = cfg.getInt("min-players", 2);
        a.maxPlayers = cfg.getInt("max-players", 12);
        a.lobbyCountdownSeconds = cfg.getInt("lobby-countdown-seconds", 20);
        a.countdownFullSeconds = cfg.getInt("countdown-full-seconds", 5);
        a.matchDurationSeconds = cfg.getInt("match-duration-seconds", 600);
        a.lobby = cfg.getLocation("lobby");
        for (Object o : cfg.getList("spawns", List.of()))
        {
            if (o instanceof Location l) {a.spawns.add(l);}
        }
        ConfigurationSection sec = cfg.getConfigurationSection("settings");
        if (sec != null)
        {
            for (String key : sec.getKeys(false)) {a.settings.put(key, sec.getInt(key));}
        }
        ConfigurationSection spots = cfg.getConfigurationSection("spots");
        if (spots != null)
        {
            for (String group : spots.getKeys(false))
            {
                ConfigurationSection gs = spots.getConfigurationSection(group);
                if (gs == null) {continue;}
                for (String key : gs.getKeys(false))
                {
                    ConfigurationSection cs = gs.getConfigurationSection(key);
                    if (cs == null) {continue;}
                    Location loc = cs.getLocation("location");
                    if (loc != null) {a.spots(group).put(loc, new ArrayList<>(cs.getStringList("tags")));}
                }
            }
        }
        return a;
    }

    public void save(File file)
    {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("display-name", displayNameRaw);
        cfg.set("description", descriptionRaw);
        cfg.set("world", worldName);
        cfg.set("enabled", enabled);
        cfg.set("min-players", minPlayers);
        cfg.set("max-players", maxPlayers);
        cfg.set("lobby-countdown-seconds", lobbyCountdownSeconds);
        cfg.set("countdown-full-seconds", countdownFullSeconds);
        cfg.set("match-duration-seconds", matchDurationSeconds);
        cfg.set("lobby", lobby);
        cfg.set("spawns", spawns);
        if (!settings.isEmpty())
        {
            ConfigurationSection sec = cfg.createSection("settings");
            for (Map.Entry<String, Integer> e : settings.entrySet()) {sec.set(e.getKey(), e.getValue());}
        }
        for (Map.Entry<String, Map<Location, List<String>>> group : spotGroups.entrySet())
        {
            if (group.getValue().isEmpty()) {continue;}
            ConfigurationSection gs = cfg.createSection("spots." + group.getKey());
            int i = 0;
            for (Map.Entry<Location, List<String>> e : group.getValue().entrySet())
            {
                ConfigurationSection cs = gs.createSection("s" + i++);
                cs.set("location", e.getKey());
                cs.set("tags", new ArrayList<>(e.getValue()));
            }
        }
        try {file.getParentFile().mkdirs(); cfg.save(file);}
        catch (IOException e) {throw new RuntimeException("Failed to save arena " + id, e);}
    }

    // ===== accessors =====

    public String getId() {return id;}
    public String getDisplayNameRaw() {return displayNameRaw;}
    public void setDisplayNameRaw(String v) {this.displayNameRaw = v;}
    public String getDescriptionRaw() {return descriptionRaw;}
    public void setDescriptionRaw(String v) {this.descriptionRaw = v;}
    public String getWorldName() {return worldName;}
    public void setWorldName(String v) {this.worldName = v;}
    public World getWorld() {return worldName == null ? null : Bukkit.getWorld(worldName);}
    public boolean isEnabled() {return enabled;}
    public void setEnabled(boolean v) {this.enabled = v;}
    public int getMinPlayers() {return minPlayers;}
    public void setMinPlayers(int v) {this.minPlayers = v;}
    public int getMaxPlayers() {return maxPlayers;}
    public void setMaxPlayers(int v) {this.maxPlayers = v;}
    public int getLobbyCountdownSeconds() {return lobbyCountdownSeconds;}
    public void setLobbyCountdownSeconds(int v) {this.lobbyCountdownSeconds = v;}
    public int getCountdownFullSeconds() {return countdownFullSeconds;}
    public void setCountdownFullSeconds(int v) {this.countdownFullSeconds = v;}
    public int getMatchDurationSeconds() {return matchDurationSeconds;}
    public void setMatchDurationSeconds(int v) {this.matchDurationSeconds = v;}
    public Location getLobby() {return lobby;}
    public void setLobby(Location v) {this.lobby = v;}
    public List<Location> getSpawns() {return spawns;}

    /** Игро-специфичная числовая настройка (getSetting/setSetting) — своё пространство имён у каждой игры. */
    public int getSetting(String key, int def) {return settings.getOrDefault(key, def);}
    public void setSetting(String key, int value) {settings.put(key, value);}
    public Map<String, Integer> getSettings() {return settings;}

    // ===== именованные группы точек с тегами (spots) =====

    /** Точки группы: локация -> список тегов. Живая мапа (можно править). */
    public Map<Location, List<String>> spots(String group)
    {
        return spotGroups.computeIfAbsent(group, k -> new LinkedHashMap<>());
    }

    /** Добавить тег на точку группы (создаёт точку при необходимости, без дублей). */
    public void addSpot(String group, Location loc, String tag)
    {
        List<String> tags = spots(group).computeIfAbsent(loc, k -> new ArrayList<>());
        if (tag != null && !tags.contains(tag)) {tags.add(tag);}
    }

    /** Убрать точку группы целиком. true — точка была. */
    public boolean removeSpot(String group, Location loc)
    {
        Location key = spotAt(group, loc);
        return key != null && spots(group).remove(key) != null;
    }

    /** Ключ точки группы по координатам блока (или null). */
    public Location spotAt(String group, Location block)
    {
        for (Location l : spots(group).keySet())
        {
            if (sameBlock(l, block)) {return l;}
        }
        return null;
    }

    private static boolean sameBlock(Location a, Location b)
    {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld())
            && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    /** Живой матч на этой арене (или null, если простаивает). Тип — api-контракт {@link Match}. */
    public Match getSession() {return session;}
    public void setSession(Match session) {this.session = session;}
}
