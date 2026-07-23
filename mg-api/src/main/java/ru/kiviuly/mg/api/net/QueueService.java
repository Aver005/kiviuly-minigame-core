package ru.kiviuly.mg.api.net;

import org.bukkit.entity.Player;

/**
 * Очередь/матчмейкинг на игру. Живёт на хабе (и/или прокси) — контракт заложен
 * заранее; реализация появляется на Фазе 3–5 (docs/04-cross-server.md,
 * docs/06-roadmap.md).
 */
public interface QueueService
{
    /** Поставить игрока в очередь на игру по id. */
    void enqueue(Player p, String gameId);

    /** Убрать игрока из очереди. */
    void dequeue(Player p);

    /** Сколько игроков в очереди на эту игру. */
    int queued(String gameId);
}
