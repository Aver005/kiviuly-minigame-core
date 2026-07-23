package ru.kiviuly.mg.command;

import java.util.ArrayList;
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
import ru.kiviuly.mg.arena.ArenaCheck;
import ru.kiviuly.mg.arena.SetupMarkers;
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

    public MinigameCommand(MgCorePlugin plugin) {this.plugin = plugin;}

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
                Arena arena = plugin.arenas().get(args[1]);
                if (arena == null) {Msg.send(p, "errors.arena-not-found", Msg.ph("arena", args[1])); return true;}
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
        if (plugin.game().onCommand(p, sub, args)) {return true;}

        if (args.length < 2) {Msg.send(p, "errors.need-args"); return true;}
        String id = args[1].toUpperCase(Locale.ROOT);

        switch (sub)
        {
            case "create" ->
            {
                if (plugin.arenas().exists(id)) {Msg.send(p, "errors.arena-exists", Msg.ph("arena", id)); return true;}
                Arena arena = plugin.arenas().create(id, p.getWorld().getName());
                arena.setLobby(p.getLocation());
                plugin.arenas().save(arena);
                Msg.send(p, "admin.created", Msg.ph("arena", id));
                Msg.send(p, "admin.created-hint", Msg.ph("arena", id));
                return true;
            }
            case "remove" ->
            {
                if (!plugin.arenas().exists(id)) {Msg.send(p, "errors.arena-not-found", Msg.ph("arena", id)); return true;}
                plugin.arenas().delete(id);
                Msg.send(p, "admin.removed", Msg.ph("arena", id));
                return true;
            }
            default -> {}
        }

        Arena arena = plugin.arenas().get(id);
        if (arena == null) {Msg.send(p, "errors.arena-not-found", Msg.ph("arena", id)); return true;}

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
        for (Arena a : plugin.arenas().all())
        {
            String statusKey = !a.isEnabled() ? "admin.list-disabled"
                : a.getSession() == null ? "admin.list-idle" : "admin.list-running";
            int current = a.getSession() == null ? 0 : a.getSession().players().size();
            Msg.send(p, "admin.list-entry", Msg.ph("arena", a.getId()),
                Msg.phC("status", Msg.get(statusKey)), Msg.ph("current", current), Msg.ph("max", a.getMaxPlayers()));
        }
    }

    private void sendHelp(Player p)
    {
        for (Component line : Msg.getList("help.player")) {p.sendMessage(line);}
        if (p.hasPermission("mg.admin"))
        {
            for (Component line : Msg.getList("help.admin")) {p.sendMessage(line);}
            for (Component line : plugin.game().helpLines(p)) {p.sendMessage(line);}
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
                    filter(new ArrayList<>(plugin.arenas().ids()), args[1], out);
                case "debuglog" -> filter(List.of("on", "off", "save", "clear", "status"), args[1], out);
                default -> {}
            }
        }
        else if (args.length == 3 && sub.equals("set"))
        {
            filter(SET_KEYS, args[2], out);
        }
        mergeGameCompletions(p, args, out);
        return out;
    }

    /** Домешать таб-подсказки игро-специфичных подкоманд (только админам). */
    private void mergeGameCompletions(Player p, String[] args, List<String> out)
    {
        if (!p.hasPermission("mg.admin")) {return;}
        for (String s : plugin.game().tabComplete(p, args)) {if (!out.contains(s)) {out.add(s);}}
    }

    private void filter(List<String> options, String prefix, List<String> out)
    {
        String low = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {if (o.toLowerCase(Locale.ROOT).startsWith(low)) {out.add(o);}}
    }
}
