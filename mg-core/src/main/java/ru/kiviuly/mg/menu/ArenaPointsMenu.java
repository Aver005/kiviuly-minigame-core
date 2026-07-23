package ru.kiviuly.mg.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.arena.SetupMarkers;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Точки арены: выдать маркер спавна (ПКМ по блоку = точка), установить лобби в
 * текущую позицию, расставить блоки-подсказки. Счётчик спавнов виден на кнопке.
 */
public class ArenaPointsMenu extends Menu
{
    private static final int SLOT_SPAWN = 11;
    private static final int SLOT_LOBBY = 13;
    private static final int SLOT_MARKERS = 15;
    private static final int SLOT_BACK_HUB = 49;

    private final MgCorePlugin plugin;
    private final Arena arena;

    public ArenaPointsMenu(MgCorePlugin plugin, Arena arena)
    {
        super(54, Msg.get("points.title").append(Component.text(arena.getId())));
        this.plugin = plugin;
        this.arena = arena;
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private void render()
    {
        inventory.clear();
        List<Component> spawnLore = new ArrayList<>();
        spawnLore.add(Msg.get("points.count", Msg.ph("n", arena.getSpawns().size())));
        spawnLore.addAll(Msg.getList("points.spawn-lore"));
        inventory.setItem(SLOT_SPAWN, Items.named(Material.BEACON, Msg.get("points.spawn-name"), spawnLore));

        List<Component> lobbyLore = new ArrayList<>();
        lobbyLore.add(Msg.get("points.lobby-current",
            Msg.ph("value", Msg.raw(arena.getLobby() != null ? "points.lobby-yes" : "points.lobby-no"))));
        lobbyLore.addAll(Msg.getList("points.lobby-lore"));
        inventory.setItem(SLOT_LOBBY, Items.named(Material.RED_BED, Msg.get("points.lobby-name"), lobbyLore));

        inventory.setItem(SLOT_MARKERS, Items.named(Material.ARMOR_STAND,
            Msg.get("points.markers-name"), Msg.getList("points.markers-lore")));

        fillAll(Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_BACK_HUB, Items.named(Material.OAK_DOOR, Msg.get("menu.back")));
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        switch (e.getRawSlot())
        {
            case SLOT_SPAWN ->
            {
                p.getInventory().addItem(SetupMarkers.markerItem(arena, "spawn", Material.BEACON, null));
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
                Msg.send(p, "points.spawn-given");
            }
            case SLOT_LOBBY ->
            {
                arena.setLobby(p.getLocation().toBlockLocation().add(0.5, 0, 0.5));
                arena.setWorldName(p.getWorld().getName());
                plugin.arenas().save(arena);
                Msg.send(p, "points.lobby-set", Msg.ph("arena", arena.getId()));
                render();
            }
            case SLOT_MARKERS ->
            {
                if (arena.getWorld() == null) {Msg.send(p, "errors.world-not-loaded"); return;}
                if (arena.getSession() != null) {Msg.send(p, "points.markers-busy", Msg.ph("arena", arena.getId())); return;}
                int n = SetupMarkers.placeAll(arena);
                Msg.send(p, "points.markers-placed", Msg.ph("arena", arena.getId()), Msg.ph("n", n));
            }
            case SLOT_BACK_HUB -> new ArenaHubMenu(plugin, arena).open(p);
            default -> {}
        }
    }
}
