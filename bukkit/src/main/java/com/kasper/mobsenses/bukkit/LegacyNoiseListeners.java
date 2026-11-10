package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.NoiseModel;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
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

    private static boolean isSpectator(Player player) {
        return player != null && player.getGameMode() == GameMode.SPECTATOR;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        Location at = e.getLocation();
        if (at.getWorld() != null) {
            double env = BukkitSenses.envMultiplier(at.getWorld(), null, NoiseModel.Kind.EXPLOSION);
            MobSenses.gameEvent("minecraft:explode", at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ(),
                    NoiseModel.Posture.WALKING, null, null, true, env);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (isSpectator(p)) {
            return;
        }
        Block block = e.getBlock();
        Location at = block.getLocation();
        if (at.getWorld() != null) {
            AcousticMaterial mat = BukkitAcoustics.material(block);
            double env = BukkitSenses.envMultiplier(at.getWorld(), p, NoiseModel.Kind.BLOCK_BREAK);
            MobSenses.gameEvent("minecraft:block_destroy", at.getWorld().getName(),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    BukkitSenses.posture(p), mat, BukkitAcoustics.blockId(block), true, env);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (isSpectator(p)) {
            return;
        }
        Block block = e.getBlockPlaced();
        Location at = block.getLocation();
        if (at.getWorld() != null) {
            AcousticMaterial mat = BukkitAcoustics.material(block);
            double env = BukkitSenses.envMultiplier(at.getWorld(), p, NoiseModel.Kind.BLOCK_PLACE);
            MobSenses.gameEvent("minecraft:block_place", at.getWorld().getName(),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    BukkitSenses.posture(p), mat, BukkitAcoustics.blockId(block), true, env);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (isSpectator(p) || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Block block = e.getClickedBlock();
        Location at = block.getLocation();
        if (at.getWorld() == null) {
            return;
        }
        Material type = block.getType();
        if (type == Material.BELL) {
            double env = BukkitSenses.envMultiplier(at.getWorld(), p, NoiseModel.Kind.BELL);
            MobSenses.gameEvent("minecraft:bell_ring", at.getWorld().getName(),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    BukkitSenses.posture(p), null, BukkitAcoustics.blockId(block), true, env);
        } else if (type == Material.NOTE_BLOCK || type == Material.JUKEBOX) {
            double env = BukkitSenses.envMultiplier(at.getWorld(), p, NoiseModel.Kind.NOTE_BLOCK);
            MobSenses.gameEvent("minecraft:note_block_play", at.getWorld().getName(),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    BukkitSenses.posture(p), null, BukkitAcoustics.blockId(block), true, env);
        } else if (block.getBlockData() instanceof Openable) {
            AcousticMaterial mat = BukkitAcoustics.material(block);
            double env = BukkitSenses.envMultiplier(at.getWorld(), p, NoiseModel.Kind.DOOR);
            MobSenses.gameEvent("minecraft:block_open", at.getWorld().getName(),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    BukkitSenses.posture(p), mat, BukkitAcoustics.blockId(block), true, env);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerOpen(InventoryOpenEvent e) {
        HumanEntity human = e.getPlayer();
        Player p = human instanceof Player ? (Player) human : null;
        if (isSpectator(p)) {
            return;
        }
        Location at = e.getInventory().getLocation();
        if (at != null && at.getWorld() != null) {
            double env = BukkitSenses.envMultiplier(at.getWorld(), p, NoiseModel.Kind.CONTAINER);
            MobSenses.gameEvent("minecraft:container_open", at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ(),
                    BukkitSenses.posture(p), null, null, true, env);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        Player p = null;
        if (damager instanceof Player) {
            p = (Player) damager;
        } else if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
            p = (Player) ((Projectile) damager).getShooter();
        } else if (e.getEntity() instanceof Player) {
            p = (Player) e.getEntity();
        }
        if (p == null || isSpectator(p)) {
            return;
        }
        Location at = e.getEntity().getLocation();
        if (at.getWorld() != null) {
            double env = BukkitSenses.envMultiplier(at.getWorld(), p, NoiseModel.Kind.COMBAT);
            MobSenses.gameEvent("minecraft:entity_damage", at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ(),
                    BukkitSenses.posture(p), null, null, true, env);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getEntity().getShooter();
        if (isSpectator(p)) {
            return;
        }
        Location at = e.getEntity().getLocation();
        if (at.getWorld() != null) {
            double env = BukkitSenses.envMultiplier(at.getWorld(), p, NoiseModel.Kind.PROJECTILE);
            MobSenses.gameEvent("minecraft:projectile_land", at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ(),
                    BukkitSenses.posture(p), null, null, true, env);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (isSpectator(p)) {
            return;
        }
        Location at = p.getLocation();
        if (at.getWorld() != null) {
            double env = BukkitSenses.envMultiplier(at.getWorld(), p, NoiseModel.Kind.EAT);
            MobSenses.gameEvent("minecraft:eat", at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ(),
                    BukkitSenses.posture(p), null, null, true, env);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (isSpectator(p)) {
            return;
        }
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
        if (!p.isOnGround() || (p.getTicksLived() & 3) != 0) {
            return;
        }
        Block surface = to.clone().subtract(0.0, 0.2, 0.0).getBlock();
        AcousticMaterial mat = BukkitAcoustics.material(surface);
        NoiseModel.Posture posture = BukkitSenses.posture(p);
        double envMult = BukkitSenses.envMultiplier(to.getWorld(), p, NoiseModel.Kind.STEP);
        MobSenses.gameEvent("minecraft:step", to.getWorld().getName(),
                to.getX(), to.getY(), to.getZ(),
                posture, mat, BukkitAcoustics.blockId(surface), true, envMult);
    }
}
