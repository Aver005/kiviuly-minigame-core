package ru.kiviuly.mg.stats;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.kiviuly.mg.api.stats.StatsService;

/**
 * SQLite-бэкенд {@link StatsService} (stats.db). Модель — произвольные именованные
 * счётчики на игрока (таблица {@code stat_counters}: uuid+stat→value), ники — в
 * {@code stat_players}. Так один бэкенд обслуживает все игры без игро-специфичных
 * колонок. Драйвер org.sqlite встроен в Paper. Запись — асинхронно; чтение — async с
 * callback в main thread.
 */
public class StatsRepository implements StatsService
{
    private final JavaPlugin plugin;
    private Connection connection;

    public StatsRepository(JavaPlugin plugin) {this.plugin = plugin;}

    public void open() throws SQLException
    {
        File db = new File(plugin.getDataFolder(), "stats.db");
        db.getParentFile().mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        try (Statement st = connection.createStatement())
        {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS stat_players (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS stat_counters (
                    uuid TEXT NOT NULL,
                    stat TEXT NOT NULL,
                    value INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, stat)
                )""");
        }
        migrateLegacyWideTable();
    }

    /**
     * Разовая миграция старой широкой таблицы {@code stats} (uuid,name,wins,loses,kills,
     * played) в модель счётчиков. После переноса старая таблица удаляется, поэтому
     * миграция идемпотентна (при следующем запуске таблицы уже нет).
     */
    private void migrateLegacyWideTable() throws SQLException
    {
        boolean hasLegacy;
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='stats'"))
        {
            try (ResultSet rs = ps.executeQuery()) {hasLegacy = rs.next();}
        }
        if (!hasLegacy) {return;}

        int migrated = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid, name, wins, loses, kills, played FROM stats"))
        {
            while (rs.next())
            {
                String uuid = rs.getString("uuid");
                ensurePlayer(uuid, rs.getString("name"));
                setValue(uuid, "wins", rs.getInt("wins"));
                setValue(uuid, "loses", rs.getInt("loses"));
                setValue(uuid, "kills", rs.getInt("kills"));
                setValue(uuid, "played", rs.getInt("played"));
                migrated++;
            }
        }
        try (Statement st = connection.createStatement()) {st.executeUpdate("DROP TABLE stats");}
        plugin.getLogger().info("Stats: migrated " + migrated + " rows from legacy wide table to counters.");
    }

    public void close()
    {
        try {if (connection != null) {connection.close();}}
        catch (SQLException ignored) {}
    }

    // ===== низкоуровневые операции (под synchronized через async) =====

    private void ensurePlayer(UUID uuid, String name) throws SQLException {ensurePlayer(uuid.toString(), name);}

    private void ensurePlayer(String uuid, String name) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO stat_players(uuid, name) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name"))
        {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private void addValue(String uuid, String stat, int delta) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO stat_counters(uuid, stat, value) VALUES(?, ?, ?) "
            + "ON CONFLICT(uuid, stat) DO UPDATE SET value = value + excluded.value"))
        {
            ps.setString(1, uuid);
            ps.setString(2, stat);
            ps.setInt(3, delta);
            ps.executeUpdate();
        }
    }

    private void setValue(String uuid, String stat, int value) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO stat_counters(uuid, stat, value) VALUES(?, ?, ?) "
            + "ON CONFLICT(uuid, stat) DO UPDATE SET value = excluded.value"))
        {
            ps.setString(1, uuid);
            ps.setString(2, stat);
            ps.setInt(3, value);
            ps.executeUpdate();
        }
    }

    private void maxValue(String uuid, String stat, int value) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO stat_counters(uuid, stat, value) VALUES(?, ?, ?) "
            + "ON CONFLICT(uuid, stat) DO UPDATE SET value = MAX(value, excluded.value)"))
        {
            ps.setString(1, uuid);
            ps.setString(2, stat);
            ps.setInt(3, value);
            ps.executeUpdate();
        }
    }

    // ===== контракт StatsService =====

    @Override
    public void recordMatch(UUID uuid, String name, boolean won, int kills)
    {
        String id = uuid.toString();
        async(() ->
        {
            ensurePlayer(id, name);
            addValue(id, "played", 1);
            addValue(id, won ? "wins" : "loses", 1);
            if (kills != 0) {addValue(id, "kills", kills);}
        });
    }

    @Override
    public void add(UUID uuid, String name, String column, int delta)
    {
        String id = uuid.toString();
        async(() -> {ensurePlayer(id, name); addValue(id, column, delta);});
    }

    @Override
    public void set(UUID uuid, String name, String column, int value)
    {
        String id = uuid.toString();
        async(() -> {ensurePlayer(id, name); setValue(id, column, value);});
    }

    @Override
    public void max(UUID uuid, String name, String column, int value)
    {
        String id = uuid.toString();
        async(() -> {ensurePlayer(id, name); maxValue(id, column, value);});
    }

    @Override
    public void findByName(String name, Consumer<Row> callback)
    {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            Row row = null;
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT p.name AS pname, c.stat AS stat, c.value AS value FROM stat_players p "
                + "LEFT JOIN stat_counters c ON c.uuid = p.uuid WHERE p.name = ? COLLATE NOCASE"))
            {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery())
                {
                    Map<String, Integer> counters = new HashMap<>();
                    String realName = null;
                    boolean found = false;
                    while (rs.next())
                    {
                        found = true;
                        realName = rs.getString("pname");
                        String stat = rs.getString("stat");
                        if (stat != null) {counters.put(stat, rs.getInt("value"));}
                    }
                    if (found) {row = new Row(realName, counters);}
                }
            }
            catch (SQLException e)
            {
                plugin.getLogger().severe("Stats read error: " + e.getMessage());
            }
            Row result = row;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> top(String gameId, String stat, int n)
    {
        CompletableFuture<List<LeaderboardEntry>> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            List<LeaderboardEntry> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT c.uuid AS uuid, p.name AS name, c.value AS value FROM stat_counters c "
                + "JOIN stat_players p ON p.uuid = c.uuid WHERE c.stat = ? ORDER BY c.value DESC LIMIT ?"))
            {
                ps.setString(1, stat);
                ps.setInt(2, n);
                try (ResultSet rs = ps.executeQuery())
                {
                    int rank = 1;
                    while (rs.next())
                    {
                        out.add(new LeaderboardEntry(
                            UUID.fromString(rs.getString("uuid")), rs.getString("name"),
                            rs.getInt("value"), rank++));
                    }
                }
            }
            catch (SQLException e) {plugin.getLogger().severe("Stats top error: " + e.getMessage());}
            Bukkit.getScheduler().runTask(plugin, () -> future.complete(out));
        });
        return future;
    }

    private interface SqlRunnable {void run() throws SQLException;}

    private void async(SqlRunnable action)
    {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            try {synchronized (this) {action.run();}}
            catch (SQLException e) {plugin.getLogger().severe("Stats write error: " + e.getMessage());}
        });
    }
}
