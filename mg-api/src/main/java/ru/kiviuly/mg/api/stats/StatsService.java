package ru.kiviuly.mg.api.stats;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Хранилище статистики игроков. Бэкенд (SQLite/MySQL) прозрачен для игр: они видят
 * только этот контракт. Запись — асинхронна; чтение — асинхронно с callback в main thread.
 *
 * <p>Модель — ПРОИЗВОЛЬНЫЕ именованные счётчики на игрока: каждая игра пишет свои
 * (Escape — {@code ores_mined}/{@code quests_completed}/…, базовые — {@code wins}/
 * {@code loses}/{@code kills}/{@code played}). Так один бэкенд обслуживает все игры без
 * игро-специфичных колонок в контракте и ложится на будущий общий MySQL.</p>
 */
public interface StatsService
{
    /**
     * Строка статистики: ник + карта счётчиков. Базовые счётчики доступны удобными
     * методами; произвольные — через {@link #counter(String)}.
     */
    record Row(String name, Map<String, Integer> counters)
    {
        /** Значение произвольного счётчика (0, если не задан). */
        public int counter(String key) {return counters.getOrDefault(key, 0);}

        public int wins() {return counter("wins");}
        public int loses() {return counter("loses");}
        public int kills() {return counter("kills");}
        public int played() {return counter("played");}
    }

    /** Одна позиция лидерборда: место {@code rank} (с 1), значение счётчика {@code value}. */
    record LeaderboardEntry(UUID uuid, String name, int value, int rank) {}

    /** Итог матча одному игроку: +1 к played, +1 к wins или loses, +kills. */
    void recordMatch(UUID uuid, String name, boolean won, int kills);

    /** Увеличить произвольный счётчик на {@code delta}. */
    void add(UUID uuid, String name, String column, int delta);

    /** Записать точное значение счётчика. */
    void set(UUID uuid, String name, String column, int value);

    /** Счётчик = max(текущее, {@code value}) — для «рекордов» (напр. лучший матч). */
    void max(UUID uuid, String name, String column, int value);

    /** Прочитать статистику по нику (async; callback в main thread; row == null если нет). */
    void findByName(String name, Consumer<Row> callback);

    /**
     * Топ-N игроков по счётчику {@code stat} для игры {@code gameId} (async).
     * Пока хранилище одно-игровое, {@code gameId} игнорируется; станет значимым на
     * Фазе 2 (общая БД).
     */
    CompletableFuture<List<LeaderboardEntry>> top(String gameId, String stat, int n);
}
