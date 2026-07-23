package ru.kiviuly.mg.api.arena;

import ru.kiviuly.mg.api.arena.Arena;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Разметка точек арены. Админ получает предмет-маркер ({@link #markerItem}) и ПКМ
 * по блоку помечает точку (см. SetupListener). Блоки-подсказки ({@link #placeAll})
 * показывают вне матча, где что; перед стартом матча снимаются ({@link #clearForMatch}),
 * чтобы игроки не появились внутри стекла.
 */
public final class SetupMarkers
{
    public static final Material SPAWN = Material.LIME_STAINED_GLASS;
    public static final Material LOBBY = Material.MAGENTA_STAINED_GLASS;

    private SetupMarkers() {}

    /** Предмет-маркер точки типа {@code type}: PDC MARKER_TYPE/MARKER_ARENA (+EXTRA). */
    public static ItemStack markerItem(Arena arena, String type, Material material, String extra)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("setup.marker-lore-arena", Msg.ph("arena", arena.getId())));
        lore.addAll(Msg.getList("setup.marker-lore-use"));
        ItemStack item = Items.named(material,
            Msg.get("setup.marker-name", Msg.ph("type", Msg.raw("marker-types." + type))), lore);
        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.MARKER_TYPE, PersistentDataType.STRING, type);
        pdc.set(Keys.MARKER_ARENA, PersistentDataType.STRING, arena.getId());
        if (extra != null) {pdc.set(Keys.MARKER_EXTRA, PersistentDataType.STRING, extra);}
        item.setItemMeta(meta);
        return item;
    }

    /** Расставить блоки-подсказки (лобби + спавны). Возвращает число обработанных точек. */
    public static int placeAll(Arena arena)
    {
        int count = 0;
        if (arena.getLobby() != null) {count += pillar(arena.getLobby(), LOBBY) ? 1 : 0;}
        for (Location loc : arena.getSpawns()) {count += pillar(loc, SPAWN) ? 1 : 0;}
        return count;
    }

    /** Снять блоки-подсказки перед матчем (стёкла лобби/спавнов → воздух). */
    public static void clearForMatch(Arena arena)
    {
        if (arena.getLobby() != null) {clearPillar(arena.getLobby(), LOBBY);}
        for (Location loc : arena.getSpawns()) {clearPillar(loc, SPAWN);}
    }

    private static boolean pillar(Location loc, Material material)
    {
        if (loc == null || loc.getWorld() == null) {return false;}
        setIfFree(loc.getBlock(), material);
        setIfFree(loc.getBlock().getRelative(0, 1, 0), material);
        return true;
    }

    private static void clearPillar(Location loc, Material material)
    {
        if (loc == null || loc.getWorld() == null) {return;}
        Block base = loc.getBlock();
        if (base.getType() == material) {base.setType(Material.AIR);}
        Block upper = base.getRelative(0, 1, 0);
        if (upper.getType() == material) {upper.setType(Material.AIR);}
    }

    private static void setIfFree(Block block, Material material)
    {
        if (block.getType() == material) {return;}
        if (block.getType().isAir() || block.isReplaceable()) {block.setType(material);}
    }
}
