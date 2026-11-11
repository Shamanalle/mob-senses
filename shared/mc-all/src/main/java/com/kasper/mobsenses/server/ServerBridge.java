package com.kasper.mobsenses.server;

import com.kasper.mobsenses.AdminCommands;
import com.kasper.mobsenses.Jv;
import com.kasper.mobsenses.LinkProtocol;
import com.kasper.mobsenses.MobSenses;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Shared server-side {@link LinkProtocol} coordinator for Fabric, Forge, and NeoForge servers.
 * <p>
 * Tracks connected modded clients that completed the {@code mobsenses:hello} handshake and keeps
 * their {@code mobsenses:state} and {@code mobsenses:admin_reply} channels synchronized using the
 * exact same wire protocol as {@code MobSensesBukkit}.
 */
public final class ServerBridge {

    private static final int STATE_CHECK_TICKS = 20;
    private static final int TELEMETRY_PUSH_TICKS = 100;

    /** Online modded players who sent {@code mobsenses:hello} (UUID -> client language). */
    private static final Map<UUID, String> MODDED_PLAYERS = new ConcurrentHashMap<>();
    /** Last synced state fingerprint per modded player so world/permission/HUD changes push immediately. */
    private static final Map<UUID, String> FINGERPRINTS = new ConcurrentHashMap<>();

    private static volatile boolean dirty;
    private static int tickCounter;

    static {
        MobSenses.setSettingsListener(ServerBridge::markDirty);
    }

    private ServerBridge() {
    }

    /** Marks server state dirty so the next {@link #tickServer} broadcasts immediately to all modded clients. */
    public static void markDirty() {
        dirty = true;
    }

    /** Forgets a disconnected player's handshake and fingerprint state. */
    public static void onDisconnect(UUID playerId) {
        if (playerId != null) {
            MODDED_PLAYERS.remove(playerId);
            FINGERPRINTS.remove(playerId);
        }
    }

    /** Clears all tracked client sessions when a server stops. */
    public static void clear() {
        MODDED_PLAYERS.clear();
        FINGERPRINTS.clear();
        dirty = false;
        tickCounter = 0;
    }

    public static void onHello(
            ServerPlayer player,
            String raw,
            boolean isAdmin,
            BiConsumer<ServerPlayer, String> stateSender) {
        if (player == null) {
            return;
        }
        LinkProtocol.Hello hello = LinkProtocol.parseHello(raw);
        if (hello == null) {
            return;
        }
        MODDED_PLAYERS.put(player.getUUID(), hello.language());
        sendStateIfNeeded(player, isAdmin, true, stateSender);
    }

    public static void onAdmin(
            ServerPlayer player,
            String raw,
            boolean isAdmin,
            BiConsumer<ServerPlayer, String> replySender,
            Runnable broadcastNow) {
        if (player == null) {
            return;
        }
        Properties reqProps = LinkProtocol.readProperties(raw);
        if (reqProps == null) {
            return;
        }
        CommandSourceStack source = player.createCommandSourceStack();
        MinecraftServer server = source.getServer();
        final String world = Worlds.id(source.getLevel());
        final List<String> worlds = server != null ? Worlds.ids(server) : Collections.singletonList(world);
        final boolean dedicated = server != null && server.isDedicatedServer();
        String knownLang = MODDED_PLAYERS.get(player.getUUID());
        final String fallbackLang = !Jv.isBlank(knownLang) ? knownLang : PlayerLanguage.of(player);

        AdminCommands.Context baseCtx = new AdminCommands.Context() {
            @Override
            public String language() {
                return fallbackLang;
            }

            @Override
            public String world() {
                return world;
            }

            @Override
            public List<String> worlds() {
                return worlds;
            }

            @Override
            public boolean isDedicatedServer() {
                return dedicated;
            }

            @Override
            public boolean hasPermission(String node) {
                return isAdmin;
            }
        };

        Properties replyProps = LinkProtocol.executeAdmin(reqProps, baseCtx);
        if (replySender != null) {
            replySender.accept(player, LinkProtocol.writeProperties(replyProps));
        }
        markDirty();
        if (broadcastNow != null) {
            broadcastNow.run();
        }
    }

    public static void tickServer(
            MinecraftServer server,
            Predicate<ServerPlayer> isAdmin,
            BiConsumer<ServerPlayer, String> stateSender) {
        if (server == null || stateSender == null || MODDED_PLAYERS.isEmpty()) {
            dirty = false;
            return;
        }
        int t = ++tickCounter;
        if ((t % 200) == 0) {
            for (UUID id : MODDED_PLAYERS.keySet()) {
                if (server.getPlayerList().getPlayer(id) == null) {
                    MODDED_PLAYERS.remove(id);
                    FINGERPRINTS.remove(id);
                }
            }
            if (MODDED_PLAYERS.isEmpty()) {
                dirty = false;
                return;
            }
        }
        boolean force = dirty || (t % TELEMETRY_PUSH_TICKS) == 0;
        if (!force && (t % STATE_CHECK_TICKS) != 0) {
            return;
        }
        dirty = false;
        broadcast(server, isAdmin, stateSender, force);
    }

    /** Immediately broadcasts fresh {@code mobsenses:state} to all connected modded clients. */
    public static void broadcastNow(
            MinecraftServer server,
            Predicate<ServerPlayer> isAdmin,
            BiConsumer<ServerPlayer, String> stateSender) {
        if (server == null || stateSender == null || MODDED_PLAYERS.isEmpty()) {
            return;
        }
        dirty = false;
        broadcast(server, isAdmin, stateSender, true);
    }

    private static void broadcast(
            MinecraftServer server,
            Predicate<ServerPlayer> isAdmin,
            BiConsumer<ServerPlayer, String> stateSender,
            boolean force) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (MODDED_PLAYERS.containsKey(online.getUUID())) {
                boolean admin = isAdmin != null && isAdmin.test(online);
                sendStateIfNeeded(online, admin, force, stateSender);
            }
        }
    }

    private static void sendStateIfNeeded(
            ServerPlayer player,
            boolean admin,
            boolean force,
            BiConsumer<ServerPlayer, String> stateSender) {
        if (player == null || stateSender == null) {
            return;
        }
        String world;
        try {
            world = Worlds.id(player.createCommandSourceStack().getLevel());
        } catch (Throwable t) {
            return;
        }
        String fp = world + '|' + admin + '|' + MobSenses.debug()
                + '|' + MobSenses.settings().allowClientHud()
                + '|' + MobSenses.settings().presetOf(world).id();
        String prev = FINGERPRINTS.put(player.getUUID(), fp);
        if (force || !fp.equals(prev)) {
            Properties stateProps = LinkProtocol.buildState(MobSenses.settings(), world, admin);
            stateSender.accept(player, LinkProtocol.writeProperties(stateProps));
        }
    }
}
