package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.NoiseModel;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Game events (the ones sculk sensors hear) on Bukkit 1.17+. Loaded only where {@code GenericGameEvent}
 * exists. On Folia it fires on the thread of the event's region, which the shared code allows.
 */
public final class GameEventListener implements Listener {

    public static void register(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new GameEventListener(), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameEvent(GenericGameEvent e) {
        try {
            Location at = e.getLocation();
            if (at.getWorld() == null) {
                return;
            }
            String eventId = ((Keyed) e.getEvent()).getKey().toString();
            NoiseModel.Kind kind = NoiseModel.classifyEvent(eventId);
            if (kind == null) {
                return;
            }
            Entity source = e.getEntity();
            Player localPlayer = null;
            UUID playerId = null;
            if (source instanceof Player) {
                Player p = (Player) source;
                playerId = p.getUniqueId();
                if (BukkitSenses.ownsPlayer(p)) {
                    if (BukkitSenses.isBypassed(p)) {
                        return;
                    }
                    localPlayer = p;
                }
            } else if (source instanceof Projectile && ((Projectile) source).getShooter() instanceof Player) {
                Player p = (Player) ((Projectile) source).getShooter();
                playerId = p.getUniqueId();
                if (BukkitSenses.ownsPlayer(p)) {
                    if (BukkitSenses.isBypassed(p)) {
                        return;
                    }
                    localPlayer = p;
                }
            }
            boolean playerInvolved = playerId != null || !kind.requiresPlayerSource();
            if (!playerInvolved) {
                return;
            }
            NoiseModel.Posture posture = localPlayer != null ? BukkitSenses.posture(localPlayer) : NoiseModel.Posture.WALKING;
            Block block = at.getBlock();
            if (block.getType().isAir()) {
                block = at.clone().subtract(0.0, 0.2, 0.0).getBlock();
                if (block.getType().isAir()) {
                    block = at.getBlock().getRelative(0, -1, 0);
                }
            }
            AcousticMaterial mat = BukkitAcoustics.material(block);
            String blockId = BukkitAcoustics.blockId(block);
            double envMult = BukkitSenses.envMultiplier(at.getWorld(), localPlayer, kind);
            MobSenses.gameEvent(eventId, at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ(),
                    posture, mat, blockId, playerId != null, envMult,
                    playerId);
        } catch (Throwable t) {
            ModLog.failure("Reading a game event", t);
        }
    }
}
