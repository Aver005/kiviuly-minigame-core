package ru.kiviuly.mg.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * ± редактор общих параметров арены (мин/макс игроков, отсчёты, длительность).
 * ЛКМ +1 (Shift +10), ПКМ -1 (Shift -10). Пишется сразу. Числовые настройки самой
 * ИГРЫ храните в arena.settings (getSetting/setSetting) — добавьте их сюда по образцу.
 */
public class ArenaSettingsMenu extends Menu
{
    private static final int SLOT_BACK_HUB = 49;

    private final MgCorePlugin plugin;
    private final Arena arena;
    private final List<Setting> settings;
    private final Map<Integer, Setting> bySlot = new HashMap<>();

    private record Setting(int slot, String key, Material icon, int min, int max, IntSupplier get, IntConsumer set) {}

    public ArenaSettingsMenu(MgCorePlugin plugin, Arena arena)
    {
        super(54, Msg.get("settings.title").append(Component.text(arena.getId())));
        this.plugin = plugin;
        this.arena = arena;
        this.settings = build(arena);
        for (Setting s : settings) {bySlot.put(s.slot(), s);}
    }

    private static List<Setting> build(Arena a)
    {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting(0, "minplayers", Material.IRON_HELMET, 1, 128, a::getMinPlayers, a::setMinPlayers));
        s.add(new Setting(1, "maxplayers", Material.DIAMOND_HELMET, 1, 128, a::getMaxPlayers, a::setMaxPlayers));
        s.add(new Setting(2, "lobbycountdown", Material.CLOCK, 0, 3600, a::getLobbyCountdownSeconds, a::setLobbyCountdownSeconds));
        s.add(new Setting(3, "countdownfull", Material.REPEATER, 0, 3600, a::getCountdownFullSeconds, a::setCountdownFullSeconds));
        s.add(new Setting(4, "duration", Material.COMPASS, 0, 100000, a::getMatchDurationSeconds, a::setMatchDurationSeconds));
        return s;
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
        for (Setting s : settings) {inventory.setItem(s.slot(), button(s));}
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_BACK_HUB, Items.named(Material.OAK_DOOR, Msg.get("menu.back")));
    }

    private ItemStack button(Setting s)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("settings.value", Msg.ph("value", s.get().getAsInt())));
        if (s.min() == 0) {lore.add(Msg.get("settings.note-zero"));}
        lore.addAll(Msg.getList("settings.adjust"));
        return Items.named(s.icon(), Msg.get("settings.name-" + s.key()), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw == SLOT_BACK_HUB) {new ArenaHubMenu(plugin, arena).open(p); return;}
        Setting s = bySlot.get(raw);
        if (s == null) {return;}
        int cur = s.get().getAsInt();
        int delta = (e.isLeftClick() ? 1 : -1) * (e.isShiftClick() ? 10 : 1);
        int next = Math.max(s.min(), Math.min(s.max(), cur + delta));
        if (next == cur) {return;}
        s.set().accept(next);
        plugin.arenas().save(arena);
        inventory.setItem(s.slot(), button(s));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, next >= cur ? 1.3f : 0.8f);
    }
}
