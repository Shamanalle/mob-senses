package com.kasper.mobsenses.neoforge;

import com.kasper.mobsenses.LinkProtocol;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.server.CommandSources;
import com.kasper.mobsenses.server.ServerBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Optional client-server {@link LinkProtocol} payloads on NeoForge 1.20.4.
 */
final class NeoNetworking {

    static final ResourceLocation HELLO_ID = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.HELLO_PATH);
    static final ResourceLocation STATE_ID = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.STATE_PATH);
    static final ResourceLocation ADMIN_ID = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN_PATH);
    static final ResourceLocation ADMIN_REPLY_ID = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN_REPLY_PATH);

    record TextPayload(ResourceLocation channel, String text) implements CustomPacketPayload {
        @Override
        public ResourceLocation id() {
            return channel;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(text, LinkProtocol.MAX_BYTES);
        }
    }

    private NeoNetworking() {
    }

    static void register(RegisterPayloadHandlerEvent event) {
        IPayloadRegistrar registrar = event.registrar(LinkProtocol.NAMESPACE).versioned("1").optional();
        registrar.play(HELLO_ID, buf -> new TextPayload(HELLO_ID, buf.readUtf(LinkProtocol.MAX_BYTES)),
                handlers -> handlers.server((payload, ctx) -> {
                    Player p = ctx.player().orElse(null);
                    if (p instanceof ServerPlayer sp) {
                        ctx.workHandler().execute(() ->
                                ServerBridge.onHello(sp, payload.text(), isAdmin(sp), NeoNetworking::sendState));
                    }
                }));
        registrar.play(ADMIN_ID, buf -> new TextPayload(ADMIN_ID, buf.readUtf(LinkProtocol.MAX_BYTES)),
                handlers -> handlers.server((payload, ctx) -> {
                    Player p = ctx.player().orElse(null);
                    if (p instanceof ServerPlayer sp) {
                        ctx.workHandler().execute(() ->
                                ServerBridge.onAdmin(sp, payload.text(), isAdmin(sp), NeoNetworking::sendAdminReply, () -> {
                                    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                                    if (server != null) {
                                        ServerBridge.broadcastNow(server, NeoNetworking::isAdmin, NeoNetworking::sendState);
                                    }
                                }));
                    }
                }));
        registrar.play(STATE_ID, buf -> new TextPayload(STATE_ID, buf.readUtf(LinkProtocol.MAX_BYTES)),
                handlers -> handlers.client((payload, ctx) ->
                        ctx.workHandler().execute(() -> ClientLink.onServerStateText(payload.text()))));
        registrar.play(ADMIN_REPLY_ID, buf -> new TextPayload(ADMIN_REPLY_ID, buf.readUtf(LinkProtocol.MAX_BYTES)),
                handlers -> handlers.client((payload, ctx) ->
                        ctx.workHandler().execute(() -> ClientLink.onAdminReplyText(payload.text()))));
    }

    static void tickServer(MinecraftServer server) {
        ServerBridge.tickServer(server, NeoNetworking::isAdmin, NeoNetworking::sendState);
    }

    static boolean isAdmin(ServerPlayer player) {
        return CommandSources.isAdmin(player);
    }

    static void sendState(ServerPlayer player, String raw) {
        if (player != null && raw != null && player.connection != null && player.connection.isConnected(STATE_ID)) {
            PacketDistributor.PLAYER.with(player).send(new TextPayload(STATE_ID, raw));
        }
    }

    static void sendAdminReply(ServerPlayer player, String raw) {
        if (player != null && raw != null && player.connection != null && player.connection.isConnected(ADMIN_REPLY_ID)) {
            PacketDistributor.PLAYER.with(player).send(new TextPayload(ADMIN_REPLY_ID, raw));
        }
    }

    static void sendHelloToServer(String raw) {
        sendToServer(HELLO_ID, raw);
    }

    static void sendAdminToServer(String raw) {
        sendToServer(ADMIN_ID, raw);
    }

    private static void sendToServer(ResourceLocation channel, String raw) {
        if (raw == null) {
            return;
        }
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null && conn.isConnected(channel)) {
            PacketDistributor.SERVER.noArg().send(new TextPayload(channel, raw));
        }
    }
}
