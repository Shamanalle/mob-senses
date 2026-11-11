package com.kasper.mobsenses;

import com.kasper.mobsenses.server.AdminPermission;
import com.kasper.mobsenses.server.ServerBridge;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server half of the {@link LinkProtocol} link on Fabric (Minecraft 1.16.5 - 1.20.4, channel API).
 */
public final class ModNetworking {

    public static final ResourceLocation HELLO = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.HELLO_PATH);
    public static final ResourceLocation STATE = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.STATE_PATH);
    public static final ResourceLocation ADMIN = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN_PATH);
    public static final ResourceLocation ADMIN_REPLY = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN_REPLY_PATH);

    private ModNetworking() {
    }

    public static void registerCommon() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(HELLO, (server, player, handler, buf, responseSender) -> {
            String raw = readText(buf);
            if (raw != null) {
                server.execute(() -> ServerBridge.onHello(player, raw, isAdmin(player), ModNetworking::sendState));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ADMIN, (server, player, handler, buf, responseSender) -> {
            String raw = readText(buf);
            if (raw != null) {
                server.execute(() -> ServerBridge.onAdmin(
                        player,
                        raw,
                        isAdmin(player),
                        ModNetworking::sendAdminReply,
                        () -> ServerBridge.broadcastNow(server, ModNetworking::isAdmin, ModNetworking::sendState)));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler != null && handler.player != null) {
                ServerBridge.onDisconnect(handler.player.getUUID());
            }
        });
    }

    public static void tickServer(MinecraftServer server) {
        ServerBridge.tickServer(server, ModNetworking::isAdmin, ModNetworking::sendState);
    }

    public static boolean isAdmin(ServerPlayer player) {
        return player != null && AdminPermission.isAdmin(player.createCommandSourceStack());
    }

    public static void sendState(ServerPlayer player, String raw) {
        send(player, STATE, raw);
    }

    public static void sendAdminReply(ServerPlayer player, String raw) {
        send(player, ADMIN_REPLY, raw);
    }

    private static void send(ServerPlayer player, ResourceLocation channel, String raw) {
        if (player == null || raw == null || !ServerPlayNetworking.canSend(player, channel)) {
            return;
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(raw, LinkProtocol.MAX_BYTES);
        ServerPlayNetworking.send(player, channel, buf);
    }

    static String readText(FriendlyByteBuf buf) {
        try {
            return buf == null ? null : buf.readUtf(LinkProtocol.MAX_BYTES);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
