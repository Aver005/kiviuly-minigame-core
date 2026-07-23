package ru.kiviuly.mg.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.game.Match;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/** Список арен для игрока: ЛКМ — войти. Иконка = статус (idle/идёт/выключена). */
public class ArenaSelectMenu extends Menu
{
    private final MgCorePlugin plugin;
    private final Map<Integer, String> idBySlot = new HashMap<>();
    private int page;

    public ArenaSelectMenu(MgCorePlugin plugin)
    {
        super(54, Msg.get("select.title"));
        this.plugin = plugin;
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
        idBySlot.clear();
        List<Arena> arenas = new ArrayList<>(plugin.arenas().all());
        int pages = pageCount(arenas.size());
        if (page >= pages) {page = pages - 1;}
        if (page < 0) {page = 0;}
        for (int i = 0; i < PAGE_SIZE; i++)
        {
            int index = page * PAGE_SIZE + i;
            if (index >= arenas.size()) {break;}
            Arena a = arenas.get(index);
            inventory.setItem(i, icon(a));
            idBySlot.put(i, a.getId());
        }
        renderControls(page, pages, false, Material.BLACK_STAINED_GLASS_PANE);
        if (arenas.isEmpty())
        {
            inventory.setItem(22, Items.named(Material.BARRIER,
                Msg.get("select.empty-name"), Msg.getList("select.empty-lore")));
        }
    }

    private ItemStack icon(Arena a)
    {
        Match s = a.getSession();
        Material mat;
        String statusKey;
        if (!a.isEnabled()) {mat = Material.GRAY_WOOL; statusKey = "select.status-disabled";}
        else if (s == null) {mat = Material.LIME_WOOL; statusKey = "select.status-idle";}
        else {mat = Material.YELLOW_WOOL; statusKey = "select.status-running";}

        int current = s == null ? 0 : s.players().size();
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("select.lore-status", Msg.phC("status", Msg.get(statusKey)))));
        lore.add(Items.flat(Msg.get("select.lore-players", Msg.ph("current", current), Msg.ph("max", a.getMaxPlayers()))));
        lore.add(Items.flat(Msg.get("select.lore-join")));
        return Items.named(mat, Items.flat(Msg.mm(a.getDisplayNameRaw())), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        int pages = pageCount(plugin.arenas().all().size());
        if (raw == SLOT_PREV && page > 0) {page--; render(); return;}
        if (raw == SLOT_NEXT && page < pages - 1) {page++; render(); return;}
        if (raw < 0 || raw >= PAGE_SIZE) {return;}
        String id = idBySlot.get(raw);
        if (id == null) {return;}
        Arena arena = plugin.arenas().get(id);
        if (arena == null) {render(); return;}
        p.closeInventory();
        plugin.arenas().join(p, arena);
    }
}
