package ru.kiviuly.mg.api.arena;

import java.util.Collection;
import java.util.Set;

import org.bukkit.entity.Player;
import ru.kiviuly.mg.api.game.Match;

/**
 * Реестр арен + карта «игрок → активная сессия». Игро-видимая часть менеджера арен
 * ядра. Точка входа игрока — {@link #join}. Админ-операции создания/удаления/
 * сохранения арен — детали реализации ядра и в контракт не входят.
 */
public interface ArenaService
{
    /** Арена по id (регистронезависимо) или null. */
    Arena get(String id);

    /** Есть ли арена с таким id. */
    boolean exists(String id);

    /** Все арены. */
    Collection<Arena> all();

    /** Идентификаторы всех арен. */
    Set<String> ids();

    /** Сохранить арену на диск (после правок игро-специфичной разметки/настроек). */
    void save(Arena arena);

    /** Активный матч игрока (или null). */
    Match sessionOf(Player p);

    /** Участвует ли игрок в матче. */
    boolean inGame(Player p);

    /** Вход игрока в арену: проверки, создание сессии при необходимости, добавление в лобби. */
    void join(Player p, Arena arena);

    /** Выход игрока по своей воле. */
    void leave(Player p);
}
