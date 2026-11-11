package com.kasper.mobsenses;

import com.kasper.mobsenses.client.ClientLink;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Client half of the {@link LinkProtocol} link on Fabric (Minecraft 1.16.5 - 1.20.4, channel API).
 */
public final class ModClientNetworking {

    private ModClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.STATE, (client, handler, buf, responseSender) -> {
            String raw = ModNetworking.readText(buf);
            if (raw != null) {
                ClientLink.onServerStateText(raw);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ADMIN_REPLY, (client, handler, buf, responseSender) -> {
            String raw = ModNetworking.readText(buf);
            if (raw != null) {
                ClientLink.onAdminReplyText(raw);
            }
        });
        ClientLink.bindNetworking(
                raw -> send(ModNetworking.HELLO, raw),
                raw -> send(ModNetworking.ADMIN, raw));
    }

    private static void send(ResourceLocation channel, String raw) {
        if (raw == null || !ClientPlayNetworking.canSend(channel)) {
            return;
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(raw, LinkProtocol.MAX_BYTES);
        ClientPlayNetworking.send(channel, buf);
    }
}
