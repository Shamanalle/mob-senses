package com.kasper.mobsenses.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Listens for thrown-item decoys ({@code Q}) and unseen/ambush attacks against listener mobs on
 * Bukkit, Spigot, Paper, Purpur, and Folia.
 */
public final class TacticalBukkitListener implements Listener {

    private TacticalBukkitListener() {
    }

    public static void register(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new TacticalBukkitListener(), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        BukkitSenses.onPlayerDropDecoy(event.getPlayer(), event.getItemDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mob)) {
            return;
        }
        Mob mob = (Mob) event.getEntity();
        Entity damager = event.getDamager();
        Player attacker = null;
        if (damager instanceof Player) {
            attacker = (Player) damager;
        } else if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                attacker = (Player) shooter;
            }
        }
        if (attacker == null || attacker.equals(mob)) {
            return;
        }
        BukkitSenses.onMobDamagedByPlayer(mob, attacker, damager);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob) || event.getTarget() == null) {
            return;
        }
        Mob mob = (Mob) event.getEntity();
        if (BukkitSenses.shouldSuppressTarget(mob, event.getTarget(), event.getReason())) {
            event.setCancelled(true);
        }
    }
}
