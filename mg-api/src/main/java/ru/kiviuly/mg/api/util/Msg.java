package ru.kiviuly.mg.api.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Каталог сообщений (messages.yml, MiniMessage). Все тексты игрокам — только отсюда
 * (инвариант): ни одной захардкоженной строки в Java. Новый текст = новый ключ.
 * Дефолты подтягиваются из jar, поэтому новые ключи работают без ручного слияния.
 *
 * <p>Класс общий и статический: он загружается один раз (в jar ядра) и разделяется
 * всеми плагинами платформы. Ядро зовёт {@link #init(JavaPlugin)}, а каждый игровой
 * плагин — {@link #merge(JavaPlugin)} в своём onEnable, домешивая свой messages.yml.
 * Поиск ключа идёт по каталогам в порядке добавления (ядро → игры), поэтому ключи
 * держи в неймспейсе игры ({@code skywars.*}), чтобы не пересечься с ядром.</p>
 */
public final class Msg
{
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<JavaPlugin> sources = new ArrayList<>();
    private static final List<YamlConfiguration> catalogs = new ArrayList<>();

    private Msg() {}

    /** Инициализация ядром: сбрасывает каталоги и добавляет messages.yml ядра. */
    public static void init(JavaPlugin corePlugin)
    {
        sources.clear();
        catalogs.clear();
        add(corePlugin);
    }

    /** Домешать messages.yml игрового плагина в общий каталог (идемпотентно). */
    public static void merge(JavaPlugin plugin)
    {
        if (!sources.contains(plugin)) {add(plugin);}
    }

    /** Перечитать messages.yml всех зарегистрированных источников (/…/reload). */
    public static void reload()
    {
        catalogs.clear();
        for (JavaPlugin pl : sources) {catalogs.add(load(pl));}
    }

    private static void add(JavaPlugin plugin)
    {
        sources.add(plugin);
        catalogs.add(load(plugin));
    }

    private static YamlConfiguration load(JavaPlugin plugin)
    {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {plugin.saveResource("messages.yml", false);}
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml"))
        {
            if (in != null)
            {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        }
        catch (IOException e)
        {
            plugin.getLogger().warning("Failed to load bundled messages.yml defaults: " + e.getMessage());
        }
        return cfg;
    }

    /** Первый каталог, где есть ключ (или null). */
    private static YamlConfiguration find(String key)
    {
        for (YamlConfiguration c : catalogs)
        {
            if (c.getString(key) != null || c.isList(key)) {return c;}
        }
        return null;
    }

    public static String raw(String key)
    {
        YamlConfiguration c = find(key);
        String s = c == null ? null : c.getString(key);
        return s == null ? "<red>[no message: " + key + "]" : s;
    }

    public static Component mm(String rawText, TagResolver... resolvers)
    {
        return MM.deserialize(rawText, resolvers);
    }

    public static Component get(String key, TagResolver... resolvers)
    {
        return MM.deserialize(raw(key), resolvers);
    }

    public static List<Component> getList(String key, TagResolver... resolvers)
    {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(key)) {out.add(MM.deserialize(line, resolvers));}
        return out;
    }

    public static List<String> rawList(String key)
    {
        YamlConfiguration c = find(key);
        return c == null ? List.of() : c.getStringList(key);
    }

    public static void send(Audience to, String key, TagResolver... resolvers)
    {
        to.sendMessage(get(key, resolvers));
    }

    public static TagResolver ph(String name, Object value)
    {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver phMm(String name, String miniMessageValue)
    {
        return Placeholder.component(name, MM.deserialize(miniMessageValue));
    }

    public static TagResolver phC(String name, Component value)
    {
        return Placeholder.component(name, value);
    }
}
