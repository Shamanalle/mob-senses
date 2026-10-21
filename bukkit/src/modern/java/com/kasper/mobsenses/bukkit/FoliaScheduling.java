package com.kasper.mobsenses.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Folia's global region scheduler; this class is loaded only on Folia, which has no main thread. */
public final class FoliaScheduling implements Scheduling {

    public static Scheduling create() {
        return new FoliaScheduling();
    }

    @Override
    public void everyTick(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), 1L, 1L);
    }
}
