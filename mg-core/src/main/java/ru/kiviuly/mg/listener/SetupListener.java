package ru.kiviuly.mg.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.util.DebugLog;
import ru.kiviuly.mg.api.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Разметка точек предметом-маркером: ПКМ по блоку добавляет точку, Shift+ПКМ —
 * убирает. Ядро знает тип «spawn»; свои типы точек добавляй сюда по образцу.
 */
public class SetupListener implements Listener
{
    private final MgCorePlugin plugin;

    public SetupListener(MgCorePlugin plugin) {this.plugin = plugin;}

    @EventHandler
    public void onInteract(PlayerInteractEvent e)
    {
        if (e.getHand() != EquipmentSlot.HAND) {return;} // не дублировать по офф-хенду
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {return;}
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) {return;}
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String type = pdc.get(Keys.MARKER_TYPE, PersistentDataType.STRING);
        String arenaTag = pdc.get(Keys.MARKER_ARENA, PersistentDataType.STRING);
        if (type == null || arenaTag == null) {return;}

        e.setCancelled(true);
        Player p = e.getPlayer();
        Arena arena = plugin.arenas().get(arenaTag);
        if (arena == null) {Msg.send(p, "setup.bad-arena"); return;}
        if (arena.getSession() != null) {Msg.send(p, "setup.busy", Msg.ph("arena", arena.getId())); return;}

        Location loc = e.getClickedBlock().getLocation();
        if ("spawn".equals(type)) {spawnPoint(p, arena, loc);}
    }

    private void spawnPoint(Player p, Arena arena, Location loc)
    {
        if (p.isSneaking())
        {
            boolean removed = arena.getSpawns().removeIf(l -> sameBlock(l, loc));
            plugin.arenas().save(arena);
            Msg.send(p, removed ? "setup.spawn-removed" : "setup.not-a-point",
                Msg.ph("x", loc.getBlockX()), Msg.ph("y", loc.getBlockY()), Msg.ph("z", loc.getBlockZ()),
                Msg.ph("n", arena.getSpawns().size()));
            return;
        }
        if (arena.getSpawns().stream().noneMatch(l -> sameBlock(l, loc))) {arena.getSpawns().add(loc);}
        plugin.arenas().save(arena);
        DebugLog.log(Cat.ADMIN, "spawn-add arena=%s at=%s total=%d", arena.getId(), DebugLog.at(loc), arena.getSpawns().size());
        Msg.send(p, "setup.spawn-added",
            Msg.ph("x", loc.getBlockX()), Msg.ph("y", loc.getBlockY()), Msg.ph("z", loc.getBlockZ()),
            Msg.ph("n", arena.getSpawns().size()));
    }

    private static boolean sameBlock(Location a, Location b)
    {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld())
            && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}
