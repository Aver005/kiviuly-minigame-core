package ru.kiviuly.mg.api.data;

import java.util.Objects;

/**
 * Типизированный ключ — замена строковых ключей в обобщённых мапах состояния
 * ({@link ru.kiviuly.mg.api.game.Match#data()}). Даёт compile-time тип значения и
 * защищает от опечаток. Объявляй ключи один раз рядом с игрой:
 *
 * <pre>{@code
 * public final class SkyWarsKeys {
 *     public static final DataKey<Integer> ROUND = DataKey.of("round", Integer.class);
 * }
 * // m.set(SkyWarsKeys.ROUND, 1);  int r = m.get(SkyWarsKeys.ROUND, 1);
 * }</pre>
 *
 * Равенство — по {@code id}, поэтому два ключа с одним id адресуют одно значение.
 */
public final class DataKey<T>
{
    private final String id;
    private final Class<T> type;

    private DataKey(String id, Class<T> type)
    {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> DataKey<T> of(String id, Class<T> type) {return new DataKey<>(id, type);}

    public String id() {return id;}
    public Class<T> type() {return type;}

    @Override public boolean equals(Object o) {return o instanceof DataKey<?> k && id.equals(k.id);}
    @Override public int hashCode() {return id.hashCode();}
    @Override public String toString() {return "DataKey[" + id + ":" + type.getSimpleName() + "]";}
}
