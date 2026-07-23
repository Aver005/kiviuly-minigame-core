package ru.kiviuly.mg.api.game;

import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * Команда в матче. В FFA-играх каждый игрок — своя команда из одного (движок создаёт
 * их автоматически), поэтому игровой код работает единообразно и для командных, и
 * для одиночных режимов. Полноценное распределение по командам (2v2 и т.п.) —
 * следующая фаза; сейчас движок отдаёт FFA-команды.
 */
public interface Team
{
    /** Стабильный идентификатор команды в рамках матча. */
    String id();

    /** Отображаемое имя (для HUD/сообщений). */
    Component displayName();

    /** Цвет команды. */
    TextColor color();

    /** Участники (UUID). */
    Set<UUID> members();

    /** Жива ли команда (жив хоть один участник). */
    boolean isAlive();
}
