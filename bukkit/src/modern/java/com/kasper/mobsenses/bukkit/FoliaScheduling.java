package com.kasper.mobsenses.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Folia's global, region and entity schedulers; loaded only on Folia, which has no single main thread. */
public final class FoliaScheduling implements Scheduling {

    public static Scheduling create() {
        return new FoliaScheduling();
    }

    @Override
    public void everyTick(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), 1L, 1L);
    }

    @Override
    public void atLocation(Plugin plugin, Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
        } else {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        }
    }

    @Override
    public void onPlayer(Plugin plugin, Player player, Runnable task) {
        if (player == null) {
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
        } else {
            player.getScheduler().run(plugin, t -> {
                if (player.isOnline()) {
                    task.run();
                }
            }, null);
        }
    }

    @Override
    public void cancelAll(Plugin plugin) {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }

    @Override
    public boolean isRegionized() {
        return true;
    }

    @Override
    public boolean owns(Player player) {
        return player != null && Bukkit.isOwnedByCurrentRegion(player);
    }
}
