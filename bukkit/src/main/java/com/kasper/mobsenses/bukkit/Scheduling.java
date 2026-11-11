package com.kasper.mobsenses.bukkit;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Runs the plugin's repeating, region-bound, and entity-bound work on Bukkit/Spigot/Paper or Folia. */
public interface Scheduling {

    void everyTick(Plugin plugin, Runnable task);

    void atLocation(Plugin plugin, Location location, Runnable task);

    void onPlayer(Plugin plugin, Player player, Runnable task);

    void cancelAll(Plugin plugin);

    default boolean isRegionized() {
        return false;
    }

    boolean owns(Player player);
}
