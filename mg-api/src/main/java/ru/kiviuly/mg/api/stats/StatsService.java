package ru.kiviuly.mg.api.stats;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Хранилище статистики игроков. Бэкенд (SQLite/MySQL) прозрачен для игр: они видят
 * только этот контракт. Запись — асинхронна; чтение — асинхронно с callback в main
 * thread. Базовый набор счётчиков — wins/loses/kills/played.
 */
public interface StatsService
{
    /** Одна строка статистики (для чтения). */
    record Row(String name, int wins, int loses, int kills, int played) {}

    /** Одна позиция лидерборда: место {@code rank} (с 1), значение счётчика {@code value}. */
    record LeaderboardEntry(UUID uuid, String name, int value, int rank) {}

    /** Итог матча одному игроку: +1 к played, +1 к wins или loses, +kills. */
    void recordMatch(UUID uuid, String name, boolean won, int kills);

    /** Увеличить произвольный счётчик из whitelist бэкенда. */
    void add(UUID uuid, String name, String column, int delta);

    /** Прочитать статистику по нику (async; callback в main thread; row == null если нет). */
    void findByName(String name, Consumer<Row> callback);

    /**
     * Топ-N игроков по счётчику {@code stat} для игры {@code gameId} (async).
     * {@code stat} — из whitelist бэкенда (wins/loses/kills/played). Пока хранилище
     * одно-игровое, {@code gameId} игнорируется; станет значимым на Фазе 2 (общая БД).
     */
    CompletableFuture<List<LeaderboardEntry>> top(String gameId, String stat, int n);
}

