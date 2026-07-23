package ru.kiviuly.mg.api;

import org.bukkit.command.TabExecutor;
import ru.kiviuly.mg.api.arena.ArenaService;
import ru.kiviuly.mg.api.game.Minigame;
import ru.kiviuly.mg.api.net.TransportService;
import ru.kiviuly.mg.api.stats.StatsService;

/**
 * Фасад платформы Kiviuly minigame. Ядро ({@code mg-core}) публикует его в Bukkit
 * {@code ServicesManager}; игровой плагин забирает через
 * {@code getServer().getServicesManager().load(MgCore.class)} и регистрирует свою
 * игру методом {@link #register(Minigame)}. Игры зависят только от этого модуля
 * ({@code mg-api}) — реализацию ядра они не видят.
 */
public interface MgCore
{
    /** Зарегистрировать игру (её {@link Minigame}). Вызывать в onEnable игрового плагина. */
    void register(Minigame game);

    /**
     * Обработчик команды, ПРИВЯЗАННЫЙ к этой игре: видит только её арены и делегирует
     * незнакомые подкоманды только ей. Игра объявляет команду в своём plugin.yml и
     * ставит его исполнителем:
     * <pre>{@code
     * var cmd = getCommand("sw");
     * TabExecutor h = core.commandFor(game);
     * cmd.setExecutor(h); cmd.setTabCompleter(h);
     * }</pre>
     */
    TabExecutor commandFor(Minigame game);

    /** Реестр арен и вход/выход игроков. */
    ArenaService arenas();

    /** Хранилище статистики. */
    StatsService stats();

    /** Межсерверный транспорт (на одиночном сервере — no-op). */
    TransportService transport();
}
