package com.kasper.mobsenses;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class LinkProtocolTest {

    @AfterEach
    void cleanup() {
        MobSenses.serverLink().resetClientState();
        MobSenses.serverLink().setAdminSender(null);
        MobSenses.hud().setEnabled(true);
    }

    @Test
    @DisplayName("VarInt UTF-8 packet encoding roundtrips ASCII, Cyrillic, and CJK properties")
    void varIntRoundtripUnicode() {
        Properties in = new Properties();
        in.setProperty("v", "1");
        in.setProperty("platform", "Paper 1.21.4");
        in.setProperty("note", "Чувства мобов · 怪物感官");
        in.setProperty("hearing", "1.75");

        byte[] wire = LinkProtocol.encodePacket(in);
        assertNotNull(wire);
        assertTrue(wire.length > 10);

        Properties out = LinkProtocol.decodePacket(wire);
        assertNotNull(out);
        assertEquals("1", out.getProperty("v"));
        assertEquals("Paper 1.21.4", out.getProperty("platform"));
        assertEquals("Чувства мобов · 怪物感官", out.getProperty("note"));
        assertEquals("1.75", out.getProperty("hearing"));
    }

    @Test
    @DisplayName("encodeString and decodeString reject payloads exceeding MAX_BYTES (32767)")
    void oversizeRejected() {
        StringBuilder huge = new StringBuilder(LinkProtocol.MAX_BYTES + 100);
        for (int i = 0; i < LinkProtocol.MAX_BYTES + 10; i++) {
            huge.append('a');
        }
        assertThrows(IllegalArgumentException.class, () -> LinkProtocol.encodeString(huge.toString()));

        // Craft malformed VarInt claiming 40000 bytes
        byte[] fakeOversize = new byte[]{(byte) 0xC0, (byte) 0xB8, (byte) 0x02, 0x61};
        assertNull(LinkProtocol.decodeString(fakeOversize));
        assertNull(LinkProtocol.decodePacket(fakeOversize));
        assertNull(LinkProtocol.decodePacket(null));
        assertNull(LinkProtocol.decodePacket(new byte[0]));
    }

    @Test
    @DisplayName("HELLO and ADMIN packets roundtrip cleanly")
    void helloAndAdminRoundtrip() {
        byte[] helloBytes = LinkProtocol.encodeHello("1.5.0", "RU_RU");
        LinkProtocol.Hello hello = LinkProtocol.decodeHello(helloBytes);
        assertNotNull(hello);
        assertEquals(LinkProtocol.VERSION, hello.version());
        assertEquals("1.5.0", hello.modVersion());
        assertEquals("ru_ru", hello.language());

        byte[] adminBytes = LinkProtocol.encodeAdmin("set hearing 1.8", "uk_ua");
        LinkProtocol.AdminRequest req = LinkProtocol.decodeAdmin(adminBytes);
        assertNotNull(req);
        assertEquals("set hearing 1.8", req.command());
        assertEquals("uk_ua", req.language());
    }

    @Test
    @DisplayName("ServerLink syncs state, world preset, allow_client_hud lock, and remote settings")
    void serverLinkSyncAndFairPlayLock(@TempDir Path dir) {
        MobSenses.start(dir, new MobSenses.Platform("Fabric", "1.21.1"), true);

        ServerSettings server = MobSenses.settings();
        server.setPreset(Preset.HARDCORE);
        server.setWorldPreset("minecraft:the_nether", Preset.SUBTLE);
        server.setOption("hearing", "1.8");
        server.setOption("step_range", "14.0");
        server.setOption("allow_client_hud", "false");

        ServerLink link = MobSenses.serverLink();
        assertFalse(link.connected());
        assertTrue(link.allowClientHud(), "HUD allowed before connecting to a server");
        assertFalse(MobSenses.hud().lockedByServer());

        byte[] statePacket = LinkProtocol.encodeState(server, "the_nether", true);
        assertTrue(link.onStatePacket(statePacket));

        assertTrue(link.connected());
        assertTrue(link.isAdmin());
        assertFalse(link.allowClientHud(), "Server allow_client_hud=false must lock client HUD");
        assertTrue(MobSenses.hud().lockedByServer());
        assertFalse(MobSenses.hud().enabled(), "StealthHudState.enabled() must be false while locked by server");
        assertTrue(MobSenses.hud().userEnabled(), "User's own preference stays intact");

        assertEquals(Preset.SUBTLE, link.activePreset());
        assertEquals(Preset.HARDCORE, link.serverPreset());
        assertEquals("the_nether", link.currentWorld());
        assertEquals("Fabric 1.21.1", link.serverPlatform());
        assertTrue(link.voiceInstalled());
        assertEquals(1.8, link.remoteSettings().hearing(), 1e-6);
        assertEquals(14.0, link.remoteSettings().stepRange(), 1e-6);

        // Switching to overworld uses server global preset
        Properties overworldState = LinkProtocol.buildState(server, "minecraft:overworld", false);
        assertTrue(link.onState(overworldState));
        assertFalse(link.isAdmin());
        assertEquals(Preset.HARDCORE, link.activePreset());

        // Disconnect restores local state
        link.resetClientState();
        assertFalse(link.connected());
        assertTrue(link.allowClientHud());
        assertFalse(MobSenses.hud().lockedByServer());
        assertTrue(MobSenses.hud().enabled());
    }

    @Test
    @DisplayName("LinkProtocol.executeAdmin executes commands for admins and refuses unprivileged senders")
    void executeAdminAndPermissionCheck(@TempDir Path dir) {
        MobSenses.start(dir, new MobSenses.Platform("NeoForge", "1.21.1"), false);

        AdminCommands.Context adminCtx = new AdminCommands.Context() {
            @Override
            public String language() {
                return "en_us";
            }

            @Override
            public String world() {
                return "minecraft:overworld";
            }

            @Override
            public List<String> worlds() {
                return Jv.listOf("minecraft:overworld");
            }
        };

        ServerLink link = MobSenses.serverLink();
        AtomicReference<byte[]> sentPacket = new AtomicReference<>();
        link.setAdminPacketSender(sentPacket::set);

        assertTrue(link.sendAdmin("preset hardcore", "en_us"));
        assertNotNull(sentPacket.get());

        Properties reqProps = LinkProtocol.decodePacket(sentPacket.get());
        Properties replyProps = LinkProtocol.executeAdmin(reqProps, adminCtx);
        byte[] replyBytes = LinkProtocol.encodePacket(replyProps);

        assertTrue(link.onAdminReplyPacket(replyBytes));
        assertTrue(link.lastReplyOk());
        assertEquals(1, link.adminReplyCount());
        assertTrue(link.lastReplyMessage().contains("Hardcore"));
        assertEquals(Preset.HARDCORE, MobSenses.settings().preset());

        AdminCommands.Context guestCtx = new AdminCommands.Context() {
            @Override
            public String language() {
                return "en_us";
            }

            @Override
            public String world() {
                return "minecraft:overworld";
            }

            @Override
            public List<String> worlds() {
                return Jv.listOf("minecraft:overworld");
            }

            @Override
            public boolean hasPermission(String node) {
                return false;
            }
        };

        Properties deniedReply = LinkProtocol.executeAdmin(LinkProtocol.buildAdmin("preset off", "en_us"), guestCtx);
        assertTrue(link.onAdminReply(deniedReply));
        assertFalse(link.lastReplyOk());
        assertEquals(Preset.HARDCORE, MobSenses.settings().preset());
    }

    @Test
    @DisplayName("ServerLink syncs player_alert telemetry and resets it on disconnect")
    void playerAlertSyncsAndResets(@TempDir Path dir) {
        MobSenses.start(dir, new MobSenses.Platform("Paper", "1.21.4"), false);
        ServerLink link = MobSenses.serverLink();

        Properties hunted = LinkProtocol.buildState(
                MobSenses.settings(), "minecraft:overworld", false, false, false, 5L, 2L, "Paper 1.21.4",
                StealthHudState.ALERT_HUNTED);
        assertTrue(link.onState(hunted));
        assertEquals(StealthHudState.ALERT_HUNTED, MobSenses.hud().mobAlertLevel());
        assertEquals("gui.mobsenses.hud.alert.hunted", MobSenses.hud().alertKey());

        link.resetClientState();
        assertEquals(StealthHudState.ALERT_CALM, MobSenses.hud().mobAlertLevel());
    }
}
