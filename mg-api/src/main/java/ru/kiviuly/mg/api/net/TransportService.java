package ru.kiviuly.mg.api.net;

import org.bukkit.entity.Player;

/**
 * Межсерверный транспорт игроков (через прокси). На одиночном сервере — no-op
 * реализация (некуда отправлять): игры зовут один и тот же контракт независимо от
 * топологии. Подробнее — docs/04-cross-server.md.
 */
public interface TransportService
{
    /** Отправить игрока на конкретный сервер сети. */
    void send(Player p, String serverName);

    /** Отправить игрока на любой сервер, где крутится игра с этим id. */
    void sendToGame(Player p, String gameId);
}
