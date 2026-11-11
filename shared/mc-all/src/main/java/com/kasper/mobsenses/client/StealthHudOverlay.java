package com.kasper.mobsenses.client;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.ClientSettings;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.NoiseModel;
import com.kasper.mobsenses.SightModel;
import com.kasper.mobsenses.StealthHudState;
import com.kasper.mobsenses.server.BlockAcoustics;
import com.kasper.mobsenses.server.ServerHearing;
import com.kasper.mobsenses.server.Worlds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Updates and renders the client-side Stealth & Noise HUD in {@code tactical} or {@code minimal}
 * style, positioned and styled according to {@link ClientSettings}
 * ({@code config/mobsenses-client.properties} or {@code /mshud}).
 */
public final class StealthHudOverlay {

    // Panel & framing palette
    private static final int BG_COLOR = 0xCC0D1119;
    private static final int BORDER_COLOR = 0xCC263042;
    private static final int TOP_SHEEN_COLOR = 0x22FFFFFF;
    private static final int TRACK_COLOR = 0xFF171E2B;
    private static final int TRACK_BORDER_COLOR = 0x66090C12;
    private static final int TICK_MARK_COLOR = 0x6656657E;
    private static final int SUBTEXT_COLOR = 0xFFB0BED1;
    private static final int METRIC_COLOR = 0xFF8EA0B8;
    private static final int VOICE_PILL_BG = 0x771A2333;

    // Semantic state & meter palette
    private static final int COLOR_HIDDEN = 0xFF34D399;
    private static final int COLOR_HIDDEN_SHADE = 0xFF10B981;
    private static final int COLOR_CAUTION = 0xFFFBBF24;
    private static final int COLOR_CAUTION_SHADE = 0xFFD97706;
    private static final int COLOR_EXPOSED = 0xFFF87171;
    private static final int COLOR_EXPOSED_SHADE = 0xFFDC2626;
    private static final int COLOR_LIGHT = 0xFFFACC15;
    private static final int COLOR_LIGHT_SHADE = 0xFFCA8A04;
    private static final int COLOR_SCENT = 0xFFEF4444;
    private static final int COLOR_SCENT_SHADE = 0xFFB91C1C;
    private static final int COLOR_VOICE = 0xFFFB7185;
    private static final int COLOR_WHISPER = 0xFF38BDF8;
    private static final int COLOR_GLINT = 0xFFFFF5F5;

    // 9x7 procedural stealth eye glyphs ('.' = empty, '#' = lid/frame, 'o' = iris, '*' = bright pupil)
    private static final String[] EYE_HIDDEN = {
            ".........",
            ".........",
            "..#####..",
            "#########",
            "..#.#.#..",
            ".........",
            "........."
    };
    private static final String[] EYE_CAUTION = {
            ".........",
            "..#####..",
            ".#.....#.",
            "#..o*o..#",
            ".#.....#.",
            "..#####..",
            "........."
    };
    private static final String[] EYE_EXPOSED = {
            "..#####..",
            ".#.....#.",
            "#..ooo..#",
            "#..o*o..#",
            "#..ooo..#",
            ".#.....#.",
            "..#####.."
    };

    private static final Field OPTIONS_HIDE_GUI = findBooleanField(Options.class, "hideGui", "field_1842", "f_92062_");
    private static final Field OPTIONS_RENDER_DEBUG = findBooleanField(Options.class, "renderDebug", "field_1866", "f_92063_");
    private static final Method MC_GET_DEBUG_OVERLAY = findNoArgMethod(Minecraft.class, "getDebugOverlay", "method_53526", "m_293199_");
    private static final Method DEBUG_SHOW_SCREEN = MC_GET_DEBUG_OVERLAY != null
            ? findNoArgMethod(MC_GET_DEBUG_OVERLAY.getReturnType(), "showDebugScreen", "method_53536", "m_294539_")
            : null;

    // Client-side tracking for dedicated servers (landing & firearm impulses)
    private static boolean wasOnGround = true;
    private static double airborneFallDist;
    private static String lastClientGunKey = "";
    private static int lastClientAmmo = -1;

    private StealthHudOverlay() {
    }

    private static Field findBooleanField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field f = owner.getField(name);
                if (f.getType() == boolean.class) {
                    return f;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> owner, String... names) {
        if (owner == null) {
            return null;
        }
        for (String name : names) {
            try {
                return owner.getMethod(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean shouldSuppressOverlay(Minecraft mc) {
        if (mc.options != null && OPTIONS_HIDE_GUI != null) {
            try {
                if (OPTIONS_HIDE_GUI.getBoolean(mc.options)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (MC_GET_DEBUG_OVERLAY != null && DEBUG_SHOW_SCREEN != null) {
            try {
                Object dbg = MC_GET_DEBUG_OVERLAY.invoke(mc);
                if (dbg != null && Boolean.TRUE.equals(DEBUG_SHOW_SCREEN.invoke(dbg))) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        } else if (mc.options != null && OPTIONS_RENDER_DEBUG != null) {
            try {
                if (OPTIONS_RENDER_DEBUG.getBoolean(mc.options)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * Samples the local player's posture, surface block, weather/gear muffling, ambient/held light,
     * health, and voice level, updates {@link MobSenses#hud()}, and draws the HUD onto {@code canvas}.
     */
    public static void render(Canvas canvas) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || shouldSuppressOverlay(mc)) {
            return;
        }
        ClientSettings cfg = MobSenses.clientSettings();
        if (cfg.file() == null && mc.gameDirectory != null) {
            MobSenses.initClient(mc.gameDirectory.toPath().resolve("config"));
            cfg = MobSenses.clientSettings();
        }
        cfg.pollReload(System.currentTimeMillis());

        StealthHudState hud = MobSenses.hud();
        if (!hud.enabled() || !cfg.enabled()) {
            return;
        }
        if (MobSenses.serverLink().connected() && !MobSenses.serverLink().allowClientHud()) {
            return;
        }
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            MobSenses.setLocalPlayerId(null);
            return;
        }
        MobSenses.setLocalPlayerId(player.getUUID());
        if (player.isSpectator()) {
            return;
        }
        samplePlayer(player, level, hud, mc.getSingleplayerServer() == null);
        drawHud(canvas, hud, cfg);
    }

    public static void samplePlayer(LocalPlayer player, Level level, StealthHudState hud) {
        samplePlayer(player, level, hud, false);
    }

    public static void samplePlayer(LocalPlayer player, Level level, StealthHudState hud, boolean remoteServer) {
        long now = System.currentTimeMillis();
        BlockPos pos = player.blockPosition();
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
        String mainHandId = ServerHearing.itemId(player.getMainHandItem());
        String offHandId = ServerHearing.itemId(player.getOffhandItem());
        int heldLight = MobSenses.settings().heldLightEnabled()
                ? Math.max(SightModel.heldItemLight(mainHandId), SightModel.heldItemLight(offHandId))
                : 0;
        boolean sneaking = player.isShiftKeyDown() || player.isCrouching();
        boolean sprinting = player.isSprinting();
        boolean onGround = Worlds.isOnGround(player);
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        boolean moving = onGround && (dx * dx + dz * dz) > 1.0e-4;

        BlockPos below = BlockAcoustics.blockUnderEvent(level, pos);
        BlockState stateBelow = level.getBlockState(below);
        String blockId = BlockAcoustics.blockId(stateBelow);
        AcousticMaterial mat = BlockAcoustics.material(stateBelow, blockId);
        double envMult = MobSenses.settings().weatherMultiplier(level.isRaining(), level.isThundering());
        boolean stealthGear = ServerHearing.hasStealthEnchant(player);
        double stepGearMult = stealthGear
                ? Math.max(0.0, 1.0 - MobSenses.settings().featherFallingMuffling())
                : 1.0;
        double surfaceFactor = NoiseModel.surfaceStepFactor(mat, blockId) * envMult * stepGearMult;

        if (remoteServer) {
            sampleRemoteClientImpulses(player, onGround, envMult, stepGearMult, surfaceFactor, hud, now);
        } else {
            wasOnGround = onGround;
            airborneFallDist = onGround ? 0.0 : Math.max(airborneFallDist, player.fallDistance);
        }

        double voiceLevel = MobSenses.voice().clientVoiceLevel(now);
        double voiceRange = MobSenses.voice().clientVoiceRangeBlocks(now);
        boolean whispering = MobSenses.voice().clientWhispering(now);
        hud.update(
                blockLight, skyLight, heldLight,
                sneaking, sprinting, moving,
                surfaceFactor,
                player.getHealth(), player.getMaxHealth(),
                voiceLevel, now,
                player.isInvisible(), whispering, voiceRange);
    }

    private static void sampleRemoteClientImpulses(
            LocalPlayer player,
            boolean onGround,
            double envMult,
            double stepGearMult,
            double surfaceFactor,
            StealthHudState hud,
            long now) {
        double currentFall = player.fallDistance;
        if (!onGround) {
            airborneFallDist = Math.max(airborneFallDist, currentFall);
        } else if (!wasOnGround && airborneFallDist > 1.25) {
            double landRange = Math.min(22.0, (5.5 + airborneFallDist * 2.1) * Math.max(0.35, surfaceFactor));
            hud.onLocalNoise(landRange, now);
            airborneFallDist = 0.0;
        } else {
            airborneFallDist = 0.0;
        }
        wasOnGround = onGround;

        ItemStack gun = findClientFirearm(player);
        if (gun == null) {
            lastClientGunKey = "";
            lastClientAmmo = -1;
            return;
        }
        String fId = ServerHearing.itemId(gun);
        String tag = ServerHearing.itemTagSummary(gun);
        String gunKey = fId + "|" + ServerHearing.extractGunId(tag);
        int ammo = ServerHearing.extractFirearmAmmo(tag);
        if (gunKey.equals(lastClientGunKey) && lastClientAmmo >= 0 && ammo >= 0 && ammo < lastClientAmmo) {
            boolean silenced = NoiseModel.isSilencedFirearm(fId, tag);
            double shotRange = (silenced
                    ? MobSenses.settings().gunshotSilencedRange()
                    : MobSenses.settings().gunshotRange()) * envMult;
            if (shotRange > 0.0) {
                hud.onLocalNoise(shotRange, now);
            }
        }
        lastClientGunKey = gunKey;
        lastClientAmmo = ammo;
    }

    private static ItemStack findClientFirearm(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (NoiseModel.isFirearmItem(ServerHearing.itemId(main))) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (NoiseModel.isFirearmItem(ServerHearing.itemId(off))) {
            return off;
        }
        return null;
    }

    private static boolean previewClamp;

    public static void drawHud(Canvas canvas, StealthHudState hud) {
        drawHud(canvas, hud, MobSenses.clientSettings());
    }

    public static void drawHud(Canvas canvas, StealthHudState hud, ClientSettings cfg) {
        long now = System.currentTimeMillis();
        double fade = hud.visibilityAlpha(cfg.autoHide(), now);
        if (fade * (cfg.opacity() / 100.0) < 0.04) {
            return;
        }
        if (cfg.style() == ClientSettings.Style.MINIMAL) {
            drawMinimalHud(canvas, hud, cfg, fade);
        } else {
            drawTacticalHud(canvas, hud, cfg, fade);
        }
    }

    /**
     * Renders a live, interactive preview of the Stealth HUD inside the Settings GUI viewport box
     * {@code [boxX, boxY, boxX + boxW, boxY + boxH]}, responding immediately to Style, Density,
     * Screen Anchor, Opacity, Offset X/Y, Audible Range, and individual sense bar toggles.
     */
    public static void renderPreview(
            Canvas parent,
            int boxX,
            int boxY,
            int boxW,
            int boxH,
            StealthHudState hud,
            ClientSettings cfg) {
        if (parent == null || hud == null || cfg == null || boxW <= 20 || boxH <= 16) {
            return;
        }
        Canvas sub = new Canvas() {
            @Override
            public int width() {
                return boxW;
            }

            @Override
            public int height() {
                return boxH;
            }

            @Override
            public int textWidth(Component text) {
                return parent.textWidth(text);
            }

            @Override
            public void fill(int x1, int y1, int x2, int y2, int argb) {
                int cx1 = Math.max(1, x1);
                int cy1 = Math.max(1, y1);
                int cx2 = Math.min(boxW - 1, x2);
                int cy2 = Math.min(boxH - 1, y2);
                if (cx2 > cx1 && cy2 > cy1) {
                    parent.fill(boxX + cx1, boxY + cy1, boxX + cx2, boxY + cy2, argb);
                }
            }

            @Override
            public void text(Component text, int x, int y, int argb) {
                if (x >= 1 && y >= 1 && y + 8 <= boxH - 1) {
                    parent.text(text, boxX + x, boxY + y, argb);
                }
            }
        };
        double fade = cfg.enabled() ? 1.0 : 0.30;
        previewClamp = true;
        try {
            if (cfg.style() == ClientSettings.Style.MINIMAL) {
                drawMinimalHud(sub, hud, cfg, fade);
            } else {
                drawTacticalHud(sub, hud, cfg, fade);
            }
        } finally {
            previewClamp = false;
        }
    }

    private static int resolveX(Canvas canvas, ClientSettings cfg, int panelWidth) {
        if (!previewClamp) {
            return cfg.computeX(canvas.width(), panelWidth);
        }
        int screenW = canvas.width();
        int ox = cfg.x() / 5;
        int base;
        switch (cfg.position()) {
            case TOP_RIGHT:
            case BOTTOM_RIGHT:
                base = screenW - panelWidth - 6;
                break;
            case TOP_CENTER:
            case HOTBAR:
                base = (screenW - panelWidth) / 2;
                break;
            case TOP_LEFT:
            case BOTTOM_LEFT:
            default:
                base = 6;
                break;
        }
        return Math.max(4, Math.min(Math.max(4, screenW - panelWidth - 4), base + ox));
    }

    private static int resolveY(Canvas canvas, ClientSettings cfg, int panelHeight) {
        if (!previewClamp) {
            return cfg.computeY(canvas.height(), panelHeight);
        }
        int screenH = canvas.height();
        int oy = cfg.y() / 5;
        int base;
        switch (cfg.position()) {
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT:
                base = screenH - panelHeight - 5;
                break;
            case HOTBAR:
                base = screenH - panelHeight - 9;
                break;
            case TOP_CENTER:
            case TOP_LEFT:
            case TOP_RIGHT:
            default:
                base = 5;
                break;
        }
        return Math.max(3, Math.min(Math.max(3, screenH - panelHeight - 3), base + oy));
    }

    private static void drawTacticalHud(Canvas canvas, StealthHudState hud, ClientSettings cfg, double fade) {
        boolean compact = cfg.compact();
        boolean drawNoise = !compact && cfg.showNoise();
        boolean drawLight = !compact && cfg.showLight();
        boolean drawScent = !compact && cfg.showScent() && hud.scentActive();

        int exposure = hud.overallExposure();
        int statePrimary = exposure >= 65 ? COLOR_EXPOSED : (exposure >= 30 ? COLOR_CAUTION : COLOR_HIDDEN);

        Component statusText = Worlds.translatable(hud.statusKey());
        int statusW = canvas.textWidth(statusText);

        boolean showVoiceBadge = hud.voiceActive() && cfg.showVoice();
        Component voiceText = showVoiceBadge
                ? Worlds.translatable(hud.whispering() ? "gui.mobsenses.hud.whisper" : "gui.mobsenses.hud.voice")
                : null;
        int voiceBadgeW = voiceText != null ? canvas.textWidth(voiceText) + 6 : 0;

        int rangeBlocks = hud.audibleRangeBlocks();
        boolean showRangeTag = cfg.showRange() && rangeBlocks > 0;
        Component rangeText = showRangeTag ? Worlds.literal("~" + rangeBlocks + "m") : null;
        int rangeW = rangeText != null ? canvas.textWidth(rangeText) : 0;

        Component noiseLabel = drawNoise ? Worlds.translatable("gui.mobsenses.hud.noise") : null;
        Component lightLabel = drawLight ? Worlds.translatable("gui.mobsenses.hud.light") : null;
        Component scentLabel = drawScent ? Worlds.translatable("gui.mobsenses.hud.scent") : null;

        int maxLabelW = 0;
        if (noiseLabel != null) {
            maxLabelW = Math.max(maxLabelW, canvas.textWidth(noiseLabel));
        }
        if (lightLabel != null) {
            maxLabelW = Math.max(maxLabelW, canvas.textWidth(lightLabel));
        }
        if (scentLabel != null) {
            maxLabelW = Math.max(maxLabelW, canvas.textWidth(scentLabel));
        }
        int labelColW = Math.max(32, maxLabelW + 6);

        int headerWidth = 19 + statusW
                + (rangeW > 0 ? rangeW + 6 : 0)
                + (voiceBadgeW > 0 ? voiceBadgeW + 5 : 0)
                + 6;
        int rows = (drawNoise ? 1 : 0) + (drawLight ? 1 : 0) + (drawScent ? 1 : 0);
        int minBodyWidth = rows > 0 ? (7 + labelColW + 64 + 6) : 0;
        int panelWidth = Math.max(compact ? 102 : 126, Math.max(headerWidth, minBodyWidth));
        int panelHeight = rows == 0 ? 17 : (19 + rows * 11 + 2);

        int x = resolveX(canvas, cfg, panelWidth);
        int y = resolveY(canvas, cfg, panelHeight);

        drawChamferedPanel(canvas, cfg, fade, x, y, panelWidth, panelHeight, statePrimary);

        // Header: procedural Stealth Eye glyph + status title + optional ~Xm range & [Voice]/[Whisper] pill
        drawStealthEye(canvas, cfg, fade, x + 6, y + 5, exposure);
        canvas.text(statusText, x + 18, y + 5, col(cfg, statePrimary, fade));

        int rightCursor = x + panelWidth - 5;
        if (voiceText != null) {
            int pillX1 = rightCursor - voiceBadgeW;
            int pillY1 = y + 3;
            canvas.fill(pillX1, pillY1, rightCursor, pillY1 + 11, col(cfg, VOICE_PILL_BG, fade));
            int vColor = hud.whispering() ? COLOR_WHISPER : COLOR_VOICE;
            canvas.fill(pillX1, pillY1, pillX1 + 1, pillY1 + 11, col(cfg, vColor, fade));
            canvas.text(voiceText, pillX1 + 3, y + 5, col(cfg, vColor, fade));
            rightCursor = pillX1 - 5;
        }
        if (rangeText != null) {
            int rColor = hud.noisePercent() >= 65 ? COLOR_EXPOSED
                    : (hud.noisePercent() >= 30 ? COLOR_CAUTION : METRIC_COLOR);
            canvas.text(rangeText, rightCursor - rangeW, y + 5, col(cfg, rColor, fade));
        }

        if (rows == 0) {
            return;
        }

        // Subtle divider below header
        canvas.fill(x + 6, y + 16, x + panelWidth - 5, y + 17, col(cfg, 0x442A3548, fade));

        int rowY = y + 19;
        int rowX = x + 7;
        int contentW = panelWidth - 13;

        if (drawNoise) {
            int noisePct = hud.noisePercent();
            int nTop = noisePct >= 65 ? COLOR_EXPOSED : (noisePct >= 30 ? COLOR_CAUTION : COLOR_HIDDEN);
            int nBot = noisePct >= 65 ? COLOR_EXPOSED_SHADE : (noisePct >= 30 ? COLOR_CAUTION_SHADE : COLOR_HIDDEN_SHADE);
            drawMeterRow(canvas, cfg, fade, rowX, rowY, contentW, labelColW,
                    noiseLabel, noisePct, hud.peakHoldPercent(), nTop, nBot, true);
            rowY += 11;
        }
        if (drawLight) {
            drawMeterRow(canvas, cfg, fade, rowX, rowY, contentW, labelColW,
                    lightLabel, hud.lightPercent(), 0, COLOR_LIGHT, COLOR_LIGHT_SHADE, true);
            rowY += 11;
        }
        if (drawScent) {
            drawMeterRow(canvas, cfg, fade, rowX, rowY, contentW, labelColW,
                    scentLabel, hud.scentPercent(), 0, COLOR_SCENT, COLOR_SCENT_SHADE, false);
        }
    }

    private static void drawMinimalHud(Canvas canvas, StealthHudState hud, ClientSettings cfg, double fade) {
        int exposure = hud.overallExposure();
        int primary = exposure >= 65 ? COLOR_EXPOSED : (exposure >= 30 ? COLOR_CAUTION : COLOR_HIDDEN);
        int shade = exposure >= 65 ? COLOR_EXPOSED_SHADE : (exposure >= 30 ? COLOR_CAUTION_SHADE : COLOR_HIDDEN_SHADE);

        boolean showScentPip = cfg.showScent() && hud.scentActive();
        boolean showVoicePip = cfg.showVoice() && hud.voiceActive();
        int extraPipsW = (showScentPip ? 5 : 0) + (showVoicePip ? 5 : 0);

        int barW = 64;
        int panelWidth = 18 + barW + extraPipsW + 5;
        int panelHeight = 13;

        int x = resolveX(canvas, cfg, panelWidth);
        int y = resolveY(canvas, cfg, panelHeight);

        drawChamferedPanel(canvas, cfg, fade, x, y, panelWidth, panelHeight, primary);
        drawStealthEye(canvas, cfg, fade, x + 5, y + 3, exposure);

        int barX = x + 17;
        int barY = y + 4;
        drawBarTrack(canvas, cfg, fade, barX, barY, barW, 5, exposure, hud.peakHoldPercent(), primary, shade, true);

        int pipX = barX + barW + 3;
        if (showScentPip) {
            canvas.fill(pipX, y + 4, pipX + 3, y + 9, col(cfg, COLOR_SCENT, fade));
            pipX += 5;
        }
        if (showVoicePip) {
            int vCol = hud.whispering() ? COLOR_WHISPER : COLOR_VOICE;
            canvas.fill(pipX, y + 4, pipX + 3, y + 9, col(cfg, vCol, fade));
        }
    }

    private static void drawChamferedPanel(
            Canvas canvas, ClientSettings cfg, double fade,
            int x, int y, int w, int h, int spineColor) {
        int border = col(cfg, BORDER_COLOR, fade);
        canvas.fill(x, y - 1, x + w, y, border);
        canvas.fill(x, y + h, x + w, y + h + 1, border);
        canvas.fill(x - 1, y, x, y + h, border);
        canvas.fill(x + w, y, x + w + 1, y + h, border);

        canvas.fill(x, y, x + w, y + h, col(cfg, BG_COLOR, fade));
        canvas.fill(x + 2, y, x + w, y + 1, col(cfg, TOP_SHEEN_COLOR, fade));
        canvas.fill(x, y, x + 2, y + h, col(cfg, spineColor, fade));
    }

    private static void drawStealthEye(
            Canvas canvas, ClientSettings cfg, double fade,
            int x, int y, int exposure) {
        String[] glyph = exposure >= 65 ? EYE_EXPOSED : (exposure >= 30 ? EYE_CAUTION : EYE_HIDDEN);
        int lidColor = col(cfg, exposure >= 65 ? COLOR_EXPOSED : (exposure >= 30 ? COLOR_CAUTION : COLOR_HIDDEN), fade);
        int irisColor = col(cfg, exposure >= 65 ? COLOR_EXPOSED_SHADE : COLOR_CAUTION_SHADE, fade);
        int glintColor = col(cfg, exposure >= 65 ? COLOR_GLINT : COLOR_CAUTION, fade);

        for (int row = 0; row < glyph.length; row++) {
            String line = glyph[row];
            int colIdx = 0;
            while (colIdx < line.length()) {
                char ch = line.charAt(colIdx);
                if (ch == '.') {
                    colIdx++;
                    continue;
                }
                int start = colIdx;
                while (colIdx < line.length() && line.charAt(colIdx) == ch) {
                    colIdx++;
                }
                int pixelColor = ch == '*' ? glintColor : (ch == 'o' ? irisColor : lidColor);
                canvas.fill(x + start, y + row, x + colIdx, y + row + 1, pixelColor);
            }
        }
    }

    private static void drawMeterRow(
            Canvas canvas, ClientSettings cfg, double fade,
            int x, int y, int totalWidth, int labelColW,
            Component label, int percent, int peakHoldPct,
            int topColor, int bottomColor, boolean drawThresholds) {
        canvas.text(label, x, y, col(cfg, SUBTEXT_COLOR, fade));
        int barX = x + labelColW;
        int barW = Math.max(28, totalWidth - labelColW);
        int barY = y + 2;
        drawBarTrack(canvas, cfg, fade, barX, barY, barW, 5, percent, peakHoldPct, topColor, bottomColor, drawThresholds);
    }

    private static void drawBarTrack(
            Canvas canvas, ClientSettings cfg, double fade,
            int barX, int barY, int barW, int barH,
            int percent, int peakHoldPct,
            int topColor, int bottomColor, boolean drawThresholds) {
        canvas.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, col(cfg, TRACK_BORDER_COLOR, fade));
        canvas.fill(barX, barY, barX + barW, barY + barH, col(cfg, TRACK_COLOR, fade));

        if (drawThresholds) {
            int tick30 = barX + (int) Math.round(barW * 0.30);
            int tick65 = barX + (int) Math.round(barW * 0.65);
            int tickCol = col(cfg, TICK_MARK_COLOR, fade);
            canvas.fill(tick30, barY, tick30 + 1, barY + barH, tickCol);
            canvas.fill(tick65, barY, tick65 + 1, barY + barH, tickCol);
        }

        int clamped = Math.max(0, Math.min(100, percent));
        int fillW = (int) Math.round((clamped / 100.0) * barW);
        if (fillW > 0) {
            int splitH = Math.max(1, barH - 2);
            canvas.fill(barX, barY, barX + fillW, barY + splitH, col(cfg, topColor, fade));
            canvas.fill(barX, barY + splitH, barX + fillW, barY + barH, col(cfg, bottomColor, fade));
        }

        int clampedPeak = Math.max(0, Math.min(100, peakHoldPct));
        if (clampedPeak > clamped + 2) {
            int peakX = barX + Math.min(barW - 1, (int) Math.round((clampedPeak / 100.0) * barW));
            int peakCol = col(cfg, clampedPeak >= 65 ? COLOR_EXPOSED : (clampedPeak >= 30 ? COLOR_CAUTION : COLOR_HIDDEN), fade);
            canvas.fill(peakX, barY - 1, peakX + 1, barY + barH + 1, peakCol);
        }
    }

    private static int col(ClientSettings cfg, int argb, double fade) {
        return cfg.applyAlpha(argb, fade);
    }
}
