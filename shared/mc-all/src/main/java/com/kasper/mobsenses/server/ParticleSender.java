package com.kasper.mobsenses.server;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Sends debug particles to admin players when {@code /mobsenses debug} is active.
 *
 * <p>{@code ServerLevel.sendParticles(ServerPlayer, ...)} gained a second boolean parameter in
 * Minecraft 1.21.4; resolving it reflectively lets one class serve every version band.
 */
public final class ParticleSender {

    private static final Method SEND_11;
    private static final Method SEND_12;

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
    }

    private ParticleSender() {
    }

    public static void sendHearingTrace(
            ServerLevel level,
            double sx, double sy, double sz,
            double ex, double ey, double ez) {
        for (ServerPlayer player : level.players()) {
            send(level, player, ParticleTypes.NOTE, sx, sy + 0.35, sz, 1, 0.0, 0.0, 0.0, 0.0);
            send(level, player, ParticleTypes.ANGRY_VILLAGER, ex, ey + 0.45, ez, 1, 0.1, 0.1, 0.1, 0.0);
            sendLine(level, player, ParticleTypes.END_ROD, sx, sy, sz, ex, ey, ez, 5);
        }
    }

    public static void sendScentTrace(
            ServerLevel level,
            double sx, double sy, double sz,
            double ex, double ey, double ez) {
        for (ServerPlayer player : level.players()) {
            send(level, player, ParticleTypes.DAMAGE_INDICATOR, sx, sy + 0.4, sz, 1, 0.1, 0.1, 0.1, 0.0);
            send(level, player, ParticleTypes.ANGRY_VILLAGER, ex, ey + 0.45, ez, 1, 0.1, 0.1, 0.1, 0.0);
            sendLine(level, player, ParticleTypes.CRIT, sx, sy + 0.3, sz, ex, ey, ez, 5);
        }
    }

    public static void sendSightTrace(
            ServerLevel level,
            double sx, double sy, double sz,
            double ex, double ey, double ez) {
        for (ServerPlayer player : level.players()) {
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
