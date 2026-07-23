package ru.kiviuly.mg.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.game.GamePhase;
import ru.kiviuly.mg.game.GameSession;
import ru.kiviuly.mg.api.game.MatchPlayer;
import ru.kiviuly.mg.player.PlayerSnapshot;
import ru.kiviuly.mg.api.util.Items;

/**
 * Игровые события матча: смерть → спектатор (fake death), защита лобби, откат
 * поставленных/сломанных блоков, вход/выход игрока, «выход»-предмет лобби.
 * Мир арены за пределами сессии защищает {@link ProtectionListener}.
 */
public class GameListener implements Listener
{
    private final MgCorePlugin plugin;

    public GameListener(MgCorePlugin plugin) {this.plugin = plugin;}

    /** Смертельный урон → выбывание в спектаторы (без ванильной смерти/респауна). */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e)
    {
        if (!(e.getEntity() instanceof Player p)) {return;}
        GameSession s = plugin.arenas().sessionOf(p);
        if (s == null) {return;}
        if (s.phase() != GamePhase.RUNNING)
        {
            // Вне матча реального урона нет. PvP-удар между участниками сессии отдаём
            // игре (обобщённый хук — напр. SkyWars считает им «разминку»).
            if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player damager
                && plugin.arenas().sessionOf(damager) == s)
            {
                s.game().onLobbyAttack(s, p, damager, by.getDamage());
            }
            if (!s.game().allowLobbyPvp()) {e.setCancelled(true);}
            return;
        }

        MatchPlayer mp = s.player(p.getUniqueId());
        if (mp == null || !mp.isAlive()) {e.setCancelled(true); return;} // спектаторы неуязвимы

        if (p.getHealth() - e.getFinalDamage() > 0) {return;} // не смертельно — обычный урон
        e.setCancelled(true);
        // Летальный урон: игра решает — возродить (false) или выбить в спектаторы (true).
        if (s.game().onLethalDamage(s, p))
        {
            creditKiller(s, e);
            s.eliminate(p, true);
        }
    }

    private void creditKiller(GameSession s, EntityDamageEvent e)
    {
        if (!(e instanceof EntityDamageByEntityEvent by)) {return;}
        if (!(by.getDamager() instanceof Player killer)) {return;}
        MatchPlayer killerMp = s.player(killer.getUniqueId());
        if (killerMp != null && killerMp.isAlive()) {killerMp.addKill();}
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e)
    {
        if (!(e.getEntity() instanceof Player p)) {return;}
        GameSession s = plugin.arenas().sessionOf(p);
        if (s != null && s.phase() != GamePhase.RUNNING) {e.setCancelled(true);}
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e)
    {
        Player p = e.getPlayer();
        if (Items.isSpecial(e.getItem(), "leave"))
        {
            e.setCancelled(true);
            plugin.arenas().leave(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e)
    {
        GameSession s = plugin.arenas().sessionOf(e.getPlayer());
        if (s != null && s.phase() != GamePhase.RUNNING) {e.setCancelled(true);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e)
    {
        GameSession s = plugin.arenas().sessionOf(e.getPlayer());
        if (s == null) {return;}
        MatchPlayer mp = s.player(e.getPlayer().getUniqueId());
        if (s.phase() == GamePhase.RUNNING && mp != null && mp.isAlive())
        {
            s.rememberBlock(e.getBlock()); // откат после матча
        }
        else
        {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e)
    {
        GameSession s = plugin.arenas().sessionOf(e.getPlayer());
        if (s == null) {return;}
        MatchPlayer mp = s.player(e.getPlayer().getUniqueId());
        if (s.phase() == GamePhase.RUNNING && mp != null && mp.isAlive())
        {
            s.rememberState(e.getBlockReplacedState()); // блок уже стоит — берём прежнее
        }
        else
        {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e)
    {
        GameSession s = plugin.arenas().sessionOf(e.getPlayer());
        if (s != null) {s.removePlayer(e.getPlayer(), false);}
    }

    /** Восстановление зависшего снапшота (краш/нечистая остановка во время матча). */
    @EventHandler
    public void onJoin(PlayerJoinEvent e)
    {
        Player p = e.getPlayer();
        if (!plugin.arenas().inGame(p) && PlayerSnapshot.exists(plugin, p.getUniqueId()))
        {
            PlayerSnapshot.restore(plugin, p);
        }
    }
}
