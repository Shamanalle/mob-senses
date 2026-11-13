package com.kasper.mobsenses.server;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Sends blood-trail particles for wounded players and debug traces when {@code /mobsenses debug} is active.
 *
 * <p>{@code ServerLevel.sendParticles(ServerPlayer, ...)} gained a second boolean parameter in
 * Minecraft 1.21.4; resolving it reflectively lets one class serve every version band.
 */
public final class ParticleSender {

    private static final Method SEND_11;
    private static final Method SEND_12;
    private static final ParticleOptions BLOOD_MIST;

    static {
        Method m11 = null;
        Method m12 = null;
        for (Method m : ServerLevel.class.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 11
                    && p[0] == ServerPlayer.class
                    && ParticleOptions.class.isAssignableFrom(p[1])
                    && p[2] == boolean.class
                    && p[3] == double.class) {
                m11 = m;
            } else if (p.length == 12
                    && p[0] == ServerPlayer.class
                    && ParticleOptions.class.isAssignableFrom(p[1])
                    && p[2] == boolean.class
                    && p[3] == boolean.class
                    && p[4] == double.class) {
                m12 = m;
            }
        }
        SEND_11 = m11;
        SEND_12 = m12;
        BLOOD_MIST = resolveBloodMist();
    }

    private ParticleSender() {
    }

    private static ParticleOptions resolveBloodMist() {
        try {
            // Minecraft 1.21.2+: DustParticleOptions(int rgb, float scale)
            Constructor<DustParticleOptions> cInt = DustParticleOptions.class.getConstructor(int.class, float.class);
            return cInt.newInstance(0xB00808, 0.85f);
        } catch (Throwable ignored) {
        }
        try {
            // Minecraft 1.16.5: DustParticleOptions(float r, float g, float b, float scale)
            Constructor<DustParticleOptions> cFloat = DustParticleOptions.class.getConstructor(
                    float.class, float.class, float.class, float.class);
            return cFloat.newInstance(0.69f, 0.03f, 0.03f, 0.85f);
        } catch (Throwable ignored) {
        }
        try {
            // Minecraft 1.17 – 1.21.1: DustParticleOptions(Vector3f color, float scale)
            for (Constructor<?> c : DustParticleOptions.class.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && p[1] == float.class && p[0] != int.class) {
                    Constructor<?> vecCtor = p[0].getConstructor(float.class, float.class, float.class);
                    Object vec = vecCtor.newInstance(0.69f, 0.03f, 0.03f);
                    return (ParticleOptions) c.newInstance(vec, 0.85f);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ParticleOptions bloodDrop() {
        return new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.REDSTONE_BLOCK.defaultBlockState());
    }

    /**
     * Spawns subtle, low-profile crimson droplets on the ground at a wounded player's scent crumb.
     */
    public static void sendBloodCrumb(ServerLevel level, double x, double y, double z) {
        ParticleOptions drop = BLOOD_MIST != null ? BLOOD_MIST : bloodDrop();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) > 32.0 * 32.0) {
                continue;
            }
            send(level, player, drop, x, y + 0.06, z, 3, 0.14, 0.02, 0.14, 0.0);
        }
    }

    private static boolean canSeeDebug(ServerPlayer player, double sx, double sy, double sz) {
        return player != null
                && player.distanceToSqr(sx, sy, sz) <= 96.0 * 96.0
                && CommandSources.isAdmin(player);
    }

    public static void sendHearingTrace(
            ServerLevel level,
            double sx, double sy, double sz,
            double ex, double ey, double ez) {
        for (ServerPlayer player : level.players()) {
            if (!canSeeDebug(player, sx, sy, sz)) {
                continue;
            }
            send(level, player, ParticleTypes.NOTE, sx, sy + 0.35, sz, 1, 0.0, 0.0, 0.0, 0.0);
            send(level, player, ParticleTypes.ANGRY_VILLAGER, ex, ey + 0.45, ez, 1, 0.1, 0.1, 0.1, 0.0);
            sendLine(level, player, ParticleTypes.END_ROD, sx, sy, sz, ex, ey, ez, 5);
        }
    }

    public static void sendScentTrace(
            ServerLevel level,
            double sx, double sy, double sz,
            double ex, double ey, double ez) {
        ParticleOptions mist = BLOOD_MIST != null ? BLOOD_MIST : bloodDrop();
        for (ServerPlayer player : level.players()) {
            if (!canSeeDebug(player, sx, sy, sz)) {
                continue;
            }
            send(level, player, mist, sx, sy + 0.10, sz, 2, 0.12, 0.02, 0.12, 0.0);
            send(level, player, ParticleTypes.ANGRY_VILLAGER, ex, ey + 0.45, ez, 1, 0.1, 0.1, 0.1, 0.0);
            sendLine(level, player, mist, sx, sy + 0.15, sz, ex, ey - 0.3, ez, 5);
        }
    }

    public static void sendSightTrace(
            ServerLevel level,
            double sx, double sy, double sz,
            double ex, double ey, double ez) {
        for (ServerPlayer player : level.players()) {
            if (!canSeeDebug(player, sx, sy, sz)) {
                continue;
            }
            send(level, player, ParticleTypes.FLAME, sx, sy + 0.35, sz, 1, 0.05, 0.05, 0.05, 0.01);
            send(level, player, ParticleTypes.ANGRY_VILLAGER, ex, ey + 0.45, ez, 1, 0.1, 0.1, 0.1, 0.0);
            sendLine(level, player, ParticleTypes.ENCHANTED_HIT, sx, sy, sz, ex, ey, ez, 5);
        }
    }

    private static void sendLine(
            ServerLevel level,
            ServerPlayer player,
            ParticleOptions particle,
            double sx, double sy, double sz,
            double ex, double ey, double ez,
            int steps) {
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double px = sx + (ex - sx) * t;
            double py = sy + (ey - sy) * t;
            double pz = sz + (ez - sz) * t;
            send(level, player, particle, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public static void send(
            ServerLevel level,
            ServerPlayer viewer,
            ParticleOptions particle,
            double x, double y, double z,
            int count,
            double dx, double dy, double dz,
            double speed) {
        try {
            if (SEND_12 != null) {
                SEND_12.invoke(level, viewer, particle, true, false, x, y, z, count, dx, dy, dz, speed);
            } else if (SEND_11 != null) {
                SEND_11.invoke(level, viewer, particle, true, x, y, z, count, dx, dy, dz, speed);
            }
        } catch (Throwable ignored) {
        }
    }
}
