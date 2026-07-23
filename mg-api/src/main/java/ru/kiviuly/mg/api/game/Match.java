package ru.kiviuly.mg.api.game;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ru.kiviuly.mg.api.MgCore;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.data.DataKey;

/**
 * Игро-видимый контракт одного матча. Реализация — движок {@code GameSession} в
 * ядре; наследники {@link Minigame} получают {@code Match} в хуки и работают через
 * этот API. Управляющие методы движка (добавить/убрать/выбить игрока, форс-старт)
 * в контракт не входят — они внутренние для ядра.
 */
public interface Match
{
    /** Фасад платформы: статистика, реестр арен и т.д. */
    MgCore core();

    /** Конфигурация площадки этого матча. */
    Arena arena();

    /** Текущая фаза. */
    GamePhase phase();

    /** Принимает ли матч новых игроков (фаза лобби/отсчёта). */
    boolean acceptsPlayers();

    /** Сколько идёт матч (секунды). */
    int elapsedSeconds();

    /** Сколько осталось (секунды); -1 = без лимита. */
    int remainingSeconds();

    /** Обобщённое состояние матча (ключ→значение). Клади сюда per-match данные игры. */
    Map<String, Object> data();

    /** Типизированное чтение состояния матча по {@link DataKey} (null, если нет). */
    <T> T get(DataKey<T> key);

    /** Типизированное чтение с дефолтом. */
    <T> T get(DataKey<T> key, T def);

    /** Типизированная запись состояния матча по {@link DataKey}. */
    <T> void set(DataKey<T> key, T value);

    /** Все участники матча (живые + спектаторы). */
    Collection<MatchPlayer> players();

    /** Участник по UUID (или null). */
    MatchPlayer player(UUID uuid);

    /** Есть ли такой участник. */
    boolean hasPlayer(UUID uuid);

    /** Команды матча. В FFA — по команде на игрока (движок создаёт автоматически). */
    Collection<Team> teams();

    /** Команда игрока (или null, если он не участник). */
    Team teamOf(UUID player);

    /** Живые (не выбывшие) игроки, что сейчас онлайн. */
    List<Player> alivePlayers();

    /** Сколько живых. */
    int aliveCount();

    /** Все онлайн-участники сессии (живые + спектаторы). */
    List<Player> onlinePlayers();

    /** Запомнить блок ПЕРЕД изменением в матче, иначе cleanup его не откатит. */
    void rememberBlock(Block block);

    /** Запомнить прежнее состояние блока напрямую (для BlockPlaceEvent: блок уже заменён). */
    void rememberState(BlockState old);

    /** Зарегистрировать заспавненную сущность — удалится после матча. */
    void trackEntity(Entity entity);

    /** Выбить игрока из матча в спектаторы (died=true — как гибель). Игра может звать сама. */
    void eliminate(Player player, boolean died);

    /** Дефолтное условие победы: последний выживший, или ничья по истечении времени. */
    MatchResult defaultResult();

    /** Разослать сообщение (ключ из messages.yml) всем онлайн-участникам сессии. */
    void broadcast(String key, TagResolver... resolvers);
}
