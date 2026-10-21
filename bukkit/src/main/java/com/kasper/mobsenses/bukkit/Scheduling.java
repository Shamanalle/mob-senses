package com.kasper.mobsenses.bukkit;

import org.bukkit.plugin.Plugin;

/** Runs the plugin's repeating work: the Bukkit scheduler, or Folia's global region where that is all there is. */
public interface Scheduling {

    void everyTick(Plugin plugin, Runnable task);
}
