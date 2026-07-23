package ru.kiviuly.mg.menu;
import ru.kiviuly.mg.api.menu.Menu;
import ru.kiviuly.mg.api.menu.AnvilInputMenu;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Хаб настройки арены (/mg gui &lt;ID&gt;): из него доступны параметры, точки, имя/
 * описание (наковальня) и действия. Действия с чат-выводом (check/enable/disable/
 * start/stop) переиспользуют команды через performCommand — без дублирования логики.
 */
public class ArenaHubMenu extends Menu
{
    private static final int SLOT_STATUS = 4;
    private static final int SLOT_SETTINGS = 10;
    private static final int SLOT_POINTS = 12;
    private static final int SLOT_NAME = 14;
    private static final int SLOT_DESC = 16;
    private static final int SLOT_CHECK = 28;
    private static final int SLOT_TOGGLE = 30;
    private static final int SLOT_START = 32;
    private static final int SLOT_STOP = 34;
    private static final int SLOT_CLOSE = 49;

    private final MgCorePlugin plugin;
    private final Arena arena;

    public ArenaHubMenu(MgCorePlugin plugin, Arena arena)
    {
        super(54, Msg.get("hub.title").append(Component.text(arena.getId())));
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
        inventory.setItem(SLOT_STATUS, status());
        inventory.setItem(SLOT_SETTINGS, btn(Material.COMPARATOR, "settings"));
        inventory.setItem(SLOT_POINTS, btn(Material.BEACON, "points"));
        inventory.setItem(SLOT_NAME, btn(Material.NAME_TAG, "name"));
        inventory.setItem(SLOT_DESC, btn(Material.OAK_SIGN, "desc"));
        inventory.setItem(SLOT_CHECK, btn(Material.SPYGLASS, "check"));
        inventory.setItem(SLOT_TOGGLE, arena.isEnabled()
            ? btn(Material.GRAY_DYE, "disable") : btn(Material.LIME_DYE, "enable"));
        inventory.setItem(SLOT_START, btn(Material.LIME_CONCRETE, "start"));
        inventory.setItem(SLOT_STOP, btn(Material.RED_CONCRETE, "stop"));
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_CLOSE, Items.named(Material.BARRIER, Msg.get("hub.close-name")));
    }

    private ItemStack btn(Material mat, String key)
    {
        return Items.named(mat, Msg.get("hub.btn-" + key + "-name"), Msg.getList("hub.btn-" + key + "-lore"));
    }

    private ItemStack status()
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("hub.status-id", Msg.ph("id", arena.getId()))));
        lore.add(Items.flat(Msg.get("hub.status-world",
            Msg.ph("world", arena.getWorldName() == null ? Msg.raw("hub.status-none") : arena.getWorldName()))));
        lore.add(Items.flat(Msg.get("hub.status-enabled",
            Msg.ph("value", Msg.raw(arena.isEnabled() ? "hub.status-yes" : "hub.status-no")))));
        lore.add(Items.flat(Msg.get("hub.status-players",
            Msg.ph("min", arena.getMinPlayers()), Msg.ph("max", arena.getMaxPlayers()))));
        lore.add(Items.flat(Msg.get("hub.status-spawns", Msg.ph("n", arena.getSpawns().size()))));
        lore.add(Items.flat(Msg.get("hub.status-session",
            Msg.ph("value", Msg.raw(arena.getSession() != null ? "hub.status-running" : "hub.status-idle")))));
        return Items.named(Material.WRITTEN_BOOK, Items.flat(Msg.mm(arena.getDisplayNameRaw())), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        switch (e.getRawSlot())
        {
            case SLOT_SETTINGS -> new ArenaSettingsMenu(plugin, arena).open(p);
            case SLOT_POINTS -> new ArenaPointsMenu(plugin, arena).open(p);
            case SLOT_NAME -> editName(p);
            case SLOT_DESC -> editDesc(p);
            case SLOT_CHECK -> runCommand(p, "check");
            case SLOT_TOGGLE -> runCommand(p, arena.isEnabled() ? "disable" : "enable");
            case SLOT_START -> runCommand(p, "start");
            case SLOT_STOP -> runCommand(p, "stop");
            case SLOT_CLOSE -> p.closeInventory();
            default -> {}
        }
    }

    private void runCommand(Player p, String sub)
    {
        p.closeInventory();
        p.performCommand("sbw " + sub + " " + arena.getId());
    }

    private void editName(Player p)
    {
        new AnvilInputMenu(plugin, Msg.get("hub.name-anvil-title"), arena.getDisplayNameRaw(), text ->
        {
            arena.setDisplayNameRaw(text);
            plugin.arenas().save(arena);
            Msg.send(p, "admin.name-set", Msg.ph("arena", arena.getId()), Msg.phMm("name", text));
            new ArenaHubMenu(plugin, arena).open(p);
        }).open(p);
    }

    private void editDesc(Player p)
    {
        new AnvilInputMenu(plugin, Msg.get("hub.desc-anvil-title"), arena.getDescriptionRaw(), text ->
        {
            arena.setDescriptionRaw(text);
            plugin.arenas().save(arena);
            Msg.send(p, "admin.desc-set", Msg.ph("arena", arena.getId()), Msg.phMm("description", text));
            new ArenaHubMenu(plugin, arena).open(p);
        }).open(p);
    }
}
