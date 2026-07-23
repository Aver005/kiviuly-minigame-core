package ru.kiviuly.mg.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.weather.WeatherChangeEvent;
import ru.kiviuly.mg.MgCorePlugin;
import ru.kiviuly.mg.api.arena.Arena;

/**
 * Держит миры арен чистыми вне матчей: гасит естественный спавн мобов, ползучий
 * огонь, гниение листвы и дождь. Применяется только к мирам, назначенным аренам
 * (не трогает остальные миры сервера). Игро-специфику решает сам {@link ru.kiviuly.mg.api.game.Minigame}.
 */
public class ProtectionListener implements Listener
{
    private final MgCorePlugin plugin;

    public ProtectionListener(MgCorePlugin plugin) {this.plugin = plugin;}

    private boolean isArenaWorld(World world)
    {
        if (world == null) {return false;}
        for (Arena a : plugin.arenas().all())
        {
            if (world.getName().equals(a.getWorldName())) {return true;}
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e)
    {
        SpawnReason reason = e.getSpawnReason();
        if (reason == SpawnReason.CUSTOM || reason == SpawnReason.SPAWNER_EGG || reason == SpawnReason.COMMAND) {return;}
        if (isArenaWorld(e.getLocation().getWorld())) {e.setCancelled(true);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent e)
    {
        if (isArenaWorld(e.getBlock().getWorld())) {e.setCancelled(true);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e)
    {
        if (isArenaWorld(e.getBlock().getWorld())) {e.setCancelled(true);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent e)
    {
        if (isArenaWorld(e.getBlock().getWorld())) {e.setCancelled(true);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e)
    {
        if (e.toWeatherState() && isArenaWorld(e.getWorld())) {e.setCancelled(true);} // не начинать дождь
    }
}
