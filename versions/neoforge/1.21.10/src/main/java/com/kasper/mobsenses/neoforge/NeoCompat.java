package com.kasper.mobsenses.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Version adapter for NeoForge 1.21.9 - 1.21.10.
 */
final class NeoCompat {

    private NeoCompat() {
    }

    static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
