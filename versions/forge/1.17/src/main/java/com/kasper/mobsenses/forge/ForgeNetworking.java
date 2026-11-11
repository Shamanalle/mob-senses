package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.LinkProtocol;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.server.AdminPermission;
import com.kasper.mobsenses.server.ServerBridge;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fmllegacy.network.NetworkEvent;
import net.minecraftforge.fmllegacy.network.NetworkRegistry;
import net.minecraftforge.fmllegacy.network.event.EventNetworkChannel;
import net.minecraftforge.fmllegacy.server.ServerLifecycleHooks;

import java.util.function.Consumer;

/**
 * Optional client-server {@link LinkProtocol} channels on Forge 1.17.1.
 */
final class ForgeNetworking {

    static final ResourceLocation HELLO = id(LinkProtocol.HELLO_PATH);
    static final ResourceLocation STATE = id(LinkProtocol.STATE_PATH);
    static final ResourceLocation ADMIN = id(LinkProtocol.ADMIN_PATH);
    static final ResourceLocation ADMIN_REPLY = id(LinkProtocol.ADMIN_REPLY_PATH);

    private static boolean registered;

    private ForgeNetworking() {
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
        listen(channel(HELLO), ForgeNetworking::onHello, null);
        listen(channel(ADMIN), ForgeNetworking::onAdmin, null);
        listen(channel(STATE), null, ClientLink::onServerStateText);
        listen(channel(ADMIN_REPLY), null, ClientLink::onAdminReplyText);
    }

    private interface ServerHandler {
        void handle(ServerPlayer player, String raw);
    }

    private static void listen(EventNetworkChannel channel, ServerHandler server, Consumer<String> client) {
        if (server != null) {
            channel.addListener((NetworkEvent.ClientCustomPayloadEvent e) -> {
                NetworkEvent.Context ctx = e.getSource().get();
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
                NetworkEvent.Context ctx = e.getSource().get();
                String raw = read(e.getPayload());
                if (raw != null) {
                    ctx.enqueueWork(() -> client.accept(raw));
                }
                ctx.setPacketHandled(true);
            });
        }
    }

    static void tickServer(MinecraftServer server) {
        ServerBridge.tickServer(server, ForgeNetworking::isAdmin, ForgeNetworking::sendState);
    }

    static boolean isAdmin(ServerPlayer player) {
        return player != null && AdminPermission.isAdmin(player.createCommandSourceStack());
    }

    static void sendState(ServerPlayer player, String raw) {
        sendToClient(player, STATE, raw);
    }

    static void sendAdminReply(ServerPlayer player, String raw) {
        sendToClient(player, ADMIN_REPLY, raw);
    }

    private static void sendToClient(ServerPlayer player, ResourceLocation channel, String raw) {
        if (player != null && player.connection != null && raw != null) {
            player.connection.send(new ClientboundCustomPayloadPacket(channel, write(raw)));
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
            conn.send(new ServerboundCustomPayloadPacket(channel, write(raw)));
        }
    }

    private static void onHello(ServerPlayer player, String raw) {
        ServerBridge.onHello(player, raw, isAdmin(player), ForgeNetworking::sendState);
    }

    private static void onAdmin(ServerPlayer player, String raw) {
        ServerBridge.onAdmin(player, raw, isAdmin(player), ForgeNetworking::sendAdminReply, () -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerBridge.broadcastNow(server, ForgeNetworking::isAdmin, ForgeNetworking::sendState);
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
