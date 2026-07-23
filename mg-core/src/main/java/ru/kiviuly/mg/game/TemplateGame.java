package ru.kiviuly.mg.game;

import org.bukkit.entity.Player;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.game.Match;
import ru.kiviuly.mg.api.game.MatchPlayer;
import ru.kiviuly.mg.api.game.MatchResult;
import ru.kiviuly.mg.api.game.Minigame;
import ru.kiviuly.mg.api.util.Msg;

/**
 * ЗАГЛУШКА игры — точка старта для твоей мини-игры. Ничего игрового не делает:
 * матч завершается дефолтным условием {@link Match#defaultResult()} (последний
 * выживший / ничья по таймеру), чтобы каркас был проверяемо рабочим.
 *
 * Как сделать свою игру:
 *   1. Создай наследника {@link Minigame} (в отдельном плагине-игре — Фаза 1).
 *   2. Переопредели нужные хуки (onStart/onTick/giveLoadout/onPlayerEliminated/
 *      checkResult/onEnd/scoreboardLines). Состояние матча держи в
 *      {@link Match#data()} и полях {@link MatchPlayer}.
 *   3. Зарегистрируй его через {@code MgCore.register(...)}.
 * Подробно — docs/03-making-a-game.md.
 */
public class TemplateGame extends Minigame
{
    public TemplateGame(MgCorePlugin plugin) {super(plugin);}

    @Override
    public String id() {return "template";}

    @Override
    public String displayName() {return Msg.raw("template-game.display-name");}

    @Override
    public void onStart(Match m)
    {
        // TODO: старт матча — раздать правила/цели, поставить блоки (через m.rememberBlock).
    }

    @Override
    public void giveLoadout(Match m, Player p)
    {
        // TODO: стартовый набор игрока (Items.fromSpec / вручную). Пусто = игрок без предметов.
    }

    @Override
    public void onTick(Match m)
    {
        // TODO: логика каждой секунды матча.
    }

    @Override
    public void onPlayerEliminated(Match m, MatchPlayer mp)
    {
        // TODO: реакция на выбывание (счёт, дроп, ...).
    }

    @Override
    public void onEnd(Match m, MatchResult result)
    {
        // TODO: концовка — награды/анимации. Победитель уже объявлен ядром.
    }

    // checkResult не переопределён — используется дефолт (последний выживший / таймер).
    // Свою победу задаёшь так:
    //   @Override public MatchResult checkResult(Match m) { ... return null; /* или MatchResult.of(...) */ }
}
