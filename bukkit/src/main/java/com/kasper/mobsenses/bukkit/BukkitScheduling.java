package com.kasper.mobsenses.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** The main thread's scheduler of Bukkit, Spigot and Paper. */
final class BukkitScheduling implements Scheduling {

    @Override
    public void everyTick(Plugin plugin, Runnable task) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, 1L);
    }
}
