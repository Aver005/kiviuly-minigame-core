package ru.kiviuly.mg.api.game;

import java.util.List;
import java.util.UUID;

/**
 * Итог матча: список победителей (может быть пустым = ничья/без победителя,
 * или содержать нескольких — для командных игр). Возврат не-null из
 * {@link Minigame#checkResult} завершает матч.
 */
public record MatchResult(List<UUID> winners)
{
    public boolean hasWinner() {return !winners.isEmpty();}

    public static MatchResult of(UUID winner) {return new MatchResult(List.of(winner));}
    public static MatchResult of(List<UUID> winners) {return new MatchResult(List.copyOf(winners));}
    public static MatchResult draw() {return new MatchResult(List.of());}
}
