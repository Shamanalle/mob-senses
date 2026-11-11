package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.LinkProtocol;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.server.AdminPermission;
import com.kasper.mobsenses.server.ServerBridge;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Optional client-server {@link LinkProtocol} channels on Forge 1.20.2 - 1.21.10.
 */
final class ForgeNetworking {

    static final EventNetworkChannel HELLO = channel(LinkProtocol.HELLO_PATH);
    static final EventNetworkChannel STATE = channel(LinkProtocol.STATE_PATH);
    static final EventNetworkChannel ADMIN = channel(LinkProtocol.ADMIN_PATH);
    static final EventNetworkChannel ADMIN_REPLY = channel(LinkProtocol.ADMIN_REPLY_PATH);

    private static boolean registered;
    private static volatile Method getConnectionMethod;
    private static volatile Field connectionField;
    private static volatile boolean connectionResolved;

    private ForgeNetworking() {
    }

    private static EventNetworkChannel channel(String path) {
        return ChannelBuilder.named(ResourceLocation.tryBuild(LinkProtocol.NAMESPACE, path)).optional().eventNetworkChannel();
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        listen(HELLO, ForgeNetworking::onHello, null);
        listen(ADMIN, ForgeNetworking::onAdmin, null);
        listen(STATE, null, ClientLink::onServerStateText);
        listen(ADMIN_REPLY, null, ClientLink::onAdminReplyText);
    }

    private interface ServerHandler {
        void handle(ServerPlayer player, String raw);
    }

    private static void listen(EventNetworkChannel channel, ServerHandler server, Consumer<String> client) {
        channel.addListener((CustomPayloadEvent e) -> {
            CustomPayloadEvent.Context ctx = e.getSource();
            String raw = read(e.getPayload());
            if (raw != null) {
                if (ctx.isServerSide() && server != null) {
                    ServerPlayer player = ctx.getSender();
                    if (player != null) {
                        ctx.enqueueWork(() -> server.handle(player, raw));
                    }
                } else if (ctx.isClientSide() && client != null) {
                    ctx.enqueueWork(() -> client.accept(raw));
                }
            }
            ctx.setPacketHandled(true);
        });
    }

    static void tickServer(MinecraftServer server) {
        ServerBridge.tickServer(server, ForgeNetworking::isAdmin, ForgeNetworking::sendState);
    }

    static boolean isAdmin(ServerPlayer player) {
        return player != null && AdminPermission.isAdmin(player.createCommandSourceStack());
    }

    static void sendState(ServerPlayer player, String raw) {
        Connection conn = serverConnection(player);
        if (conn != null) {
            send(STATE, conn, raw);
        }
    }

    static void sendAdminReply(ServerPlayer player, String raw) {
        Connection conn = serverConnection(player);
        if (conn != null) {
            send(ADMIN_REPLY, conn, raw);
        }
    }

    static void sendHelloToServer(String raw) {
        sendToServer(HELLO, raw);
    }

    static void sendAdminToServer(String raw) {
        sendToServer(ADMIN, raw);
    }

    private static void sendToServer(EventNetworkChannel channel, String raw) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            send(channel, conn.getConnection(), raw);
        }
    }

    private static void send(EventNetworkChannel channel, Connection connection, String raw) {
        if (connection != null && raw != null) {
            channel.send(write(raw), connection);
        }
    }

    private static Connection serverConnection(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return null;
        }
        Object listener = player.connection;
        if (!connectionResolved) {
            synchronized (ForgeNetworking.class) {
                if (!connectionResolved) {
                    try {
                        Method m = listener.getClass().getMethod("getConnection");
                        if (Connection.class.isAssignableFrom(m.getReturnType())) {
                            getConnectionMethod = m;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                    if (getConnectionMethod == null) {
                        for (Class<?> c = listener.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                            for (Field f : c.getDeclaredFields()) {
                                if (f.getType() == Connection.class) {
                                    f.setAccessible(true);
                                    connectionField = f;
                                    break;
                                }
                            }
                            if (connectionField != null) {
                                break;
                            }
                        }
                    }
                    connectionResolved = true;
                }
            }
        }
        try {
            if (getConnectionMethod != null) {
                return (Connection) getConnectionMethod.invoke(listener);
            }
            if (connectionField != null) {
                return (Connection) connectionField.get(listener);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
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
