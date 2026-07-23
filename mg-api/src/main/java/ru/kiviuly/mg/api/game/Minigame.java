package ru.kiviuly.mg.api.game;

import java.util.List;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import ru.kiviuly.mg.api.MgCore;
import ru.kiviuly.mg.api.arena.Arena;

/**
 * ТОЧКА РАСШИРЕНИЯ. Логика конкретной мини-игры — один наследник, зарегистрированный
 * через {@link MgCore#register}. Класс БЕЗ состояния матча: одно на весь плагин;
 * состояние матча живёт в {@link Match} ({@link Match#data()} + список
 * {@link MatchPlayer}). Все хуки по умолчанию пусты/разумны — переопредели нужные.
 *
 * <p>Жизненный цикл (кто зовёт что): движок ({@code GameSession}) ведёт
 * лобби→отсчёт→матч→конец, телепорт/снапшот/откат мира; игра решает правила, лут и
 * условие победы. См. docs/03-making-a-game.md.</p>
 */
public abstract class Minigame
{
    protected final MgCore core;

    protected Minigame(MgCore core) {this.core = core;}

    /** Уникальный id игры (например, "spleef"). */
    public abstract String id();

    /** Имя для HUD/сообщений (по умолчанию — id). */
    public String displayName() {return id();}

    /**
     * Паспорт игры для реестра ядра и карточки в селекторе хаба. По умолчанию выводится
     * из {@link #id()}/{@link #displayName()} без иконки и лимитов — переопредели для
     * богатой карточки (иконка, min/max, командный режим).
     */
    public MinigameDescriptor descriptor()
    {
        return new MinigameDescriptor(id(), Component.text(displayName()), null, 0, 0, false);
    }

    // ===== хуки жизненного цикла =====

    /** Игрок вошёл в лобби (снапшот уже снят, инвентарь очищен). */
    public void onLobbyJoin(Match m, Player p) {}

    /** Матч начался: игроки уже на спавнах, SURVIVAL, очищены. Раздай правила/HUD. */
    public void onStart(Match m) {}

    /** Стартовый набор одному игроку (вызывается на старте для каждого). */
    public void giveLoadout(Match m, Player p) {}

    /** Каждую секунду матча. */
    public void onTick(Match m) {}

    /** Игрок выбыл (умер) — уже переведён в спектаторы. */
    public void onPlayerEliminated(Match m, MatchPlayer mp) {}

    /** Игрок покинул сессию (leave/quit, не выбывание) — подчистить per-player UI/состояние. */
    public void onPlayerRemoved(Match m, UUID id) {}

    /**
     * Матч полностью завершён и откачен — НОРМАЛЬНО или форс-стопом (/stop, reload,
     * remove, shutdown). Зовётся ядром в конце cleanup. Подчисти per-match ресурсы
     * игры (боссбары, задачи), которые {@link #onEnd} мог не покрыть.
     */
    public void onCleanup(Match m) {}

    /**
     * Условие завершения. Верни не-null, чтобы закончить матч, иначе null (продолжаем).
     * По умолчанию — «последний выживший, или ничья по истечении времени».
     */
    public MatchResult checkResult(Match m) {return m.defaultResult();}

    /** Матч завершается: объяви победителя, выдай награды. Мир ещё не откачен. */
    public void onEnd(Match m, MatchResult result) {}

    /** Доп. строки сайдбара под стандартными (пусто = только стандартные). */
    public List<Component> scoreboardLines(Match m, Player viewer) {return List.of();}

    // ===== обобщённые хуки расширения (движок зовёт, игра решает) =====

    /**
     * Смертельный урон по игроку в матче. Верни {@code true} — движок штатно выбивает
     * игрока в спектаторы (дефолт). Верни {@code false} — игра сама обработала событие
     * (например, возродила игрока на его блоке): движок НЕ выбивает. Урон уже отменён.
     */
    public boolean onLethalDamage(Match m, Player p) {return true;}

    /**
     * Разрешён ли PvP-урон в лобби/отсчёте (для лобби-разминок). По умолчанию нет.
     */
    public boolean allowLobbyPvp() {return false;}

    /**
     * PvP-удар между участниками сессии в лобби/отсчёте (реального урона нет — ядро его
     * гасит, если {@link #allowLobbyPvp} не разрешил). Обобщённый колбэк для лобби-разминок
     * (напр. SkyWars считает им «избиение в лобби» и рисует фейковый фидбек).
     */
    public void onLobbyAttack(Match m, Player victim, Player damager, double damage) {}

    /**
     * Игро-специфичная подкоманда {@code /<cmd> <sub> ...} (движок зовёт после своих,
     * до «неизвестная подкоманда»; вызывающий уже прошёл проверку прав админа).
     * Верни {@code true}, если обработал. {@code args[0]} = сам {@code sub}.
     */
    public boolean onCommand(Player p, String sub, String[] args) {return false;}

    /** Подсказки таб-комплита для игро-специфичных подкоманд (движок домешивает к своим). */
    public List<String> tabComplete(Player p, String[] args) {return List.of();}

    /** Доп. строки в {@code /<cmd> help} (игро-специфичные команды). */
    public List<Component> helpLines(Player p) {return List.of();}

    /** {@code /<cmd> reload}: перечитать игро-специфичные конфиги. */
    public void onReload() {}

    /**
     * Арена только что создана ({@code /<cmd> create}). Момент, чтобы материализовать
     * игро-специфичный конфиг арены с дефолтами из глобального config.yml.
     */
    public void onArenaCreated(Arena arena) {}

    /** Арена удалена ({@code /<cmd> remove}): подчистить игро-специфичные данные арены. */
    public void onArenaRemoved(String arenaId) {}
}
