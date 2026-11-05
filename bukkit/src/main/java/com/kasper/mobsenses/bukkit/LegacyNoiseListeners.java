package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.NoiseModel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

/**
 * Fallback noise event listeners for Bukkit / Spigot / Paper 1.16.5 where {@code GenericGameEvent}
 * (added in Minecraft 1.17) does not exist.
 */
public final class LegacyNoiseListeners implements Listener {

    public static void register(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new LegacyNoiseListeners(), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        Location at = e.getLocation();
        if (at.getWorld() != null) {
            MobSenses.gameEvent("minecraft:explode", at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        Location at = block.getLocation();
        if (at.getWorld() != null) {
            AcousticMaterial mat = BukkitAcoustics.material(block);
            MobSenses.gameEvent("minecraft:block_destroy", at.getWorld().getName(),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    BukkitSenses.posture(e.getPlayer()), mat, BukkitAcoustics.blockId(block), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Block block = e.getBlockPlaced();
        Location at = block.getLocation();
        if (at.getWorld() != null) {
            AcousticMaterial mat = BukkitAcoustics.material(block);
            MobSenses.gameEvent("minecraft:block_place", at.getWorld().getName(),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    BukkitSenses.posture(e.getPlayer()), mat, BukkitAcoustics.blockId(block), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Block block = e.getClickedBlock();
        Location at = block.getLocation();
        if (at.getWorld() == null) {
            return;
        }
        Material type = block.getType();
        if (type == Material.BELL) {
            MobSenses.gameEvent("minecraft:bell_ring", at.getWorld().getName(),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        } else if (type == Material.NOTE_BLOCK || type == Material.JUKEBOX) {
            MobSenses.gameEvent("minecraft:note_block_play", at.getWorld().getName(),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        } else if (block.getBlockData() instanceof Openable) {
            AcousticMaterial mat = BukkitAcoustics.material(block);
            MobSenses.gameEvent("minecraft:block_open", at.getWorld().getName(),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    BukkitSenses.posture(e.getPlayer()), mat, BukkitAcoustics.blockId(block), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerOpen(InventoryOpenEvent e) {
        Location at = e.getInventory().getLocation();
        if (at != null && at.getWorld() != null) {
            MobSenses.gameEvent("minecraft:container_open", at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        boolean playerInvolved = damager instanceof Player || e.getEntity() instanceof Player
                || (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player);
        if (!playerInvolved) {
            return;
        }
        Location at = e.getEntity().getLocation();
        if (at.getWorld() != null) {
            MobSenses.gameEvent("minecraft:entity_damage", at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player)) {
            return;
        }
        Location at = e.getEntity().getLocation();
        if (at.getWorld() != null) {
            MobSenses.gameEvent("minecraft:projectile_land", at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        Location at = p.getLocation();
        if (at.getWorld() != null) {
            MobSenses.gameEvent("minecraft:eat", at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ(),
                    BukkitSenses.posture(p), null, null, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx * dx + dz * dz < 0.04) {
            return;
        }
        Player p = e.getPlayer();
        if (!p.isOnGround() || (p.getTicksLived() & 3) != 0) {
            return;
        }
        Block surface = to.clone().subtract(0.0, 0.2, 0.0).getBlock();
        AcousticMaterial mat = BukkitAcoustics.material(surface);
        NoiseModel.Posture posture = BukkitSenses.posture(p);
        MobSenses.gameEvent("minecraft:step", to.getWorld().getName(),
                to.getX(), to.getY(), to.getZ(),
                posture, mat, BukkitAcoustics.blockId(surface), true);
    }
}
