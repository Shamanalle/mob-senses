package com.kasper.mobsenses.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** The main thread's scheduler of Bukkit, Spigot and Paper. */
final class BukkitScheduling implements Scheduling {

    @Override
    public void everyTick(Plugin plugin, Runnable task) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, 1L);
    }

    @Override
    public void atLocation(Plugin plugin, Location location, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void onPlayer(Plugin plugin, Player player, Runnable task) {
        if (player == null) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    task.run();
                }
            });
        }
    }

    @Override
    public void cancelAll(Plugin plugin) {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    @Override
    public boolean owns(Player player) {
        return Bukkit.isPrimaryThread();
    }
}
