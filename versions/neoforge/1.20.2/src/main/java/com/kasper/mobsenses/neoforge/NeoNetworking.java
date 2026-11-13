package com.kasper.mobsenses.neoforge;

import com.kasper.mobsenses.LinkProtocol;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.server.CommandSources;
import com.kasper.mobsenses.server.ServerBridge;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.custom.payload.SimplePayload;
import net.neoforged.neoforge.network.event.EventNetworkChannel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.function.Consumer;

/**
 * Optional client-server {@link LinkProtocol} channels on NeoForge 1.20.2 - 1.20.3.
 */
final class NeoNetworking {

    static final ResourceLocation HELLO = id(LinkProtocol.HELLO_PATH);
    static final ResourceLocation STATE = id(LinkProtocol.STATE_PATH);
    static final ResourceLocation ADMIN = id(LinkProtocol.ADMIN_PATH);
    static final ResourceLocation ADMIN_REPLY = id(LinkProtocol.ADMIN_REPLY_PATH);

    private static boolean registered;

    private NeoNetworking() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(LinkProtocol.NAMESPACE, path);
    }

    private static EventNetworkChannel channel(ResourceLocation name) {
        return NetworkRegistry.newEventChannel(name, () -> "1", v -> true, v -> true);
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        listen(channel(HELLO), NeoNetworking::onHello, null);
        listen(channel(ADMIN), NeoNetworking::onAdmin, null);
        listen(channel(STATE), null, ClientLink::onServerStateText);
        listen(channel(ADMIN_REPLY), null, ClientLink::onAdminReplyText);
    }

    private interface ServerHandler {
        void handle(ServerPlayer player, String raw);
    }

    private static void listen(EventNetworkChannel channel, ServerHandler server, Consumer<String> client) {
        if (server != null) {
            channel.addListener((NetworkEvent.ClientCustomPayloadEvent e) -> {
                NetworkEvent.Context ctx = e.getSource();
                ServerPlayer player = ctx.getSender();
                String raw = read(e.getPayload());
                if (player != null && raw != null) {
                    ctx.enqueueWork(() -> server.handle(player, raw));
                }
                ctx.setPacketHandled(true);
            });
        }
        if (client != null) {
            channel.addListener((NetworkEvent.ServerCustomPayloadEvent e) -> {
                NetworkEvent.Context ctx = e.getSource();
                String raw = read(e.getPayload());
                if (raw != null) {
                    ctx.enqueueWork(() -> client.accept(raw));
                }
                ctx.setPacketHandled(true);
            });
        }
    }

    static void tickServer(MinecraftServer server) {
        ServerBridge.tickServer(server, NeoNetworking::isAdmin, NeoNetworking::sendState);
    }

    static boolean isAdmin(ServerPlayer player) {
        return CommandSources.isAdmin(player);
    }

    static void sendState(ServerPlayer player, String raw) {
        sendToClient(player, STATE, raw);
    }

    static void sendAdminReply(ServerPlayer player, String raw) {
        sendToClient(player, ADMIN_REPLY, raw);
    }

    private static void sendToClient(ServerPlayer player, ResourceLocation channel, String raw) {
        if (player != null && player.connection != null && raw != null) {
            player.connection.send(new ClientboundCustomPayloadPacket(SimplePayload.outbound(write(raw), 0, channel)));
        }
    }

    static void sendHelloToServer(String raw) {
        sendToServer(HELLO, raw);
    }

    static void sendAdminToServer(String raw) {
        sendToServer(ADMIN, raw);
    }

    private static void sendToServer(ResourceLocation channel, String raw) {
        if (raw == null) {
            return;
        }
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(SimplePayload.outbound(write(raw), 0, channel)));
        }
    }

    private static void onHello(ServerPlayer player, String raw) {
        ServerBridge.onHello(player, raw, isAdmin(player), NeoNetworking::sendState);
    }

    private static void onAdmin(ServerPlayer player, String raw) {
        ServerBridge.onAdmin(player, raw, isAdmin(player), NeoNetworking::sendAdminReply, () -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerBridge.broadcastNow(server, NeoNetworking::isAdmin, NeoNetworking::sendState);
            }
        });
    }

    private static String read(FriendlyByteBuf buf) {
        try {
            return buf == null ? null : buf.readUtf(LinkProtocol.MAX_BYTES);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static FriendlyByteBuf write(String raw) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(raw, LinkProtocol.MAX_BYTES);
        return buf;
    }
}
