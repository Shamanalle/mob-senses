package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.NoiseModel;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
            String eventId = e.getEvent().getKey().toString();
            NoiseModel.Kind kind = NoiseModel.classifyEvent(eventId);
            if (kind == null) {
                return;
            }
            Entity source = e.getEntity();
            Player player = null;
            if (source instanceof Player) {
                Player p = (Player) source;
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }
                player = p;
            } else if (source instanceof Projectile && ((Projectile) source).getShooter() instanceof Player) {
                Player p = (Player) ((Projectile) source).getShooter();
                if (p.getGameMode() != GameMode.SPECTATOR) {
                    player = p;
                }
            }
            boolean playerInvolved = player != null || !kind.requiresPlayerSource();
            if (!playerInvolved) {
                return;
            }
            NoiseModel.Posture posture = BukkitSenses.posture(player);
            Block block = at.getBlock();
            if (block.getType().isAir()) {
                block = at.clone().subtract(0.0, 0.2, 0.0).getBlock();
            }
            AcousticMaterial mat = BukkitAcoustics.material(block);
            String blockId = BukkitAcoustics.blockId(block);
            MobSenses.gameEvent(eventId, at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ(),
                    posture, mat, blockId, player != null);
        } catch (Throwable t) {
            ModLog.failure("Reading a game event", t);
        }
    }
}
