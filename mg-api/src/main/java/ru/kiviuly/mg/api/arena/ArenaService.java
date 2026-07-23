package ru.kiviuly.mg.api.arena;

import java.util.Collection;
import java.util.List;
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
    /**
     * Арена по id, ЕСЛИ он однозначен во всех играх. При конфликте (одинаковый id в
     * разных мини-играх) вернёт null — используй {@link #get(String, String)} или
     * {@link #findById(String)}.
     */
    Arena get(String id);

    /** Арена конкретной игры (точное разрешение пары «игра + id»). */
    Arena get(String gameId, String arenaId);

    /** Все арены с таким id во всех играх (пусто / одна / несколько при конфликте). */
    List<Arena> findById(String arenaId);

    /** Есть ли арена с таким id хоть в одной игре. */
    boolean exists(String id);

    /** Есть ли такая арена у конкретной игры. */
    boolean exists(String gameId, String arenaId);

    /** Все арены (всех игр). */
    Collection<Arena> all();

    /** Идентификаторы всех арен. */
    Set<String> ids();

    /** Арены конкретной игры (по {@code Minigame.id()}). */
    Collection<Arena> all(String gameId);

    /** Идентификаторы арен конкретной игры. */
    Set<String> ids(String gameId);

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
