package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.plugin.Plugin;

/**
 * Game events (the ones sculk sensors hear) on Bukkit 1.17+. Loaded only where {@code GenericGameEvent}
 * exists. On Folia it fires on the thread of the event's region, which the shared code allows.
 */
public final class GameEventListener implements Listener {

    public static void register(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new GameEventListener(), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameEvent(GenericGameEvent e) {
        try {
            Location at = e.getLocation();
            if (at.getWorld() == null) {
                return;
            }
            MobSenses.gameEvent(e.getEvent().getKey().toString(), at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
        } catch (Throwable t) {
            ModLog.failure("Reading a game event", t);
        }
    }
}
