package com.kasper.mobsenses.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
}
