package ru.kiviuly.mg.arena;

import ru.kiviuly.mg.api.arena.Arena;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Валидатор арены перед включением. CRITICAL блокирует {@code /mg enable},
 * WARNING — предупреждение. Тексты — check.* в messages.yml.
 */
public final class ArenaCheck
{
    public enum Severity {CRITICAL, WARNING, GOOD}

    public record Finding(Severity severity, Component message) {}

    private ArenaCheck() {}

    public static List<Finding> run(MgCorePlugin plugin, Arena a)
    {
        List<Finding> out = new ArrayList<>();
        if (a.getWorld() == null)
        {
            out.add(new Finding(Severity.CRITICAL, Msg.get("check.world", Msg.ph("world", String.valueOf(a.getWorldName())))));
        }
        if (a.getLobby() == null)
        {
            out.add(new Finding(Severity.CRITICAL, Msg.get("check.no-lobby")));
        }
        if (a.getSpawns().isEmpty())
        {
            out.add(new Finding(Severity.CRITICAL, Msg.get("check.no-spawns")));
        }
        else if (a.getSpawns().size() < a.getMinPlayers())
        {
            out.add(new Finding(Severity.WARNING, Msg.get("check.few-spawns",
                Msg.ph("have", a.getSpawns().size()), Msg.ph("need", a.getMinPlayers()))));
        }
        if (a.getMaxPlayers() < a.getMinPlayers())
        {
            out.add(new Finding(Severity.WARNING, Msg.get("check.player-range",
                Msg.ph("min", a.getMinPlayers()), Msg.ph("max", a.getMaxPlayers()))));
        }
        if (out.isEmpty())
        {
            out.add(new Finding(Severity.GOOD, Msg.get("check.good")));
        }
        return out;
    }

    public static boolean hasCritical(List<Finding> findings)
    {
        return findings.stream().anyMatch(f -> f.severity() == Severity.CRITICAL);
    }

    public static void report(Player p, Arena arena, List<Finding> findings)
    {
        Msg.send(p, "check.header", Msg.ph("arena", arena.getId()));
        for (Finding f : findings)
        {
            String prefix = switch (f.severity())
            {
                case CRITICAL -> Msg.raw("check.mark-critical");
                case WARNING -> Msg.raw("check.mark-warning");
                case GOOD -> Msg.raw("check.mark-good");
            };
            p.sendMessage(Msg.mm(prefix).append(f.message()));
        }
    }
}
