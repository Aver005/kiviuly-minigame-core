package ru.kiviuly.mg.ui;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.kiviuly.mg.api.game.GamePhase;
import ru.kiviuly.mg.game.GameSession;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Босс-бар матча: фаза/время сверху экрана. Прогресс во время матча = доля
 * оставшегося времени (бар «утекает»); в лобби/отсчёте — полный, жёлтый.
 */
public class GameBossBar
{
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private final Set<UUID> viewers = new HashSet<>();

    public void add(Player p)
    {
        viewers.add(p.getUniqueId());
        p.showBossBar(bar);
    }

    public void remove(Player p)
    {
        viewers.remove(p.getUniqueId());
        p.hideBossBar(bar);
    }

    public void update(GameSession s)
    {
        if (s.phase() == GamePhase.RUNNING)
        {
            int rem = s.remainingSeconds();
            int dur = s.arena().getMatchDurationSeconds();
            float progress = (dur > 0 && rem >= 0) ? clamp(rem / (float) dur) : 1f;
            bar.color(BossBar.Color.GREEN);
            bar.progress(progress);
            bar.name(Items.flat(Msg.get("hud.bossbar-running",
                Msg.ph("alive", s.aliveCount()), Msg.ph("time", format(Math.max(0, rem))))));
        }
        else
        {
            bar.color(BossBar.Color.YELLOW);
            bar.progress(1f);
            bar.name(Items.flat(Msg.get("hud.bossbar-lobby",
                Msg.ph("n", s.players().size()), Msg.ph("max", s.arena().getMaxPlayers()))));
        }
    }

    public void clearAll()
    {
        for (UUID id : viewers)
        {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {p.hideBossBar(bar);}
        }
        viewers.clear();
    }

    private static float clamp(float v) {return Math.max(0f, Math.min(1f, v));}

    private static String format(int seconds) {return String.format("%d:%02d", seconds / 60, seconds % 60);}
}
