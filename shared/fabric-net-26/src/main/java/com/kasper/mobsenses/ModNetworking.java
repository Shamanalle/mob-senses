package com.kasper.mobsenses;

import com.kasper.mobsenses.server.AdminPermission;
import com.kasper.mobsenses.server.ServerBridge;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server half of the {@link LinkProtocol} link on Fabric (Minecraft 26.x, payload API).
 */
public final class ModNetworking {

    public record Hello(String text) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.HELLO_PATH));
        public static final StreamCodec<ByteBuf, Hello> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_BYTES).map(Hello::new, Hello::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record State(String text) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.STATE_PATH));
        public static final StreamCodec<ByteBuf, State> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_BYTES).map(State::new, State::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Admin(String text) implements CustomPacketPayload {
        public static final Type<Admin> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN_PATH));
        public static final StreamCodec<ByteBuf, Admin> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_BYTES).map(Admin::new, Admin::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AdminReply(String text) implements CustomPacketPayload {
        public static final Type<AdminReply> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN_REPLY_PATH));
        public static final StreamCodec<ByteBuf, AdminReply> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_BYTES).map(AdminReply::new, AdminReply::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private ModNetworking() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.serverboundPlay().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Admin.TYPE, Admin.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdminReply.TYPE, AdminReply.CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(Hello.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player != null) {
                context.server().execute(() -> ServerBridge.onHello(player, payload.text(), isAdmin(player), ModNetworking::sendState));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(Admin.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            if (player != null && server != null) {
                server.execute(() -> ServerBridge.onAdmin(
                        player,
                        payload.text(),
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
        if (player != null && raw != null && ServerPlayNetworking.canSend(player, State.TYPE)) {
            ServerPlayNetworking.send(player, new State(raw));
        }
    }

    public static void sendAdminReply(ServerPlayer player, String raw) {
        if (player != null && raw != null && ServerPlayNetworking.canSend(player, AdminReply.TYPE)) {
            ServerPlayNetworking.send(player, new AdminReply(raw));
        }
    }
}
