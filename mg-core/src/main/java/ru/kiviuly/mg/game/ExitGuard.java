package ru.kiviuly.mg.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.World;

import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.game.ExitGuardConfig;
import ru.kiviuly.mg.api.game.MatchPlayer;
import ru.kiviuly.mg.api.util.DebugLog;
import ru.kiviuly.mg.api.util.DebugLog.Cat;

/**
 * Встроенный «страж выхода» одного матча. Отключился живой участник — движок держит его в
 * матче на грейс-период и (по конфигу) ставит болванчика в его экипировке. Вернулся вовремя —
 * продолжает как ни в чём не бывало; болванчика убили или грейс истёк — заочная гибель через
 * {@link GameSession#eliminate(UUID)} (кредит убийце, лут падает). Всё ведёт движок — коду игры
 * достаточно вернуть {@link ExitGuardConfig} из {@code Minigame.exitGuard()}.
 */
public class ExitGuard
{
    private static final class Guard
    {
        UUID owner;
        String ownerName;
        final List<ItemStack> items = new ArrayList<>();
        UUID standInId;
        Location at;
        BukkitTask timer;
    }

    private final GameSession session;
    private final MgCorePlugin plugin;
    private final ExitGuardConfig cfg;
    private final Map<UUID, Guard> guards = new HashMap<>();

    ExitGuard(GameSession session, MgCorePlugin plugin, ExitGuardConfig cfg)
    {
        this.session = session;
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public boolean has(UUID id) {return guards.containsKey(id);}

    public boolean ownsEntity(UUID entityId) {return findByEntity(entityId) != null;}

    private Guard findByEntity(UUID entityId)
    {
        for (Guard g : guards.values()) {if (entityId.equals(g.standInId)) {return g;}}
        return null;
    }

    // ===== выход =====

    /** Игрок отключился: снять снимок инвентаря, поставить болванчика, запустить грейс. */
    void engage(Player p)
    {
        Guard g = new Guard();
        g.owner = p.getUniqueId();
        g.ownerName = p.getName();
        g.at = p.getLocation().clone();
        for (ItemStack item : p.getInventory().getContents())
        {
            if (item != null && !item.getType().isAir()) {g.items.add(item.clone());}
        }
        if (cfg.standIn() != null) {spawnStandIn(g, p);}

        int secs = Math.max(5, cfg.graceSeconds());
        g.timer = Bukkit.getScheduler().runTaskLater(plugin, () -> {g.timer = null; lose(g, null);}, secs * 20L);
        guards.put(g.owner, g);
        DebugLog.log(Cat.SESSION, "exit-guard engage player=%s grace=%ds standin=%s items=%d",
            g.ownerName, secs, cfg.standIn(), g.items.size());
    }

    // ===== возврат =====

    /** Игрок вернулся, пока страж активен: болванчик снят, игрок продолжает со своим инвентарём. */
    boolean resume(Player p)
    {
        Guard g = guards.remove(p.getUniqueId());
        if (g == null) {return false;}
        cancel(g);
        removeStandIn(g);
        DebugLog.log(Cat.SESSION, "exit-guard resume player=%s", p.getName());
        return true;
    }

    // ===== болванчик =====

    private void spawnStandIn(Guard g, Player p)
    {
        World world = g.at.getWorld();
        if (world == null) {return;}
        Entity e = world.spawnEntity(g.at, cfg.standIn());
        g.standInId = e.getUniqueId();
        session.trackEntity(e); // страховка: если страж не снимет — уберёт cleanup
        if (e instanceof LivingEntity le)
        {
            le.setAI(false);
            le.setPersistent(true);
            le.setRemoveWhenFarAway(false);
            le.setCanPickupItems(false);
            le.customName(Component.text(p.getName()));
            le.setCustomNameVisible(true);
            if (le instanceof Zombie z) {z.setShouldBurnInDay(false);}
            copyEquipment(le.getEquipment(), p.getInventory());
        }
        DebugLog.log(Cat.SESSION, "exit-guard standin-spawn player=%s type=%s", p.getName(), cfg.standIn());
    }

    private void copyEquipment(EntityEquipment eq, PlayerInventory inv)
    {
        if (eq == null) {return;}
        eq.setHelmet(clone(inv.getHelmet()));
        eq.setChestplate(clone(inv.getChestplate()));
        eq.setLeggings(clone(inv.getLeggings()));
        eq.setBoots(clone(inv.getBoots()));
        eq.setItemInMainHand(clone(inv.getItemInMainHand()));
        eq.setItemInOffHand(clone(inv.getItemInOffHand()));
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    private ItemStack clone(ItemStack item) {return item == null ? null : item.clone();}

    private void removeStandIn(Guard g)
    {
        if (g.standInId == null) {return;}
        Entity e = Bukkit.getEntity(g.standInId);
        g.standInId = null;
        if (e != null) {e.remove();}
    }

    /** Болванчик убит (из {@code GameListener}). {@code killer} != null — убит игроком. */
    public void onStandInDeath(UUID entityId, Player killer)
    {
        Guard g = findByEntity(entityId);
        if (g == null) {return;}
        g.standInId = null; // сущность уже мертва
        DebugLog.log(Cat.SESSION, "exit-guard standin-death player=%s killer=%s",
            g.ownerName, killer == null ? "-" : killer.getName());
        lose(g, killer);
    }

    // ===== заочная гибель / уборка =====

    /** Грейс истёк или болванчик убит: владелец выбывает заочно. */
    private void lose(Guard g, Player killer)
    {
        if (guards.remove(g.owner) == null) {return;} // уже обработан
        cancel(g);
        removeStandIn(g);
        if (cfg.dropItems()) {dropItems(g);}
        if (killer != null && cfg.killIsDeath())
        {
            MatchPlayer km = session.player(killer.getUniqueId());
            if (km != null && km.isAlive()) {km.addKill();}
        }
        DebugLog.log(Cat.SESSION, "exit-guard lose player=%s by=%s", g.ownerName, killer == null ? "timeout" : killer.getName());
        session.eliminate(g.owner); // ядро: alive=false + onPlayerEliminated + checkResult
    }

    private void dropItems(Guard g)
    {
        if (g.at == null || g.at.getWorld() == null) {return;}
        for (ItemStack item : g.items)
        {
            if (item == null || item.getType().isAir()) {continue;}
            Item drop = g.at.getWorld().dropItemNaturally(g.at.clone().add(0, 0.5, 0), item);
            session.trackEntity(drop); // подберёт cleanup, если останется на земле
        }
        g.items.clear();
    }

    /** Снять всех болванчиков и таймеры (конец матча). */
    public void clear()
    {
        for (Guard g : new ArrayList<>(guards.values()))
        {
            cancel(g);
            removeStandIn(g);
        }
        guards.clear();
    }

    private void cancel(Guard g)
    {
        if (g.timer != null) {g.timer.cancel(); g.timer = null;}
    }
}
