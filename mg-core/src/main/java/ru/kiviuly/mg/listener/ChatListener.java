package ru.kiviuly.mg.listener;

import java.util.HashSet;
import java.util.Set;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.game.GameSession;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Чат участника матча виден только игрокам его арены (config chat.scoped-to-arena),
 * формат — config chat.format. Событие асинхронное; читаем только простую карту
 * игрок→сессия и рендерим MiniMessage — без обращений к Bukkit-состоянию.
 */
public class ChatListener implements Listener
{
    private final MgCorePlugin plugin;

    public ChatListener(MgCorePlugin plugin) {this.plugin = plugin;}

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent e)
    {
        if (!plugin.getConfig().getBoolean("chat.scoped-to-arena", true)) {return;}
        GameSession session = plugin.arenas().sessionOf(e.getPlayer());
        if (session == null) {return;}

        String format = plugin.getConfig().getString("chat.format", "<gray>[<arena>] <white><player><gray>: <reset><message>");
        String arenaId = session.arena().getId();

        Set<Audience> viewers = new HashSet<>(session.onlinePlayers());
        viewers.add(e.getPlayer());
        e.viewers().clear();
        e.viewers().addAll(viewers);

        e.renderer((source, sourceName, message, viewer) -> Msg.mm(format,
            Msg.ph("arena", arenaId), Msg.phC("player", sourceName), Msg.phC("message", message)));
    }
}
