package ru.kiviuly.mg.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.MgCore;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.data.DataKey;
import ru.kiviuly.mg.api.game.GamePhase;
import ru.kiviuly.mg.api.game.Match;
import ru.kiviuly.mg.api.game.MatchPlayer;
import ru.kiviuly.mg.api.game.MatchResult;
import ru.kiviuly.mg.api.game.Minigame;
import ru.kiviuly.mg.api.game.Team;
import ru.kiviuly.mg.api.arena.SetupMarkers;
import ru.kiviuly.mg.player.PlayerSnapshot;
import ru.kiviuly.mg.ui.GameBossBar;
import ru.kiviuly.mg.ui.GameScoreboard;
import ru.kiviuly.mg.api.util.DebugLog;
import ru.kiviuly.mg.api.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Движок одного матча: ведёт лобби → отсчёт → игру → концовку, снимает/возвращает
 * снапшоты игроков, откатывает изменённые блоки и заспавненные сущности, крутит
 * HUD и зовёт хуки {@link Minigame}. Игро-независим — правила/победа в Minigame.
 *
 * Один тикер (20 тиков = 1 сек) обслуживает и отсчёт, и матч по текущей фазе.
 */
public class GameSession implements Match
{
    private static final int END_DELAY_SECONDS = 5;
    private static final Set<Integer> ANNOUNCE_AT = Set.of(60, 45, 30, 20, 15, 10, 5, 4, 3, 2, 1);

    private final MgCorePlugin plugin;
    private final Arena arena;
    private final Minigame game;

    private final Map<UUID, MatchPlayer> players = new LinkedHashMap<>();
    private final Map<Location, BlockState> editedBlocks = new LinkedHashMap<>();
    private final Set<UUID> spawnedEntities = new HashSet<>();
    private final Map<String, Object> data = new HashMap<>();

    private GamePhase phase = GamePhase.LOBBY;
    private int countdown;
    private int elapsed;
    private BukkitTask ticker;
    private BukkitTask endTask;

    private final boolean restoreWorld;
    private final GameScoreboard scoreboard;
    private final GameBossBar bossBar;

    public GameSession(MgCorePlugin plugin, Arena arena, Minigame game)
    {
        this.plugin = plugin;
        this.arena = arena;
        this.game = game;
        var cfg = plugin.getConfig();
        this.restoreWorld = cfg.getBoolean("match.restore-world", true);
        this.scoreboard = cfg.getBoolean("hud.scoreboard", true) ? new GameScoreboard() : null;
        this.bossBar = cfg.getBoolean("hud.bossbar", true) ? new GameBossBar() : null;
    }

    // ===== публичный API (для игр и слушателей) =====

    public MgCorePlugin plugin() {return plugin;}
    @Override public MgCore core() {return plugin;}
    @Override public Arena arena() {return arena;}
    public Minigame game() {return game;}
    public GamePhase phase() {return phase;}
    public int elapsedSeconds() {return elapsed;}
    @Override public Map<String, Object> data() {return data;}

    @Override public <T> T get(DataKey<T> key) {return key.type().cast(data.get(key.id()));}

    @Override public <T> T get(DataKey<T> key, T def)
    {
        Object v = data.get(key.id());
        return v == null ? def : key.type().cast(v);
    }

    @Override public <T> void set(DataKey<T> key, T value) {data.put(key.id(), value);}

    @Override public Collection<MatchPlayer> players() {return players.values();}
    @Override public MatchPlayer player(UUID uuid) {return players.get(uuid);}
    @Override public boolean hasPlayer(UUID uuid) {return players.containsKey(uuid);}

    @Override public Collection<Team> teams()
    {
        List<Team> out = new ArrayList<>();
        for (MatchPlayer mp : players.values()) {out.add(new FfaTeam(mp));}
        return out;
    }

    @Override public Team teamOf(UUID player)
    {
        MatchPlayer mp = players.get(player);
        return mp == null ? null : new FfaTeam(mp);
    }
    @Override public boolean acceptsPlayers() {return phase == GamePhase.LOBBY || phase == GamePhase.COUNTDOWN;}

    /** Оставшееся время матча в секундах; -1 = без лимита. */
    public int remainingSeconds()
    {
        int dur = arena.getMatchDurationSeconds();
        return dur <= 0 ? -1 : Math.max(0, dur - elapsed);
    }

    /** Живые (не выбывшие) игроки, что сейчас онлайн. */
    public List<Player> alivePlayers()
    {
        List<Player> out = new ArrayList<>();
        for (MatchPlayer mp : players.values())
        {
            if (!mp.isAlive()) {continue;}
            Player p = Bukkit.getPlayer(mp.getUuid());
            if (p != null && p.isOnline()) {out.add(p);}
        }
        return out;
    }

    public int aliveCount()
    {
        int n = 0;
        for (MatchPlayer mp : players.values()) {if (mp.isAlive()) {n++;}}
        return n;
    }

    /** Все онлайн-участники сессии (живые + спектаторы). */
    public List<Player> onlinePlayers()
    {
        List<Player> out = new ArrayList<>();
        for (UUID id : players.keySet())
        {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {out.add(p);}
        }
        return out;
    }

    /** Запомнить блок ПЕРЕД изменением в матче, иначе cleanup его не откатит. */
    public void rememberBlock(Block block)
    {
        editedBlocks.putIfAbsent(block.getLocation(), block.getState());
    }

    /**
     * Запомнить прежнее состояние блока напрямую (для BlockPlaceEvent: блок УЖЕ
     * заменён, прежнее берётся из {@code e.getBlockReplacedState()}).
     */
    public void rememberState(BlockState old)
    {
        editedBlocks.putIfAbsent(old.getLocation(), old);
    }

    /** Зарегистрировать заспавненную сущность — удалится после матча. */
    public void trackEntity(Entity entity)
    {
        spawnedEntities.add(entity.getUniqueId());
    }

    /** Дефолтное условие победы: последний выживший, или ничья по истечении времени. */
    public MatchResult defaultResult()
    {
        int alive = aliveCount();
        if (alive <= 1)
        {
            for (MatchPlayer mp : players.values())
            {
                if (mp.isAlive()) {return MatchResult.of(mp.getUuid());}
            }
            return MatchResult.draw();
        }
        int rem = remainingSeconds();
        if (rem == 0) {return MatchResult.draw();}
        return null;
    }

    // ===== лобби =====

    public void addPlayer(Player p)
    {
        PlayerSnapshot.save(plugin, p);
        PlayerSnapshot.clear(p);
        players.put(p.getUniqueId(), new MatchPlayer(p));
        plugin.arenas().bind(p.getUniqueId(), this);

        if (arena.getLobby() != null) {p.teleport(arena.getLobby());}
        giveLobbyItems(p);
        if (scoreboard != null) {scoreboard.update(this);}
        if (bossBar != null) {bossBar.add(p);}

        broadcast("game.joined", Msg.ph("player", p.getName()),
            Msg.ph("current", players.size()), Msg.ph("max", arena.getMaxPlayers()));
        game.onLobbyJoin(this, p);
        DebugLog.log(Cat.SESSION, "join arena=%s player=%s size=%d", arena.getId(), p.getName(), players.size());

        maybeStartCountdown();
    }

    private void giveLobbyItems(Player p)
    {
        p.getInventory().setItem(8, Items.special(Material.RED_BED,
            Msg.get("game.leave-item-name"), Msg.getList("game.leave-item-lore"), "leave"));
    }

    private void maybeStartCountdown()
    {
        if (phase == GamePhase.LOBBY && players.size() >= arena.getMinPlayers()) {beginCountdown();}
    }

    private void beginCountdown()
    {
        phase = GamePhase.COUNTDOWN;
        countdown = arena.getLobbyCountdownSeconds();
        startTicker();
        broadcast("game.countdown-start", Msg.ph("n", countdown));
    }

    private void startTicker()
    {
        if (ticker != null) {return;}
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void stopTicker()
    {
        if (ticker != null) {ticker.cancel(); ticker = null;}
    }

    private void tick()
    {
        switch (phase)
        {
            case COUNTDOWN -> countdownTick();
            case RUNNING -> matchTick();
            default -> {}
        }
    }

    private void countdownTick()
    {
        if (players.size() < arena.getMinPlayers())
        {
            phase = GamePhase.LOBBY;
            stopTicker();
            broadcast("game.countdown-cancel");
            return;
        }
        if (players.size() >= arena.getMaxPlayers() && countdown > arena.getCountdownFullSeconds())
        {
            countdown = arena.getCountdownFullSeconds();
        }
        if (countdown <= 0) {startMatch(); return;}
        if (ANNOUNCE_AT.contains(countdown)) {broadcast("game.countdown", Msg.ph("n", countdown));}
        countdown--;
    }

    // ===== матч =====

    private void startMatch()
    {
        phase = GamePhase.RUNNING;
        elapsed = 0;
        SetupMarkers.clearForMatch(arena); // убрать блоки-подсказки, чтобы не спавнить в стекле

        List<Location> order = new ArrayList<>(arena.getSpawns());
        Collections.shuffle(order);
        int i = 0;
        for (Player p : onlinePlayers())
        {
            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            fullHeal(p);
            if (!order.isEmpty())
            {
                Location spot = order.get(i % order.size()).clone().add(0.5, 0, 0.5);
                p.teleport(spot);
                i++;
            }
            game.giveLoadout(this, p);
        }

        broadcast("game.started", Msg.ph("game", game.displayName()));
        game.onStart(this);
        if (scoreboard != null) {scoreboard.update(this);}
        DebugLog.log(Cat.SESSION, "start arena=%s players=%d game=%s", arena.getId(), players.size(), game.id());
    }

    private void matchTick()
    {
        elapsed++;
        game.onTick(this);
        if (scoreboard != null) {scoreboard.update(this);}
        if (bossBar != null) {bossBar.update(this);}

        MatchResult result = game.checkResult(this);
        if (result != null) {endMatch(result);}
    }

    /** Игрок выбыл (умер/сдался): в спектаторы, хук игры, проверка конца. */
    @Override public void eliminate(Player p, boolean died)
    {
        MatchPlayer mp = players.get(p.getUniqueId());
        if (mp == null || !mp.isAlive()) {return;}
        mp.setAlive(false);
        p.setGameMode(GameMode.SPECTATOR);

        broadcast(died ? "game.eliminated" : "game.left-match", Msg.ph("player", p.getName()),
            Msg.ph("alive", aliveCount()));
        game.onPlayerEliminated(this, mp);
        DebugLog.log(Cat.SESSION, "eliminate arena=%s player=%s alive=%d", arena.getId(), p.getName(), aliveCount());

        if (phase == GamePhase.RUNNING)
        {
            MatchResult result = game.checkResult(this);
            if (result != null) {endMatch(result);}
        }
    }

    private void endMatch(MatchResult result)
    {
        if (phase == GamePhase.ENDING) {return;}
        phase = GamePhase.ENDING;
        stopTicker();

        announceResult(result);
        game.onEnd(this, result);
        recordStats(result);
        DebugLog.log(Cat.SESSION, "end arena=%s winner=%s", arena.getId(), result.hasWinner());

        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, END_DELAY_SECONDS * 20L);
    }

    private void announceResult(MatchResult result)
    {
        if (result.hasWinner())
        {
            List<String> names = new ArrayList<>();
            for (UUID id : result.winners())
            {
                MatchPlayer mp = players.get(id);
                names.add(mp != null ? mp.getName() : id.toString());
            }
            broadcast("game.winner", Msg.ph("winner", String.join(", ", names)));
        }
        else
        {
            broadcast("game.draw");
        }
    }

    private void recordStats(MatchResult result)
    {
        if (plugin.stats() == null) {return;}
        Set<UUID> winners = new HashSet<>(result.winners());
        for (MatchPlayer mp : players.values())
        {
            boolean won = winners.contains(mp.getUuid());
            plugin.stats().recordMatch(mp.getUuid(), mp.getName(), won, mp.getKills());
        }
    }

    // ===== выход игрока / очистка =====

    /** Убрать игрока из сессии и вернуть его снапшот (если онлайн). */
    public void removePlayer(Player p, boolean voluntary)
    {
        MatchPlayer mp = players.remove(p.getUniqueId());
        if (mp == null) {return;}
        plugin.arenas().unbind(p.getUniqueId());
        if (bossBar != null) {bossBar.remove(p);}
        if (scoreboard != null) {scoreboard.remove(p);}
        game.onPlayerRemoved(this, p.getUniqueId());

        if (p.isOnline())
        {
            PlayerSnapshot.restore(plugin, p);
            if (voluntary) {Msg.send(p, "game.you-left", Msg.ph("arena", arena.getId()));}
        }
        broadcast("game.player-left", Msg.ph("player", p.getName()), Msg.ph("current", players.size()));

        if (phase == GamePhase.COUNTDOWN && players.size() < arena.getMinPlayers())
        {
            phase = GamePhase.LOBBY;
            stopTicker();
            broadcast("game.countdown-cancel");
        }
        else if (phase == GamePhase.RUNNING)
        {
            MatchResult result = game.checkResult(this);
            if (result != null) {endMatch(result); return;}
        }
        if (players.isEmpty() && phase != GamePhase.ENDING) {forceCleanup();}
    }

    /** Досрочный старт админом. false — некого/уже идёт. */
    public boolean forceStart()
    {
        if (players.isEmpty() || phase == GamePhase.RUNNING || phase == GamePhase.ENDING) {return false;}
        startTicker();
        startMatch();
        return true;
    }

    /** Полная немедленная остановка и откат (shutdown/удаление/аварийно). */
    public void forceCleanup()
    {
        if (endTask != null) {endTask.cancel(); endTask = null;}
        cleanup();
    }

    private void cleanup()
    {
        stopTicker();
        if (endTask != null) {endTask.cancel(); endTask = null;}

        // вернуть игроков
        for (UUID id : new ArrayList<>(players.keySet()))
        {
            Player p = Bukkit.getPlayer(id);
            if (bossBar != null && p != null) {bossBar.remove(p);}
            if (scoreboard != null && p != null) {scoreboard.remove(p);}
            plugin.arenas().unbind(id);
            if (p != null && p.isOnline()) {PlayerSnapshot.restore(plugin, p);}
        }
        players.clear();

        // откат мира
        int blocks = editedBlocks.size();
        int entities = spawnedEntities.size();
        if (restoreWorld)
        {
            for (Map.Entry<Location, BlockState> e : editedBlocks.entrySet()) {e.getValue().update(true, false);}
            for (UUID id : spawnedEntities)
            {
                Entity ent = Bukkit.getEntity(id);
                if (ent != null) {ent.remove();}
            }
        }
        editedBlocks.clear();
        spawnedEntities.clear();
        if (bossBar != null) {bossBar.clearAll();}
        game.onCleanup(this); // per-match ресурсы игры (напр. свои боссбары) — при любом завершении

        arena.setSession(null);
        DebugLog.log(Cat.SESSION, "cleanup arena=%s blocks=%d entities=%d", arena.getId(), blocks, entities);
    }

    // ===== helpers =====

    private void fullHeal(Player p)
    {
        var maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(10f);
        p.setFireTicks(0);
        p.setExp(0f);
        p.setLevel(0);
    }

    /** Разослать сообщение всем онлайн-участникам сессии. */
    @Override
    public void broadcast(String key, TagResolver... resolvers)
    {
        Component msg = Msg.get(key, resolvers);
        for (Player p : onlinePlayers()) {p.sendMessage(msg);}
    }

    /** FFA-команда из одного игрока (дефолт движка, пока нет реального командного режима). */
    private static final class FfaTeam implements Team
    {
        private final MatchPlayer mp;

        FfaTeam(MatchPlayer mp) {this.mp = mp;}

        @Override public String id() {return mp.getUuid().toString();}
        @Override public Component displayName() {return Component.text(mp.getName());}
        @Override public TextColor color() {return NamedTextColor.WHITE;}
        @Override public Set<UUID> members() {return Set.of(mp.getUuid());}
        @Override public boolean isAlive() {return mp.isAlive();}
    }
}
