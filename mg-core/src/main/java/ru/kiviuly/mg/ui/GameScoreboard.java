package ru.kiviuly.mg.ui;

import java.util.ArrayList;
import java.util.List;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import ru.kiviuly.mg.api.game.GamePhase;
import ru.kiviuly.mg.game.GameSession;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Сайдбар матча: одна доска на сессию, показывается всем её участникам. Строки —
 * стандартные (арена/фаза/игроки/время) плюс {@link ru.kiviuly.mg.api.game.Minigame#scoreboardLines}.
 * Числа скрыты (NumberFormat.blank); уникальность записей — невидимым суффиксом.
 */
public class GameScoreboard
{
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Scoreboard board;
    private Objective objective;

    public void update(GameSession s)
    {
        ensure(s);
        // очистить старые записи
        for (String entry : new ArrayList<>(board.getEntries())) {board.resetScores(entry);}

        List<Component> lines = buildLines(s);
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++)
        {
            String entry = unique(LEGACY.serialize(Items.flat(lines.get(i))), i);
            objective.getScore(entry).setScore(score--);
        }
        for (Player p : s.onlinePlayers()) {p.setScoreboard(board);}
    }

    private void ensure(GameSession s)
    {
        if (board != null) {return;}
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = board.registerNewObjective("sbw", Criteria.DUMMY,
            Items.flat(Msg.get("hud.title", Msg.ph("game", s.game().displayName()))));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
    }

    private List<Component> buildLines(GameSession s)
    {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(Msg.get("hud.line-arena", Msg.ph("arena", s.arena().getId())));
        lines.add(Msg.get("hud.line-phase", Msg.ph("phase", Msg.raw("hud.phase-" + s.phase().name().toLowerCase()))));
        if (s.phase() == GamePhase.RUNNING)
        {
            lines.add(Msg.get("hud.line-alive", Msg.ph("n", s.aliveCount())));
            int rem = s.remainingSeconds();
            if (rem >= 0) {lines.add(Msg.get("hud.line-time", Msg.ph("time", format(rem))));}
        }
        else
        {
            lines.add(Msg.get("hud.line-players", Msg.ph("n", s.players().size()), Msg.ph("max", s.arena().getMaxPlayers())));
        }
        for (Component extra : s.game().scoreboardLines(s, null)) {lines.add(extra);}
        lines.add(Component.empty());
        return lines;
    }

    public void remove(Player p)
    {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private static String format(int seconds)
    {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Уникальный (невидимый суффикс из цвет-кодов) — записи скорборда не должны совпадать. */
    private static String unique(String base, int index)
    {
        String suffix = "§" + "0123456789abcdef".charAt(index & 0xF) + "§r";
        if (base.length() > 40) {base = base.substring(0, 40);}
        return base + suffix;
    }
}
