package ru.kiviuly.mg.net;

import org.bukkit.entity.Player;
import ru.kiviuly.mg.api.net.TransportService;
import ru.kiviuly.mg.api.util.DebugLog;
import ru.kiviuly.mg.api.util.DebugLog.Cat;

/**
 * No-op транспорт для одиночного сервера: отправлять некуда (нет прокси). Игры зовут
 * {@link TransportService} независимо от топологии; на сети его заменит реализация
 * поверх прокси/plugin-messaging (Фаза 3, docs/04-cross-server.md).
 */
public final class LocalTransport implements TransportService
{
    @Override
    public void send(Player p, String serverName)
    {
        DebugLog.log(Cat.ADMIN, "transport noop: send %s -> %s (single server)", p.getName(), serverName);
    }

    @Override
    public void sendToGame(Player p, String gameId)
    {
        DebugLog.log(Cat.ADMIN, "transport noop: sendToGame %s -> %s (single server)", p.getName(), gameId);
    }
}
