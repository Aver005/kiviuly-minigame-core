package ru.kiviuly.mg.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.game.Minigame;
import ru.kiviuly.mg.arena.ArenaCheck;
import ru.kiviuly.mg.api.arena.SetupMarkers;
import ru.kiviuly.mg.game.GameSession;
import ru.kiviuly.mg.menu.ArenaHubMenu;
import ru.kiviuly.mg.menu.ArenaSelectMenu;
import ru.kiviuly.mg.api.util.DebugLog;
import ru.kiviuly.mg.api.util.Msg;

/** /mg — команды игрока и админа (регистрация арен, настройка, вход в игру). */
public class MinigameCommand implements TabExecutor
{
    private static final List<String> PLAYER_SUBS = List.of("join", "leave", "stats", "help");
    private static final List<String> ADMIN_SUBS = List.of(
        "create", "remove", "enable", "disable", "gui", "setlobby", "addspawn", "set",
        "check", "start", "stop", "list", "reload", "save", "debuglog");
    private static final List<String> SET_KEYS = List.of(
        "minplayers", "maxplayers", "lobbycountdown", "countdownfull", "duration");

    private final MgCorePlugin plugin;
    /** Игра-владелец команды; null = платформенная команда (/mg) поверх всех арен. */
    private final Minigame owner;

    public MinigameCommand(MgCorePlugin plugin) {this(plugin, null);}

    public MinigameCommand(MgCorePlugin plugin, Minigame owner)
    {
        this.plugin = plugin;
        this.owner = owner;
    }

    /** Арены в области видимости команды: свои для игровой, все — для платформенной. */
    private Collection<Arena> scopedArenas()
    {
        return owner == null ? plugin.arenas().all() : plugin.arenas().all(owner.id());
    }

    private Set<String> scopedIds()
    {
        return owner == null ? plugin.arenas().ids() : plugin.arenas().ids(owner.id());
    }

    /** Slug мини-игры среди аргументов (любой arg после id, совпадающий с id игры). */
    private String slugFrom(String[] args)
    {
        for (int i = 2; i < args.length; i++)
        {
            for (Minigame g : plugin.games())
            {
                if (g.id().equalsIgnoreCase(args[i])) {return g.id();}
            }
        }
        return null;
    }

    /**
     * Разрешить арену по id в области видимости команды. Сообщения об ошибке шлёт сам
     * (null = обработку прекратить).
     *
     * <p>Игровая команда видит только свои арены. Платформенная (/mg) — все; при
     * конфликте id между мини-играми требует slug: {@code /mg enable <ID> <slug>}.</p>
     */
    private Arena resolveArena(Player p, String id, String[] args)
    {
        if (owner != null)
        {
            Arena a = plugin.arenas().get(owner.id(), id);
            if (a == null) {Msg.send(p, "errors.arena-not-found", Msg.ph("arena", id));}
            return a;
        }
        List<Arena> found = plugin.arenas().findById(id);
        if (found.isEmpty()) {Msg.send(p, "errors.arena-not-found", Msg.ph("arena", id)); return null;}
        String slug = slugFrom(args);
        if (slug != null)
        {
            for (Arena a : found) {if (slug.equals(a.getGameId())) {return a;}}
            Msg.send(p, "errors.arena-not-found-in-game", Msg.ph("arena", id), Msg.ph("game", slug));
            return null;
        }
        if (found.size() == 1) {return found.get(0);}
        List<String> gameIds = new ArrayList<>();
        for (Arena a : found) {gameIds.add(a.getGameId() == null ? "?" : a.getGameId());}
        Msg.send(p, "errors.arena-ambiguous", Msg.ph("arena", id), Msg.ph("games", String.join(", ", gameIds)));
        return null;
    }

    /** Игра, которой принадлежит создаваемая арена (для /mg — из slug либо единственная). */
    private String targetGameId(Player p, String[] args)
    {
        if (owner != null) {return owner.id();}
        String slug = slugFrom(args);
        if (slug != null) {return slug;}
        String single = null;
        for (Minigame g : plugin.games())
        {
            if ("template".equals(g.id())) {continue;}
            if (single != null)
            {
                Msg.send(p, "errors.game-slug-required");
                return null;
            }
            single = g.id();
        }
        if (single == null) {Msg.send(p, "errors.game-slug-required");}
        return single;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player p)) {sender.sendMessage("Players only"); return true;}
        if (args.length == 0) {new ArenaSelectMenu(plugin).open(p); return true;}
        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub)
        {
            case "help" -> {sendHelp(p); return true;}
            case "join" ->
            {
                if (args.length < 2) {new ArenaSelectMenu(plugin).open(p); return true;}
                Arena arena = resolveArena(p, args[1].toUpperCase(Locale.ROOT), args);
                if (arena == null) {return true;}
                plugin.arenas().join(p, arena);
                return true;
            }
            case "leave" -> {plugin.arenas().leave(p); return true;}
            case "stats" ->
            {
                String target = args.length >= 2 ? args[1] : p.getName();
                plugin.stats().findByName(target, row ->
                {
                    if (row == null) {Msg.send(p, "stats.not-found", Msg.ph("player", target)); return;}
                    Msg.send(p, "stats.header", Msg.ph("player", row.name()));
                    Msg.send(p, "stats.line-wins", Msg.ph("n", row.wins()));
                    Msg.send(p, "stats.line-loses", Msg.ph("n", row.loses()));
                    Msg.send(p, "stats.line-kills", Msg.ph("n", row.kills()));
                    Msg.send(p, "stats.line-played", Msg.ph("n", row.played()));
                });
                return true;
            }
            default -> {}
        }

        if (!p.hasPermission("mg.admin")) {sendHelp(p); return true;}

        switch (sub)
        {
            case "list" -> {list(p); return true;}
            case "reload" ->
            {
                plugin.arenas().stopAll();
                plugin.reloadEverything();
                Msg.send(p, "admin.reloaded");
                return true;
            }
            case "save" -> {plugin.saveEverything(); Msg.send(p, "admin.saved"); return true;}
            case "debuglog" -> {handleDebugLog(p, args); return true;}
            default -> {}
        }

        // Игро-специфичные подкоманды (setcenter/setradius/... в MgCore) — до
        // ядровой логики, требующей арену. Игра сама парсит и валидирует свои аргументы.
        if (owner != null && owner.onCommand(p, sub, args)) {return true;}

        if (args.length < 2) {Msg.send(p, "errors.need-args"); return true;}
        String id = args[1].toUpperCase(Locale.ROOT);

        switch (sub)
        {
            case "create" ->
            {
                String target = targetGameId(p, args);
                if (target == null) {return true;}
                if (plugin.arenas().exists(target, id)) {Msg.send(p, "errors.arena-exists", Msg.ph("arena", id)); return true;}
                Arena arena = plugin.arenas().create(id, p.getWorld().getName(), target);
                arena.setLobby(p.getLocation());
                plugin.arenas().save(arena);
                Msg.send(p, "admin.created", Msg.ph("arena", id));
                Msg.send(p, "admin.created-hint", Msg.ph("arena", id));
                return true;
            }
            case "remove" ->
            {
                Arena victim = resolveArena(p, id, args);
                if (victim == null) {return true;}
                plugin.arenas().delete(victim.getGameId(), victim.getId());
                Msg.send(p, "admin.removed", Msg.ph("arena", id));
                return true;
            }
            default -> {}
        }

        Arena arena = resolveArena(p, id, args);
        if (arena == null) {return true;}

        switch (sub)
        {
            case "gui" -> new ArenaHubMenu(plugin, arena).open(p);
            case "enable", "disable" ->
            {
                if (sub.equals("enable"))
                {
                    List<ArenaCheck.Finding> findings = ArenaCheck.run(plugin, arena);
                    ArenaCheck.report(p, arena, findings);
                    if (ArenaCheck.hasCritical(findings)) {Msg.send(p, "check.enable-blocked", Msg.ph("arena", id)); return true;}
                }
                arena.setEnabled(sub.equals("enable"));
                plugin.arenas().save(arena);
                Msg.send(p, sub.equals("enable") ? "admin.enabled" : "admin.disabled", Msg.ph("arena", id));
            }
            case "check" -> ArenaCheck.report(p, arena, ArenaCheck.run(plugin, arena));
            case "setlobby" ->
            {
                arena.setLobby(p.getLocation().toBlockLocation().add(0.5, 0, 0.5));
                arena.setWorldName(p.getWorld().getName());
                plugin.arenas().save(arena);
                Msg.send(p, "admin.lobby-set", Msg.ph("arena", id));
            }
            case "addspawn" ->
            {
                p.getInventory().addItem(SetupMarkers.markerItem(arena, "spawn", Material.BEACON, null));
                Msg.send(p, "admin.marker-given");
            }
            case "set" -> handleSet(p, arena, args);
            case "start" ->
            {
                GameSession s = (GameSession) arena.getSession();
                if (s == null || !s.forceStart()) {Msg.send(p, "admin.cannot-start", Msg.ph("arena", id)); return true;}
                Msg.send(p, "admin.started", Msg.ph("arena", id));
            }
            case "stop" ->
            {
                if (arena.getSession() != null) {((GameSession) arena.getSession()).forceCleanup();}
                Msg.send(p, "admin.stopped", Msg.ph("arena", id));
            }
            default -> Msg.send(p, "errors.unknown-sub");
        }
        return true;
    }

    private void handleSet(Player p, Arena arena, String[] args)
    {
        if (args.length < 4) {Msg.send(p, "admin.set-usage"); return;}
        String key = args[2].toLowerCase(Locale.ROOT);
        int n;
        try {n = Integer.parseInt(args[3]);}
        catch (NumberFormatException e) {Msg.send(p, "errors.not-a-number"); return;}
        switch (key)
        {
            case "minplayers" -> arena.setMinPlayers(Math.max(1, n));
            case "maxplayers" -> arena.setMaxPlayers(Math.max(1, n));
            case "lobbycountdown" -> arena.setLobbyCountdownSeconds(Math.max(0, n));
            case "countdownfull" -> arena.setCountdownFullSeconds(Math.max(0, n));
            case "duration" -> arena.setMatchDurationSeconds(Math.max(0, n));
            default -> {Msg.send(p, "admin.set-usage"); return;}
        }
        plugin.arenas().save(arena);
        Msg.send(p, "admin.set-ok", Msg.ph("arena", arena.getId()), Msg.ph("key", key), Msg.ph("n", n));
    }

    private void handleDebugLog(Player p, String[] args)
    {
        if (!p.hasPermission("mg.admin.debug")) {Msg.send(p, "errors.no-permission"); return;}
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action)
        {
            case "on" -> {DebugLog.setEnabled(true); Msg.send(p, "admin.debuglog-on");}
            case "off" -> {DebugLog.setEnabled(false); Msg.send(p, "admin.debuglog-off");}
            case "clear" -> {int n = DebugLog.clear(); Msg.send(p, "admin.debuglog-clear", Msg.ph("n", n));}
            case "save" ->
            {
                var f = DebugLog.save();
                if (f == null) {Msg.send(p, "admin.debuglog-save-fail");}
                else {Msg.send(p, "admin.debuglog-save", Msg.ph("file", f.getName()));}
            }
            default -> Msg.send(p, "admin.debuglog-status",
                Msg.ph("state", Msg.raw(DebugLog.on() ? "admin.state-on" : "admin.state-off")), Msg.ph("n", DebugLog.buffered()));
        }
    }

    private void list(Player p)
    {
        Msg.send(p, "admin.list-header");
        for (Arena a : scopedArenas())
        {
            String statusKey = !a.isEnabled() ? "admin.list-disabled"
                : a.getSession() == null ? "admin.list-idle" : "admin.list-running";
            int current = a.getSession() == null ? 0 : a.getSession().players().size();
            String label = owner != null || a.getGameId() == null ? a.getId() : a.getGameId() + ":" + a.getId();
            Msg.send(p, "admin.list-entry", Msg.ph("arena", label),
                Msg.phC("status", Msg.get(statusKey)), Msg.ph("current", current), Msg.ph("max", a.getMaxPlayers()));
        }
    }

    private void sendHelp(Player p)
    {
        for (Component line : Msg.getList("help.player")) {p.sendMessage(line);}
        if (p.hasPermission("mg.admin"))
        {
            for (Component line : Msg.getList("help.admin")) {p.sendMessage(line);}
            if (owner != null) {for (Component line : owner.helpLines(p)) {p.sendMessage(line);}}
        }
    }

    // ===== tab =====

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player p)) {return List.of();}
        List<String> out = new ArrayList<>();
        if (args.length == 1)
        {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (p.hasPermission("mg.admin")) {subs.addAll(ADMIN_SUBS);}
            filter(subs, args[0], out);
            mergeGameCompletions(p, args, out);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2)
        {
            switch (sub)
            {
                case "join", "remove", "enable", "disable", "gui", "setlobby", "addspawn", "set", "check", "start", "stop" ->
                    filter(new ArrayList<>(scopedIds()), args[1], out);
                case "debuglog" -> filter(List.of("on", "off", "save", "clear", "status"), args[1], out);
                default -> {}
            }
        }
        else if (args.length == 3 && sub.equals("set"))
        {
            filter(SET_KEYS, args[2], out);
        }
        else if (args.length == 3 && owner == null)
        {
            List<String> slugs = new ArrayList<>();
            for (Minigame g : plugin.games()) {slugs.add(g.id());}
            filter(slugs, args[2], out);
        }
        mergeGameCompletions(p, args, out);
        return out;
    }

    /** Домешать таб-подсказки игро-специфичных подкоманд (только админам). */
    private void mergeGameCompletions(Player p, String[] args, List<String> out)
    {
        if (!p.hasPermission("mg.admin")) {return;}
        if (owner == null) {return;}
        for (String s : owner.tabComplete(p, args)) {if (!out.contains(s)) {out.add(s);}}
    }

    private void filter(List<String> options, String prefix, List<String> out)
    {
        String low = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {if (o.toLowerCase(Locale.ROOT).startsWith(low)) {out.add(o);}}
    }
}
