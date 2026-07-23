package ru.kiviuly.mg.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Универсальный ввод текста через наковальню: игрок печатает в поле переименования,
 * клик по слоту-результату применяет текст ({@code onConfirm}). Начальное значение —
 * имя предмета в первом слоте (для MiniMessage-полей показывается сырой текст).
 * Клики по предметам запрещены, XP не берётся. API сверен с Paper 26.1.2 (AnvilView).
 */
public class AnvilInputMenu extends Menu
{
    private static final int RESULT_SLOT = 2;

    private final MgCorePlugin plugin;
    private final String initial;
    private final Consumer<String> onConfirm;

    public AnvilInputMenu(MgCorePlugin plugin, Component title, String initial, Consumer<String> onConfirm)
    {
        super(InventoryType.ANVIL, title);
        this.plugin = plugin;
        this.initial = initial == null ? "" : initial;
        this.onConfirm = onConfirm;
        inventory.setItem(0, Items.named(Material.PAPER, Component.text(this.initial.isEmpty() ? " " : this.initial)));
    }

    @Override
    public boolean allowsInteraction() {return false;}

    @Override
    public void onPrepareAnvil(PrepareAnvilEvent e)
    {
        AnvilView view = e.getView();
        String text = view.getRenameText();
        String shown = text == null || text.isBlank() ? initial : text;
        e.setResult(Items.named(Material.PAPER, Component.text(shown.isEmpty() ? " " : shown)));
        view.setRepairCost(0);
        view.setMaximumRepairCost(0);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (e.getRawSlot() != RESULT_SLOT) {return;}
        if (!(e.getView() instanceof AnvilView view)) {return;}
        String text = view.getRenameText();
        if (text == null || text.isBlank()) {Msg.send(p, "anvil.empty"); return;}
        String value = text.trim();
        p.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> onConfirm.accept(value));
    }
}
