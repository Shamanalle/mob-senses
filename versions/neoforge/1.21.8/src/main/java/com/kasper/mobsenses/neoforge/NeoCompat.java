package com.kasper.mobsenses.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;

/**
 * Version adapter for NeoForge 1.21.6 - 1.21.8 ({@code PacketDistributor} in 21.6-21.7,
 * {@code ClientPacketDistributor} in 21.8).
 */
final class NeoCompat {

    private static volatile Method sendMethod;

    private NeoCompat() {
    }

    static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    static void sendToServer(CustomPacketPayload payload) {
        try {
            Method m = sendMethod;
            if (m == null) {
                m = find("net.neoforged.neoforge.client.network.ClientPacketDistributor");
                if (m == null) {
                    m = find("net.neoforged.neoforge.network.PacketDistributor");
                }
                sendMethod = m;
            }
            if (m != null) {
                m.invoke(null, payload, new CustomPacketPayload[0]);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Method find(String owner) {
        try {
            return Class.forName(owner).getMethod("sendToServer", CustomPacketPayload.class, CustomPacketPayload[].class);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
