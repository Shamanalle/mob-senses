package com.kasper.mobsenses.neoforge;

import com.kasper.mobsenses.LinkProtocol;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.server.CommandSources;
import com.kasper.mobsenses.server.ServerBridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Optional client-server {@link LinkProtocol} payloads on NeoForge 1.20.5 - 26.x.
 */
public final class NeoNetworking {

    public record Hello(String text) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(NeoCompat.id(LinkProtocol.NAMESPACE, LinkProtocol.HELLO_PATH));
        public static final StreamCodec<ByteBuf, Hello> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_BYTES).map(Hello::new, Hello::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record State(String text) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(NeoCompat.id(LinkProtocol.NAMESPACE, LinkProtocol.STATE_PATH));
        public static final StreamCodec<ByteBuf, State> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_BYTES).map(State::new, State::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Admin(String text) implements CustomPacketPayload {
        public static final Type<Admin> TYPE = new Type<>(NeoCompat.id(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN_PATH));
        public static final StreamCodec<ByteBuf, Admin> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_BYTES).map(Admin::new, Admin::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AdminReply(String text) implements CustomPacketPayload {
        public static final Type<AdminReply> TYPE = new Type<>(NeoCompat.id(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN_REPLY_PATH));
        public static final StreamCodec<ByteBuf, AdminReply> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_BYTES).map(AdminReply::new, AdminReply::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private NeoNetworking() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(Hello.TYPE, Hello.CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        ServerBridge.onHello(sp, payload.text(), isAdmin(sp), NeoNetworking::sendState);
                    }
                }));
        registrar.playToServer(Admin.TYPE, Admin.CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        ServerBridge.onAdmin(sp, payload.text(), isAdmin(sp), NeoNetworking::sendAdminReply, () -> {
                            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                            if (server != null) {
                                ServerBridge.broadcastNow(server, NeoNetworking::isAdmin, NeoNetworking::sendState);
                            }
                        });
                    }
                }));
        registrar.playToClient(State.TYPE, State.CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> ClientLink.onServerStateText(payload.text())));
        registrar.playToClient(AdminReply.TYPE, AdminReply.CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> ClientLink.onAdminReplyText(payload.text())));
    }

    static void tickServer(MinecraftServer server) {
        ServerBridge.tickServer(server, NeoNetworking::isAdmin, NeoNetworking::sendState);
    }

    static boolean isAdmin(ServerPlayer player) {
        return CommandSources.isAdmin(player);
    }

    static void sendState(ServerPlayer player, String raw) {
        if (player != null && raw != null && player.connection != null && player.connection.hasChannel(State.TYPE)) {
            PacketDistributor.sendToPlayer(player, new State(raw));
        }
    }

    static void sendAdminReply(ServerPlayer player, String raw) {
        if (player != null && raw != null && player.connection != null && player.connection.hasChannel(AdminReply.TYPE)) {
            PacketDistributor.sendToPlayer(player, new AdminReply(raw));
        }
    }

    static void sendHelloToServer(String raw) {
        if (raw == null) {
            return;
        }
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null && conn.hasChannel(Hello.TYPE)) {
            NeoCompat.sendToServer(new Hello(raw));
        }
    }

    static void sendAdminToServer(String raw) {
        if (raw == null) {
            return;
        }
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null && conn.hasChannel(Admin.TYPE)) {
            NeoCompat.sendToServer(new Admin(raw));
        }
    }
}
