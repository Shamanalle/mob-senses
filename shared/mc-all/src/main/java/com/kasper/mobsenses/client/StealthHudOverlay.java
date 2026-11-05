package com.kasper.mobsenses.client;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.NoiseModel;
import com.kasper.mobsenses.SightModel;
import com.kasper.mobsenses.StealthHudState;
import com.kasper.mobsenses.server.BlockAcoustics;
import com.kasper.mobsenses.server.ServerHearing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Updates and renders the client-side Stealth & Noise HUD.
 */
public final class StealthHudOverlay {

    private static final int BG_COLOR = 0xB810141C;
    private static final int BORDER_COLOR = 0xCC2A3346;
    private static final int TRACK_COLOR = 0xFF1E2533;
    private static final int SUBTEXT_COLOR = 0xFFA3B1C6;

    private static final int COLOR_HIDDEN = 0xFF4ADE80;
    private static final int COLOR_CAUTION = 0xFFFBBF24;
    private static final int COLOR_EXPOSED = 0xFFF87171;
    private static final int COLOR_LIGHT = 0xFFFACC15;
    private static final int COLOR_SCENT = 0xFFEF4444;

    private StealthHudOverlay() {
    }

    /**
     * Samples the local player's posture, surface block, ambient/held light, health, and voice
     * level, updates {@link MobSenses#hud()}, and draws the HUD onto {@code canvas}.
     */
    public static void render(Canvas canvas) {
        StealthHudState hud = MobSenses.hud();
        if (!hud.enabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || player.isSpectator()) {
            return;
        }
        samplePlayer(player, level, hud);
        drawHud(canvas, hud);
    }

    public static void samplePlayer(LocalPlayer player, Level level, StealthHudState hud) {
        long now = System.currentTimeMillis();
        BlockPos pos = player.blockPosition();
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
        int heldLight = Math.max(
                SightModel.heldItemLight(ServerHearing.itemId(player.getMainHandItem())),
                SightModel.heldItemLight(ServerHearing.itemId(player.getOffhandItem())));
        boolean sneaking = player.isShiftKeyDown() || player.isCrouching();
        boolean sprinting = player.isSprinting();
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        boolean moving = player.onGround() && (dx * dx + dz * dz) > 1.0e-4;

        BlockPos below = BlockAcoustics.blockUnderEvent(level, pos);
        BlockState stateBelow = level.getBlockState(below);
        String blockId = BlockAcoustics.blockId(stateBelow);
        AcousticMaterial mat = BlockAcoustics.material(stateBelow, blockId);
        double surfaceFactor = NoiseModel.surfaceStepFactor(mat, blockId);

        double voiceLevel = MobSenses.voice().clientVoiceLevel(now);
        hud.update(
                blockLight, skyLight, heldLight,
                sneaking, sprinting, moving,
                surfaceFactor,
                player.getHealth(), player.getMaxHealth(),
                voiceLevel, now);
    }

    public static void drawHud(Canvas canvas, StealthHudState hud) {
        boolean showScent = hud.scentActive();
        int rows = showScent ? 3 : 2;
        int panelWidth = 118;
        int panelHeight = 20 + rows * 12;
        int x = 10;
        int y = 10;

        // Panel background & subtle border
        canvas.fill(x - 1, y - 1, x + panelWidth + 1, y + panelHeight + 1, BORDER_COLOR);
        canvas.fill(x, y, x + panelWidth, y + panelHeight, BG_COLOR);

        // Status indicator dot + status text
        int exposure = hud.overallExposure();
        int statusColor = exposure >= 65 ? COLOR_EXPOSED : (exposure >= 30 ? COLOR_CAUTION : COLOR_HIDDEN);
        canvas.fill(x + 6, y + 6, x + 11, y + 11, statusColor);
        Component statusText = Component.translatable(hud.statusKey());
        canvas.text(statusText, x + 15, y + 5, statusColor);

        if (hud.voiceActive()) {
            Component voiceTag = Component.translatable("gui.mobsenses.hud.voice");
            int vw = canvas.textWidth(voiceTag);
            canvas.text(voiceTag, x + panelWidth - vw - 6, y + 5, COLOR_EXPOSED);
        }

        // Row 1: Noise
        int rowY = y + 18;
        int noisePct = hud.noisePercent();
        int noiseColor = noisePct >= 65 ? COLOR_EXPOSED : (noisePct >= 30 ? COLOR_CAUTION : COLOR_HIDDEN);
        drawMeterRow(canvas, x + 6, rowY, panelWidth - 12,
                Component.translatable("gui.mobsenses.hud.noise"), noisePct, noiseColor);

        // Row 2: Light
        rowY += 12;
        int lightPct = hud.lightPercent();
        drawMeterRow(canvas, x + 6, rowY, panelWidth - 12,
                Component.translatable("gui.mobsenses.hud.light"), lightPct, COLOR_LIGHT);

        // Row 3: Scent (when wounded <= 50% HP)
        if (showScent) {
            rowY += 12;
            drawMeterRow(canvas, x + 6, rowY, panelWidth - 12,
                    Component.translatable("gui.mobsenses.hud.scent"), hud.scentPercent(), COLOR_SCENT);
        }
    }

    private static void drawMeterRow(
            Canvas canvas, int x, int y, int width,
            Component label, int percent, int barColor) {
        canvas.text(label, x, y, SUBTEXT_COLOR);
        int barX = x + 42;
        int barW = Math.max(24, width - 42);
        int barY = y + 2;
        int barH = 5;
        canvas.fill(barX, barY, barX + barW, barY + barH, TRACK_COLOR);
        int fillW = (int) Math.round((Math.max(0, Math.min(100, percent)) / 100.0) * barW);
        if (fillW > 0) {
            canvas.fill(barX, barY, barX + fillW, barY + barH, barColor);
        }
    }
}
