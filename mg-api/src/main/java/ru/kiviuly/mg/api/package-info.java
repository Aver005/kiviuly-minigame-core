/**
 * Контракты платформы Kiviuly minigame (mg-api).
 *
 * <p>Игры и хаб зависят только от этого модуля; реализацию ядра ({@code mg-core})
 * они не видят и находят её в рантайме через Bukkit {@code ServicesManager}
 * (фасад {@link ru.kiviuly.mg.api.MgCore}).</p>
 *
 * <ul>
 *   <li>{@code game/} — {@link ru.kiviuly.mg.api.game.GamePhase},
 *       {@link ru.kiviuly.mg.api.game.Match},
 *       {@link ru.kiviuly.mg.api.game.MatchPlayer},
 *       {@link ru.kiviuly.mg.api.game.MatchResult},
 *       {@link ru.kiviuly.mg.api.game.Team},
 *       {@link ru.kiviuly.mg.api.game.MinigameDescriptor},
 *       {@link ru.kiviuly.mg.api.game.Minigame} (точка расширения).</li>
 *   <li>{@code arena/} — {@link ru.kiviuly.mg.api.arena.Arena} (конфиг площадки),
 *       {@link ru.kiviuly.mg.api.arena.ArenaService}.</li>
 *   <li>{@code data/} — {@link ru.kiviuly.mg.api.data.DataKey} (типизированные ключи).</li>
 *   <li>{@code stats/} — {@link ru.kiviuly.mg.api.stats.StatsService} (+ лидерборды).</li>
 *   <li>{@code net/} — {@link ru.kiviuly.mg.api.net.TransportService},
 *       {@link ru.kiviuly.mg.api.net.QueueService}.</li>
 *   <li>{@link ru.kiviuly.mg.api.MgCore} — фасад платформы.</li>
 * </ul>
 *
 * <p>Заложено, но ещё не «оживлено» полностью: реальное распределение по
 * {@code Team} (сейчас FFA-дефолт), типизированные настройки {@code Arena} через
 * {@code DataKey}, реализация {@code QueueService} и мульти-игровой {@code stats.top}
 * (общая БД — Фаза 2). См. docs/06-roadmap.md.</p>
 */
package ru.kiviuly.mg.api;
