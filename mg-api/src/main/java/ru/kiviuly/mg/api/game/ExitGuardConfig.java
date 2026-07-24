package ru.kiviuly.mg.api.game;

import org.bukkit.entity.EntityType;

/**
 * Конфиг встроенного «стража выхода»: что делает движок, когда живой игрок отключился
 * во время идущего матча. Игра включает фичу, вернув не-null из {@link Minigame#exitGuard()};
 * дальше движок всё ведёт сам — держит игрока оффлайн-участником на грейс-период, ставит
 * болванчика в его экипировке, обрабатывает возврат/гибель/таймаут. Коду игры делать ничего
 * не нужно (game-специфику при желании можно добавить в хуках {@code onPlayerDisconnect}/
 * {@code onPlayerReconnect}/{@code onPlayerEliminated}).
 *
 * @param graceSeconds сколько секунд держать отключившегося участником матча; по истечении —
 *                     заочная гибель. Минимум 5с.
 * @param standIn      тип моба-болванчика на месте игрока в его экипировке (напр.
 *                     {@link EntityType#ZOMBIE}); {@code null} — без болванчика (только грейс).
 * @param killIsDeath  убийство болванчика игроком = немедленная гибель владельца (кредит убийце).
 * @param dropItems    ронять инвентарь болванчика при гибели/таймауте (иначе вещи исчезают).
 */
public record ExitGuardConfig(int graceSeconds, EntityType standIn, boolean killIsDeath, boolean dropItems)
{
    /** Разумный дефолт: зомби-болванчик, убийство = гибель, лут падает. */
    public static ExitGuardConfig standard(int graceSeconds)
    {
        return new ExitGuardConfig(graceSeconds, EntityType.ZOMBIE, true, true);
    }

    /** Только грейс-окно без болванчика: вышел — есть N секунд вернуться, иначе выбыл. */
    public static ExitGuardConfig graceOnly(int graceSeconds)
    {
        return new ExitGuardConfig(graceSeconds, null, false, true);
    }
}
