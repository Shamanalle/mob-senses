package com.kasper.mobsenses;

import com.kasper.mobsenses.client.ClientLink;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client half of the {@link LinkProtocol} link on Fabric (Minecraft 1.20.5 - 1.21.11, payload API).
 */
public final class ModClientNetworking {

    private ModClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.State.TYPE,
                (payload, context) -> ClientLink.onServerStateText(payload.text()));
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.AdminReply.TYPE,
                (payload, context) -> ClientLink.onAdminReplyText(payload.text()));
        ClientLink.bindNetworking(
                raw -> {
                    if (raw != null && ClientPlayNetworking.canSend(ModNetworking.Hello.TYPE)) {
                        ClientPlayNetworking.send(new ModNetworking.Hello(raw));
                    }
                },
                raw -> {
                    if (raw != null && ClientPlayNetworking.canSend(ModNetworking.Admin.TYPE)) {
                        ClientPlayNetworking.send(new ModNetworking.Admin(raw));
                    }
                });
    }
}
