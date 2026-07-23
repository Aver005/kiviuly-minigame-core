package ru.kiviuly.mg.api.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * NamespacedKey платформы (PDC-метки предметов). Ядро держит только общие ключи;
 * конкретная игра добавляет свои по образцу (объявить поле + инициализировать в
 * {@link #init}).
 */
public final class Keys
{
    /** Тег системного предмета лобби/матча ("leave", "kit-selector", ...). */
    public static NamespacedKey SPECIAL_ITEM;
    /** Тип маркера настройки арены ("spawn", "lobby", ...). */
    public static NamespacedKey MARKER_TYPE;
    /** Id арены на предмете-маркере. */
    public static NamespacedKey MARKER_ARENA;
    /** Доп. данные маркера (имя точки/группа) — опционально. */
    public static NamespacedKey MARKER_EXTRA;

    private Keys() {}

    public static void init(Plugin plugin)
    {
        SPECIAL_ITEM = new NamespacedKey(plugin, "special_item");
        MARKER_TYPE = new NamespacedKey(plugin, "marker_type");
        MARKER_ARENA = new NamespacedKey(plugin, "marker_arena");
        MARKER_EXTRA = new NamespacedKey(plugin, "marker_extra");
    }
}
