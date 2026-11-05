package com.kasper.mobsenses.bukkit;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/** Runs the plugin's repeating and region-bound work on Bukkit/Spigot/Paper or Folia. */
public interface Scheduling {

    void everyTick(Plugin plugin, Runnable task);

    void atLocation(Plugin plugin, Location location, Runnable task);
}
