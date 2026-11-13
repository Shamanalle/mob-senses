package com.kasper.mobsenses;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat integration for Mob Senses across Fabric, Forge, NeoForge, and Bukkit/Paper.
 * <p>
 * Decodes each speaking player's Opus microphone packets on the server, normalizes their loudness
 * against that player's adaptive microphone median in {@link VoiceTracker}, and feeds the resulting
 * voice radius into the world's hearing tick so nearby mobs hear players talking or shouting.
 */
@ForgeVoicechatPlugin
public final class MobSensesVoicePlugin implements VoicechatPlugin {

    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return MobSenses.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        MobSenses.markVoiceChatActive(true);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStopped);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onPlayerDisconnected);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        MobSenses.markVoiceChatActive(true);
    }

    private void onServerStopped(VoicechatServerStoppedEvent event) {
        for (OpusDecoder decoder : decoders.values()) {
            try {
                decoder.close();
            } catch (Throwable ignored) {
            }
        }
        decoders.clear();
        MobSenses.voice().clear();
    }

    private void onPlayerDisconnected(PlayerDisconnectedEvent event) {
        UUID playerUuid = event.getPlayerUuid();
        if (playerUuid == null) {
            return;
        }
        OpusDecoder decoder = decoders.remove(playerUuid);
        if (decoder != null) {
            try {
                decoder.close();
            } catch (Throwable ignored) {
            }
        }
        MobSenses.voice().removePlayer(playerUuid);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        try {
            if (!MobSenses.settings().voice()) {
                return;
            }
            VoicechatConnection sender = event.getSenderConnection();
            if (sender == null || sender.getPlayer() == null) {
                return;
            }
            UUID playerUuid = sender.getPlayer().getUuid();
            if (playerUuid == null) {
                return;
            }
            byte[] opus = event.getPacket().getOpusEncodedData();
            if (opus == null || opus.length == 0) {
                return;
            }
            OpusDecoder decoder = decoders.computeIfAbsent(playerUuid, id -> event.getVoicechat().createDecoder());
            if (decoder == null || decoder.isClosed()) {
                decoders.remove(playerUuid);
                return;
            }
            short[] pcm;
            synchronized (decoder) {
                pcm = decoder.decode(opus);
            }
            if (pcm != null && pcm.length > 0) {
                long now = System.currentTimeMillis();
                double rawRange = MobSenses.voice().onVoiceFrame(playerUuid, pcm, event.getPacket().isWhispering(), now);
                ServerSettings srv = MobSenses.settings();
                ServerLink link = MobSenses.serverLink();
                Preset p = link.connected() ? link.activePreset() : srv.preset();
                double effRange = (p == Preset.OFF || !srv.voice())
                        ? 0.0
                        : NoiseModel.effectiveRange(rawRange, p, srv.hearing());
                UUID localId = MobSenses.localPlayerId();
                if (effRange >= NoiseModel.MIN_AUDIBLE_RANGE && localId != null && localId.equals(playerUuid)) {
                    MobSenses.hud().onLocalNoise(effRange, now);
                }
            }
        } catch (Throwable t) {
            ModLog.failure("Decoding a Simple Voice Chat packet", t);
        }
    }

    private void onClientSound(ClientSoundEvent event) {
        try {
            short[] raw = event.getRawAudio();
            if (raw != null && raw.length > 0) {
                long now = System.currentTimeMillis();
                MobSenses.voice().onClientSound(raw, event.isWhispering(), now);
                double range = MobSenses.voice().clientVoiceRangeBlocks(now);
                if (range >= NoiseModel.MIN_AUDIBLE_RANGE) {
                    MobSenses.hud().onLocalNoise(range, now);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
