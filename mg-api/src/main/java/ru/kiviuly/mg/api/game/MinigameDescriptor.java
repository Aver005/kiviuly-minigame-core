package ru.kiviuly.mg.api.game;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

/**
 * Паспорт игры: по нему ядро идентифицирует игру, а хаб рисует карточку в селекторе.
 * {@code id} стабилен во времени (по нему идёт статистика). {@code minPlayers}/
 * {@code maxPlayers} — подсказки для селектора; фактические лимиты берутся из
 * конкретной {@link ru.kiviuly.mg.api.arena.Arena}. {@code icon} может быть null.
 */
public record MinigameDescriptor(
    String id,
    Component displayName,
    ItemStack icon,
    int minPlayers,
    int maxPlayers,
    boolean teamBased)
{
}
