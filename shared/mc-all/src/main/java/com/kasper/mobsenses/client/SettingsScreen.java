package com.kasper.mobsenses.client;

import com.kasper.mobsenses.AdminCommands;
import com.kasper.mobsenses.ClientSettings;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.NoiseModel;
import com.kasper.mobsenses.Preset;
import com.kasper.mobsenses.Reply;
import com.kasper.mobsenses.ServerLink;
import com.kasper.mobsenses.ServerSettings;
import com.kasper.mobsenses.SightModel;
import com.kasper.mobsenses.StealthHudState;
import com.kasper.mobsenses.compat.BaseScreen;
import com.kasper.mobsenses.compat.Btn;
import com.kasper.mobsenses.compat.Mc;
import com.kasper.mobsenses.compat.Tip;
import com.kasper.mobsenses.compat.Txt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Single-source 2-tab dark-slate settings screen for Mob Senses (Minecraft 1.16.5 - 26.x).
 * <ol>
 *     <li><b>HUD</b> — Interactive Live Stealth HUD Preview viewport + quick acoustic test-pulse buttons</li>
 *     <li><b>SENSES</b> — Live Telemetry, Acoustic Wall Attenuation & Sight/Scent Summary Card + World & Sense controls</li>
 * </ol>
 */
public abstract class SettingsScreen extends BaseScreen {

    public enum Tab {
        HUD("gui.mobsenses.tab.hud"),
        SENSES("gui.mobsenses.tab.senses");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private static final class ScrollRow {
        final AbstractWidget widget;
        final int baseY;

        ScrollRow(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }
    }

    private static final class SectionHeader {
        final int baseY;
        final Component title;

        SectionHeader(int baseY, Component title) {
            this.baseY = baseY;
            this.title = title;
        }
    }

    private static volatile Tab nextInitialTab;

    protected final Screen parent;
    private Tab currentTab = Tab.HUD;

    private final List<AbstractWidget> allWidgets = new ArrayList<>();
    private final List<ScrollRow> scrollRows = new ArrayList<>();
    private final List<SectionHeader> sectionHeaders = new ArrayList<>();
    private final StealthHudState previewHud = new StealthHudState();

    private int panelX;
    private int panelW;
    private int panelTop;
    private int panelBottom;
    private int tabsY;
    private int bannerY;
    private int bodyTop;
    private int bodyBottom;
    private int footerY;

    private int scrollY;
    private int maxScroll;
    private int contentHeight;

    private int previewStage = 1;
    private long lastPulseTimeMs;
    private String pendingSliderKey;
    private String pendingSliderCommand;
    private int pendingSliderTicks;
    private long lastSliderEditMs;

    private String localToastMessage = "";
    private boolean localToastOk = true;
    private long localToastTimeMs;
    private int observedReplyCount;

    protected SettingsScreen(Screen parent) {
        super(Txt.translatable("gui.mobsenses.title"));
        this.parent = parent;
        Tab requested = nextInitialTab;
        if (requested != null) {
            nextInitialTab = null;
            this.currentTab = requested;
        }
        this.observedReplyCount = MobSenses.serverLink().adminReplyCount();
        triggerPreviewPulse(1);
    }

    /** Selects which tab the next opened {@link SettingsScreen} starts on. */
    public static void openOn(Tab tab) {
        nextInitialTab = tab;
    }

    public Tab currentTab() {
        return currentTab;
    }

    public void selectTab(Tab tab) {
        if (tab != null && tab != this.currentTab) {
            flushPendingSlider();
            this.currentTab = tab;
            this.scrollY = 0;
            rebuildLayout();
        }
    }

    /** Scrolls the active tab directly to its bottom. */
    public void scrollToEnd() {
        if (maxScroll > 0 && scrollY != maxScroll) {
            scrollY = maxScroll;
            layoutScrollRows();
        }
    }

    /** Advances the scroll position by one viewport page, returning {@code true} if moved. */
    public boolean scrollPage() {
        if (maxScroll <= 0 || scrollY >= maxScroll) {
            return false;
        }
        int step = Math.max(36, (bodyBottom - bodyTop) - 24);
        int next = Math.min(maxScroll, scrollY + step);
        if (next == scrollY) {
            return false;
        }
        scrollY = next;
        layoutScrollRows();
        return true;
    }

    public void onLinkUpdated() {
        ServerLink link = MobSenses.serverLink();
        if (link.adminReplyCount() != observedReplyCount) {
            observedReplyCount = link.adminReplyCount();
            localToastMessage = link.lastReplyMessage();
            localToastOk = link.lastReplyOk();
            localToastTimeMs = System.currentTimeMillis();
        }
        if (pendingSliderTicks <= 0 && (System.currentTimeMillis() - lastSliderEditMs) > 400L) {
            rebuildLayout();
        }
    }

    private boolean isRemoteMultiplayer() {
        if (this.minecraft != null && this.minecraft.level != null && this.minecraft.getSingleplayerServer() == null) {
            return true;
        }
        return MobSenses.serverLink().connected();
    }

    private boolean isServerEditable() {
        if (!isRemoteMultiplayer()) {
            return true;
        }
        ServerLink link = MobSenses.serverLink();
        return link.connected() && link.isAdmin();
    }

    private ServerSettings activeServerSettings() {
        if (MobSenses.serverLink().connected()) {
            return MobSenses.serverLink().effectiveSettings();
        }
        return MobSenses.settings();
    }

    private void triggerPreviewPulse(int stage) {
        this.previewStage = stage;
        long now = System.currentTimeMillis();
        this.lastPulseTimeMs = now;
        ServerSettings s = activeServerSettings();
        if (stage == 0) {
            previewHud.update(1, 0, 0, true, false, false, 0.35, 20.0, 20.0, 0.0, now, false, false, 0.0);
            previewHud.syncRemoteMobAlert(StealthHudState.ALERT_CALM, now);
        } else if (stage == 1) {
            double stepR = Math.max(6.0, s.stepRange() * s.hearing());
            previewHud.update(7, 5, 0, false, false, true, 1.0, 16.0, 20.0, 0.40, now, false, true, 5.0);
            previewHud.onLocalNoise(stepR, now);
            previewHud.syncRemoteMobAlert(StealthHudState.ALERT_SUSPICIOUS, now);
        } else if (stage == 2) {
            double silR = Math.max(12.0, s.gunshotSilencedRange() * s.hearing());
            previewHud.update(9, 8, 0, false, false, true, 1.0, 7.0, 20.0, 0.55, now, false, true, 6.0);
            previewHud.onLocalNoise(silR, now);
            previewHud.syncRemoteMobAlert(StealthHudState.ALERT_SEARCHING, now);
        } else {
            double gunR = Math.max(28.0, s.gunshotRange() * s.hearing());
            previewHud.update(13, 12, 14, false, true, true, 1.4, 5.0, 20.0, 0.92, now, false, false, s.voiceRange());
            previewHud.onLocalNoise(gunR, now);
            previewHud.syncRemoteMobAlert(StealthHudState.ALERT_HUNTED, now);
        }
        MobSenses.hud().triggerPreview(now);
    }

    @Override
    protected void init() {
        if (this.minecraft != null && MobSenses.clientSettings().file() == null && this.minecraft.gameDirectory != null) {
            MobSenses.initClient(this.minecraft.gameDirectory.toPath().resolve("config"));
        }
        allWidgets.clear();
        scrollRows.clear();
        sectionHeaders.clear();

        panelW = Math.min(360, Math.max(264, this.width - 16));
        panelX = (this.width - panelW) / 2;
        panelTop = Math.max(6, Math.min(12, (this.height - 224) / 4));
        panelBottom = this.height - Math.max(6, panelTop);

        tabsY = panelTop + 26;
        bannerY = tabsY + 21;

        boolean hasBanner = hasActiveBanner();
        bodyTop = hasBanner ? (bannerY + 19) : (tabsY + 22);
        footerY = panelBottom - 24;
        bodyBottom = footerY - 18;

        int innerX = panelX + 8;
        int innerW = panelW - 16;

        // 2 Tabs: Stealth HUD | Senses & World
        Tab[] tabs = Tab.values();
        int tabGap = 4;
        int tabW = (innerW - tabGap * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            final Tab tab = tabs[i];
            int tx = innerX + i * (tabW + tabGap);
            int tw = (i == tabs.length - 1) ? (innerX + innerW - tx) : tabW;
            Component label = Txt.translatable(tab.key)
                    .withStyle(currentTab == tab ? ChatFormatting.AQUA : ChatFormatting.GRAY);
            Button tabBtn = Btn.create(tx, tabsY, tw, 18, label, () -> selectTab(tab), null);
            tabBtn.active = currentTab != tab;
            registerFixed(tabBtn);
        }

        int cursorY = bodyTop + 4;
        if (currentTab == Tab.HUD) {
            cursorY = buildHudTab(innerX, innerW, cursorY);
        } else {
            cursorY = buildSensesTab(innerX, innerW, cursorY);
        }

        contentHeight = Math.max(0, cursorY - bodyTop + 4);
        int viewportH = Math.max(1, bodyBottom - bodyTop);
        maxScroll = Math.max(0, contentHeight - viewportH);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        buildFooter(innerX, innerW);
        layoutScrollRows();
    }

    private boolean hasActiveBanner() {
        if (currentTab == Tab.HUD) {
            return MobSenses.serverLink().connected() && !MobSenses.serverLink().allowClientHud();
        }
        return true;
    }

    private int buildHudTab(int x, int w, int y) {
        ClientSettings cfg = MobSenses.clientSettings();
        int colGap = 6;
        int colW = (w - colGap) / 2;
        int rightX = x + colW + colGap;
        int rightW = w - colW - colGap;

        // Space reserved for Live Stealth HUD Preview Card (drawn in paintHudPreviewCard)
        y += 64;

        // Interactive Test-Pulse Row (Quiet / Step / Silenced / Gunshot)
        String[] pulseLabels = {"Quiet", "Step", "Silenced", "Gunshot"};
        ChatFormatting[] pulseColors = {ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.AQUA, ChatFormatting.RED};
        int pGap = 4;
        int pw = (w - pGap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            final int stage = i;
            int px = x + i * (pw + pGap);
            int curW = (i == 3) ? (x + w - px) : pw;
            boolean activeStage = previewStage == stage;
            MutableComponent lbl = (activeStage ? Txt.literal("▸ ") : Txt.empty())
                    .append(Txt.literal(pulseLabels[i]))
                    .withStyle(activeStage ? pulseColors[i] : ChatFormatting.GRAY);
            addScrollButton(px, y, curW, 16, lbl, "gui.mobsenses.hud.preview.tip", () -> {
                triggerPreviewPulse(stage);
                rebuildLayout();
            }, true);
        }
        y += 21;

        // Row 1: Enable Stealth HUD | Show Audible Range (m)
        addScrollButton(x, y, colW, 18,
                toggleLabel("gui.mobsenses.hud.enabled", cfg.enabled()),
                "gui.mobsenses.hud.enabled.tip",
                () -> {
                    cfg.setEnabled(!cfg.enabled());
                    cfg.save();
                    MobSenses.hud().setEnabled(cfg.enabled());
                    rebuildLayout();
                }, true);
        addScrollButton(rightX, y, rightW, 18,
                toggleLabel("gui.mobsenses.hud.show_range", cfg.showRange()),
                "gui.mobsenses.hud.show_range.tip",
                () -> {
                    cfg.setShowRange(!cfg.showRange());
                    cfg.save();
                    rebuildLayout();
                }, true);
        y += 21;

        // Row 2: Style (Tactical / Minimal) | Density (Full / Compact)
        Component styleVal = Txt.translatable(cfg.style() == ClientSettings.Style.MINIMAL
                ? "gui.mobsenses.hud.style.minimal"
                : "gui.mobsenses.hud.style.tactical").withStyle(ChatFormatting.YELLOW);
        addScrollButton(x, y, colW, 18,
                kvLabel("gui.mobsenses.hud.style", styleVal),
                "gui.mobsenses.hud.style.tip",
                () -> {
                    cfg.setStyle(cfg.style() == ClientSettings.Style.TACTICAL
                            ? ClientSettings.Style.MINIMAL
                            : ClientSettings.Style.TACTICAL);
                    cfg.save();
                    rebuildLayout();
                }, true);

        Component layoutVal = Txt.translatable(cfg.compact()
                ? "gui.mobsenses.hud.layout.compact"
                : "gui.mobsenses.hud.layout.full").withStyle(ChatFormatting.YELLOW);
        addScrollButton(rightX, y, rightW, 18,
                kvLabel("gui.mobsenses.hud.layout", layoutVal),
                "gui.mobsenses.hud.layout.tip",
                () -> {
                    cfg.setCompact(!cfg.compact());
                    cfg.save();
                    rebuildLayout();
                }, cfg.style() == ClientSettings.Style.TACTICAL);
        y += 21;

        // Row 3: Screen Anchor | Auto-Hide
        Component anchorVal = Txt.translatable("gui.mobsenses.hud.position." + cfg.position().id())
                .withStyle(ChatFormatting.AQUA);
        addScrollButton(x, y, colW, 18,
                kvLabel("gui.mobsenses.hud.position", anchorVal),
                "gui.mobsenses.hud.position.tip",
                () -> {
                    ClientSettings.Anchor[] anchors = ClientSettings.Anchor.values();
                    ClientSettings.Anchor next = anchors[(cfg.position().ordinal() + 1) % anchors.length];
                    cfg.setPosition(next);
                    cfg.save();
                    rebuildLayout();
                }, true);

        addScrollButton(rightX, y, rightW, 18,
                toggleLabel("gui.mobsenses.hud.auto_hide", cfg.autoHide()),
                "gui.mobsenses.hud.auto_hide.tip",
                () -> {
                    cfg.setAutoHide(!cfg.autoHide());
                    cfg.save();
                    rebuildLayout();
                }, true);
        y += 21;

        // Row 4: Opacity slider (full width)
        addScrollSlider(x, y, w, 18,
                "gui.mobsenses.hud.opacity", "gui.mobsenses.hud.opacity.tip",
                cfg.opacity(), 10, 100, 1,
                v -> Math.round(v) + "%",
                v -> {
                    cfg.setOpacity((int) Math.round(v));
                    cfg.save();
                }, true);
        y += 21;

        // Row 5: Offset X | Offset Y (-500..500 px)
        addScrollSlider(x, y, colW, 18,
                "gui.mobsenses.hud.offset_x", "gui.mobsenses.hud.offset_x.tip",
                cfg.x(), -500, 500, 1,
                v -> Math.round(v) + " px",
                v -> {
                    cfg.setX((int) Math.round(v));
                    cfg.save();
                }, true);

        addScrollSlider(rightX, y, rightW, 18,
                "gui.mobsenses.hud.offset_y", "gui.mobsenses.hud.offset_y.tip",
                cfg.y(), -500, 500, 1,
                v -> Math.round(v) + " px",
                v -> {
                    cfg.setY((int) Math.round(v));
                    cfg.save();
                }, true);
        y += 23;

        // Visible Sense Bars section
        sectionHeaders.add(new SectionHeader(y + 1, Txt.translatable("gui.mobsenses.hud.bars")));
        y += 13;

        addScrollButton(x, y, colW, 18,
                toggleLabel("gui.mobsenses.hud.bars.noise", cfg.showNoise()),
                "gui.mobsenses.hud.bars.tip",
                () -> {
                    cfg.setShowNoise(!cfg.showNoise());
                    cfg.save();
                    rebuildLayout();
                }, true);
        addScrollButton(rightX, y, rightW, 18,
                toggleLabel("gui.mobsenses.hud.bars.light", cfg.showLight()),
                "gui.mobsenses.hud.bars.tip",
                () -> {
                    cfg.setShowLight(!cfg.showLight());
                    cfg.save();
                    rebuildLayout();
                }, true);
        y += 21;

        addScrollButton(x, y, colW, 18,
                toggleLabel("gui.mobsenses.hud.bars.scent", cfg.showScent()),
                "gui.mobsenses.hud.bars.tip",
                () -> {
                    cfg.setShowScent(!cfg.showScent());
                    cfg.save();
                    rebuildLayout();
                }, true);
        addScrollButton(rightX, y, rightW, 18,
                toggleLabel("gui.mobsenses.hud.bars.voice", cfg.showVoice()),
                "gui.mobsenses.hud.bars.tip",
                () -> {
                    cfg.setShowVoice(!cfg.showVoice());
                    cfg.save();
                    rebuildLayout();
                }, true);
        y += 22;

        return y;
    }

    private int buildSensesTab(int x, int w, int y) {
        ServerSettings s = activeServerSettings();
        boolean editable = isServerEditable();
        int colGap = 6;
        int colW = (w - colGap) / 2;
        int rightX = x + colW + colGap;
        int rightW = w - colW - colGap;

        // Space reserved for Top Live Telemetry & Senses Summary Card (drawn in paintSensesSummaryCard)
        y += 116;

        // 1. Global Sense Preset (Off / Subtle / Default / Hardcore)
        sectionHeaders.add(new SectionHeader(y + 1, Txt.translatable("gui.mobsenses.hearing.preset")));
        y += 13;
        Preset[] presets = Preset.values();
        int pGap = 4;
        int pw = (w - pGap * (presets.length - 1)) / presets.length;
        for (int i = 0; i < presets.length; i++) {
            final Preset p = presets[i];
            int px = x + i * (pw + pGap);
            int curW = (i == presets.length - 1) ? (x + w - px) : pw;
            boolean selected = s.preset() == p;
            MutableComponent label = (selected ? Txt.literal("● ") : Txt.empty())
                    .append(Txt.translatable("command.mobsenses.preset." + p.id()))
                    .withStyle(selected ? ChatFormatting.AQUA : ChatFormatting.WHITE);
            addScrollButton(px, y, curW, 18, label, "gui.mobsenses.hearing.preset.tip",
                    () -> applyAdminImmediate("preset " + p.id()), editable);
        }
        y += 23;

        // 2. Hearing, Walls, Firearms, Actions & Voice Chat
        sectionHeaders.add(new SectionHeader(y + 1, Txt.translatable("gui.mobsenses.tab.hearing")));
        y += 13;

        addServerSlider(x, y, colW, "gui.mobsenses.hearing.hearing", "gui.mobsenses.hearing.hearing.tip",
                s.hearing(), 0.0, 3.0, 0.05, SettingsScreen::fmtMult, "hearing", editable);
        addServerSlider(rightX, y, rightW, "gui.mobsenses.hearing.wall_muffling", "gui.mobsenses.hearing.wall_muffling.tip",
                s.wallMuffling(), 0.0, 3.0, 0.05, SettingsScreen::fmtMult, "wall_muffling", editable);
        y += 21;

        addServerToggle(x, y, colW, "gui.mobsenses.hearing.through_wall", "gui.mobsenses.hearing.through_wall.tip",
                s.throughWallHearing(), "through_wall_hearing", editable);
        addServerToggle(rightX, y, rightW, "gui.mobsenses.hearing.surface_scaling", "gui.mobsenses.hearing.surface_scaling.tip",
                s.surfaceSoundScaling(), "surface_sound_scaling", editable);
        y += 21;

        addServerToggle(x, y, colW, "gui.mobsenses.hearing.sound_cover", "gui.mobsenses.hearing.sound_cover.tip",
                s.soundCoverEnabled(), "sound_cover_enabled", editable);
        addServerToggle(rightX, y, rightW, "gui.mobsenses.hearing.decoy_throw", "gui.mobsenses.hearing.decoy_throw.tip",
                s.decoyThrowEnabled(), "decoy_throw_enabled", editable);
        y += 21;

        addServerSlider(x, y, colW, "gui.mobsenses.hearing.rain_muffling", "gui.mobsenses.hearing.rain_muffling.tip",
                s.rainMuffling(), 0.0, 1.00, 0.05, SettingsScreen::fmtPct01, "rain_muffling", editable);
        addServerSlider(rightX, y, rightW, "gui.mobsenses.hearing.feather_falling", "gui.mobsenses.hearing.feather_falling.tip",
                s.featherFallingMuffling(), 0.0, 1.00, 0.05, SettingsScreen::fmtPct01, "feather_falling_muffling", editable);
        y += 21;

        addServerSlider(x, y, colW, "gui.mobsenses.hearing.gunshot_range", "gui.mobsenses.hearing.gunshot_range.tip",
                s.gunshotRange(), 0.0, 160.0, 1.0, SettingsScreen::fmtBlocks, "gunshot_range", editable);
        addServerSlider(rightX, y, rightW, "gui.mobsenses.hearing.gunshot_silenced_range", "gui.mobsenses.hearing.gunshot_silenced_range.tip",
                s.gunshotSilencedRange(), 0.0, 80.0, 1.0, SettingsScreen::fmtBlocks, "gunshot_silenced_range", editable);
        y += 21;

        addServerSlider(x, y, colW, "gui.mobsenses.hearing.bullet_impact_range", "gui.mobsenses.hearing.bullet_impact_range.tip",
                s.bulletImpactRange(), 0.0, 80.0, 1.0, SettingsScreen::fmtBlocks, "bullet_impact_range", editable);
        addServerSlider(rightX, y, rightW, "gui.mobsenses.hearing.step_range", "gui.mobsenses.hearing.step_range.tip",
                s.stepRange(), 0.0, 32.0, 0.5, SettingsScreen::fmtBlocksHalf, "step_range", editable);
        y += 21;

        addServerSlider(x, y, colW, "gui.mobsenses.hearing.block_break_range", "gui.mobsenses.hearing.block_break_range.tip",
                s.blockBreakRange(), 0.0, 48.0, 1.0, SettingsScreen::fmtBlocks, "block_break_range", editable);
        addServerSlider(rightX, y, rightW, "gui.mobsenses.hearing.door_range", "gui.mobsenses.hearing.door_range.tip",
                s.doorRange(), 0.0, 32.0, 1.0, SettingsScreen::fmtBlocks, "door_range", editable);
        y += 21;

        addServerToggle(x, y, colW, "gui.mobsenses.hearing.voice", "gui.mobsenses.hearing.voice.tip",
                s.voice(), "voice", editable);
        addServerSlider(rightX, y, rightW, "gui.mobsenses.hearing.voice_range", "gui.mobsenses.hearing.voice_range.tip",
                s.voiceRange(), 0.0, 48.0, 1.0, SettingsScreen::fmtBlocks, "voice_range", editable);
        y += 21;

        addServerSlider(x, y, w, "gui.mobsenses.hearing.whisper_multiplier", "gui.mobsenses.hearing.whisper_multiplier.tip",
                s.voiceWhisperMultiplier(), 0.0, 1.00, 0.05, SettingsScreen::fmtPct01, "voice_whisper_multiplier", editable);
        y += 23;

        // 3. Sight, Darkness & Blood Scent
        sectionHeaders.add(new SectionHeader(y + 1, Txt.translatable("gui.mobsenses.tab.sight")));
        y += 13;

        addServerSlider(x, y, colW, "gui.mobsenses.sight.sight", "gui.mobsenses.sight.sight.tip",
                s.sight(), 0.0, 3.0, 0.05, SettingsScreen::fmtMult, "sight", editable);
        addServerSlider(rightX, y, rightW, "gui.mobsenses.sight.sight_range", "gui.mobsenses.sight.sight_range.tip",
                s.sightRange(), 0.0, 48.0, 1.0, SettingsScreen::fmtBlocks, "sight_range", editable);
        y += 21;

        addServerToggle(x, y, colW, "gui.mobsenses.sight.darkness_stealth", "gui.mobsenses.sight.darkness_stealth.tip",
                s.darknessStealthEnabled(), "darkness_stealth_enabled", editable);
        addServerToggle(rightX, y, rightW, "gui.mobsenses.sight.torchlight_penalty", "gui.mobsenses.sight.torchlight_penalty.tip",
                s.heldLightEnabled(), "held_light_enabled", editable);
        y += 21;

        addServerToggle(x, y, colW, "gui.mobsenses.sight.blood_scent", "gui.mobsenses.sight.blood_scent.tip",
                s.scentEnabled(), "scent_enabled", editable);
        addServerToggle(rightX, y, rightW, "gui.mobsenses.sight.blood_particles", "gui.mobsenses.sight.blood_particles.tip",
                s.bloodParticles(), "blood_particles", editable);
        y += 21;

        addServerSlider(x, y, colW, "gui.mobsenses.sight.blood_range", "gui.mobsenses.sight.blood_range.tip",
                s.scentRange(), 0.0, 48.0, 1.0, SettingsScreen::fmtBlocks, "scent_range", editable);
        addServerSlider(rightX, y, rightW, "gui.mobsenses.sight.horde_range", "gui.mobsenses.sight.horde_range.tip",
                s.hordeAlertRange(), 0.0, 32.0, 1.0, SettingsScreen::fmtBlocks, "horde_alert_range", editable);
        y += 21;

        addServerToggle(x, y, colW, "gui.mobsenses.sight.safe_goal_filter", "gui.mobsenses.sight.safe_goal_filter.tip",
                s.safeGoalFilter(), "safe_goal_filter", editable);
        addServerToggle(rightX, y, rightW, "gui.mobsenses.sight.suspicion", "gui.mobsenses.sight.suspicion.tip",
                s.suspicionEnabled(), "suspicion_enabled", editable);
        y += 23;

        // 4. World & Server
        sectionHeaders.add(new SectionHeader(y + 1, Txt.translatable("gui.mobsenses.tab.server")));
        y += 13;

        String worldId = resolveCurrentWorldId();
        Preset worldOwn = worldId.isEmpty() ? null : s.worldOwnPreset(worldId);
        Component worldBadge = worldOwn == null
                ? Txt.literal("Inherit (" + s.preset().id() + ")").withStyle(ChatFormatting.GRAY)
                : Txt.translatable("command.mobsenses.preset." + worldOwn.id()).withStyle(ChatFormatting.AQUA);
        addScrollButton(x, y, w, 18,
                kvLabel("gui.mobsenses.server.world_preset", worldBadge),
                "gui.mobsenses.server.world_preset.tip",
                () -> {
                    String nextArg = nextWorldPresetArg(worldOwn);
                    String targetWorld = worldId.isEmpty() ? "minecraft:overworld" : worldId;
                    applyAdminImmediate("world " + targetWorld + " " + nextArg);
                }, editable);
        y += 21;

        addServerToggle(x, y, colW, "gui.mobsenses.server.allow_client_hud", "gui.mobsenses.server.allow_client_hud.tip",
                s.allowClientHud(), "allow_client_hud", editable);

        boolean debugOn = MobSenses.serverLink().connected()
                ? MobSenses.serverLink().debugEnabled()
                : MobSenses.debug();
        addScrollButton(rightX, y, rightW, 18,
                toggleLabel("gui.mobsenses.server.debug", debugOn),
                "gui.mobsenses.server.debug.tip",
                () -> applyAdminImmediate("debug " + (debugOn ? "off" : "on")),
                editable);
        y += 22;

        return y;
    }

    private void buildFooter(int x, int w) {
        boolean editable = currentTab == Tab.HUD || isServerEditable();
        Button resetBtn = Btn.create(x, footerY, 80, 18,
                Txt.translatable("gui.mobsenses.reset"),
                () -> {
                    if (currentTab == Tab.HUD) {
                        MobSenses.clientSettings().reset();
                        MobSenses.clientSettings().save();
                        MobSenses.hud().setEnabled(MobSenses.clientSettings().enabled());
                        rebuildLayout();
                    } else {
                        applyAdminImmediate("reset all");
                    }
                }, null);
        resetBtn.active = editable;
        registerFixed(resetBtn);

        if (currentTab == Tab.SENSES) {
            Button reloadBtn = Btn.create(x + 84, footerY, 84, 18,
                    Txt.translatable("gui.mobsenses.reload"),
                    () -> applyAdminImmediate("reload"), null);
            reloadBtn.active = editable;
            registerFixed(reloadBtn);
        }

        Button doneBtn = Btn.create(x + w - 82, footerY, 82, 18,
                Txt.translatable("gui.mobsenses.done"),
                this::onClose, null);
        registerFixed(doneBtn);
    }

    private static String nextWorldPresetArg(Preset current) {
        if (current == null) {
            return Preset.OFF.id();
        }
        switch (current) {
            case OFF:
                return Preset.SUBTLE.id();
            case SUBTLE:
                return Preset.DEFAULT.id();
            case DEFAULT:
                return Preset.HARDCORE.id();
            case HARDCORE:
            default:
                return "inherit";
        }
    }

    private String resolveCurrentWorldId() {
        if (MobSenses.serverLink().connected()) {
            String remote = MobSenses.serverLink().currentWorld();
            if (remote != null && !remote.isEmpty()) {
                return remote;
            }
        }
        if (this.minecraft != null && this.minecraft.level != null) {
            String raw = String.valueOf(this.minecraft.level.dimension());
            int slash = raw.indexOf('/');
            int bracket = raw.indexOf(']');
            if (slash > 0 && bracket > slash) {
                return raw.substring(slash + 1, bracket).trim();
            }
        }
        return "minecraft:overworld";
    }

    private void addServerToggle(
            int x, int y, int w,
            String labelKey, String tipKey,
            boolean current, String optionKey, boolean editable) {
        addScrollButton(x, y, w, 18,
                toggleLabel(labelKey, current),
                tipKey,
                () -> applyAdminImmediate("set " + optionKey + " " + (!current)),
                editable);
    }

    private void addServerSlider(
            int x, int y, int w,
            String labelKey, String tipKey,
            double current, double min, double max, double step,
            java.util.function.DoubleFunction<String> fmt,
            String optionKey, boolean editable) {
        addScrollSlider(x, y, w, 18, labelKey, tipKey, current, min, max, step, fmt, v -> {
            String formatted = ServerSettings.format(v);
            if (isRemoteMultiplayer()) {
                if (pendingSliderKey != null && !pendingSliderKey.equals(optionKey)) {
                    flushPendingSlider();
                }
                activeServerSettings().setOption(optionKey, formatted);
                pendingSliderKey = optionKey;
                pendingSliderCommand = "set " + optionKey + " " + formatted;
                pendingSliderTicks = 6;
            } else {
                executeLocalAdmin("set " + optionKey + " " + formatted);
            }
        }, editable);
    }

    private void applyAdminImmediate(String command) {
        flushPendingSlider();
        if (isRemoteMultiplayer()) {
            if (MobSenses.serverLink().connected() && MobSenses.serverLink().isAdmin()) {
                MobSenses.serverLink().sendAdmin(command, ClientLink.languageCode(this.minecraft));
            }
        } else {
            executeLocalAdmin(command);
            rebuildLayout();
        }
    }

    private void executeLocalAdmin(String command) {
        final String lang = ClientLink.languageCode(this.minecraft);
        final String world = resolveCurrentWorldId();
        final List<String> worlds = Collections.singletonList(world);
        Reply reply = AdminCommands.execute(command, new AdminCommands.Context() {
            @Override
            public String language() {
                return lang;
            }

            @Override
            public String world() {
                return world;
            }

            @Override
            public List<String> worlds() {
                return worlds;
            }
        });
        if (!reply.lines().isEmpty()) {
            localToastMessage = reply.lines().get(0).text();
            localToastOk = !reply.refused();
            localToastTimeMs = System.currentTimeMillis();
        }
    }

    private void flushPendingSlider() {
        if (pendingSliderCommand != null) {
            String cmd = pendingSliderCommand;
            pendingSliderKey = null;
            pendingSliderCommand = null;
            pendingSliderTicks = 0;
            if (isRemoteMultiplayer() && MobSenses.serverLink().connected() && MobSenses.serverLink().isAdmin()) {
                MobSenses.serverLink().sendAdmin(cmd, ClientLink.languageCode(this.minecraft));
            }
        }
    }

    @Override
    public void removed() {
        flushPendingSlider();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingSliderTicks > 0) {
            pendingSliderTicks--;
            if (pendingSliderTicks == 0) {
                flushPendingSlider();
            }
        }
    }

    private <T extends AbstractWidget> T registerFixed(T widget) {
        addRenderableWidget(widget);
        allWidgets.add(widget);
        return widget;
    }

    private void addScrollButton(
            int x, int baseY, int w, int h,
            Component label, String tipKey,
            Runnable onPress, boolean active) {
        Tip tip = tipKey == null ? null : Tip.of(Txt.translatable(tipKey));
        Button btn = Btn.create(x, baseY, w, h, label, onPress, tip);
        btn.active = active;
        addRenderableWidget(btn);
        allWidgets.add(btn);
        scrollRows.add(new ScrollRow(btn, baseY));
    }

    private void addScrollSlider(
            int x, int baseY, int w, int h,
            String labelKey, String tipKey,
            double current, double min, double max, double step,
            java.util.function.DoubleFunction<String> fmt,
            java.util.function.DoubleConsumer onChanged,
            boolean active) {
        Tip tip = tipKey == null ? null : Tip.of(Txt.translatable(tipKey));
        RangeSlider slider = new RangeSlider(x, baseY, w, h, Txt.translatable(labelKey),
                current, min, max, step, fmt, v -> {
                    lastSliderEditMs = System.currentTimeMillis();
                    if (onChanged != null) {
                        onChanged.accept(v);
                    }
                }, tip);
        slider.active = active;
        addRenderableWidget(slider);
        allWidgets.add(slider);
        scrollRows.add(new ScrollRow(slider, baseY));
    }

    private void layoutScrollRows() {
        for (ScrollRow row : scrollRows) {
            int drawY = row.baseY - scrollY;
            Mc.setY(row.widget, drawY);
            row.widget.visible = drawY >= bodyTop && (drawY + row.widget.getHeight()) <= bodyBottom;
        }
    }

    protected void rebuildLayout() {
        clearWidgets();
        init();
    }

    protected boolean onScroll(double mouseX, double mouseY, double verticalDelta) {
        if (maxScroll <= 0) {
            return false;
        }
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < bodyTop || mouseY > bodyBottom) {
            return false;
        }
        int step = (int) Math.round(-verticalDelta * 18.0);
        int next = Math.max(0, Math.min(maxScroll, scrollY + step));
        if (next != scrollY) {
            scrollY = next;
            layoutScrollRows();
            return true;
        }
        return false;
    }

    /** Paints panel framing, headers, and section borders before widgets render. */
    protected void paintBackdrop(Canvas canvas) {
        canvas.fill(0, 0, this.width, this.height, Palette.SCRIM);
        canvas.fill(panelX, panelTop, panelX + panelW, panelBottom, Palette.PANEL_BG);
        canvas.fill(panelX, panelTop, panelX + panelW, tabsY + 20, Palette.PANEL_HEADER);
        canvas.fill(panelX, footerY - 4, panelX + panelW, panelBottom, Palette.PANEL_FOOTER);
        canvas.frame(panelX, panelTop, panelX + panelW, panelBottom, Palette.PANEL_BORDER);
        canvas.hLine(panelX + 1, panelX + panelW - 1, panelTop + 1, Palette.PANEL_SHEEN);
        canvas.hLine(panelX + 1, panelX + panelW - 1, bodyTop - 1, Palette.CARD_BORDER);
        canvas.hLine(panelX + 1, panelX + panelW - 1, footerY - 4, Palette.CARD_BORDER);
    }

    protected void paint(Canvas canvas, int mouseX, int mouseY) {
        int innerX = panelX + 8;
        int innerW = panelW - 16;

        // Title & Subtitle
        canvas.text(this.title, innerX + 2, panelTop + 6, Palette.TEXT_PRIMARY);
        Component sub = Txt.translatable("gui.mobsenses.subtitle");
        if (canvas.pushScale(0.75F)) {
            canvas.text(sub, Math.round((innerX + 2) / 0.75F), Math.round((panelTop + 16) / 0.75F), Palette.TEXT_SECONDARY);
            canvas.popScale();
        }

        // Active tab emerald accent underline
        Tab[] tabs = Tab.values();
        int tabGap = 4;
        int tabW = (innerW - tabGap * (tabs.length - 1)) / tabs.length;
        int idx = currentTab.ordinal();
        int tx = innerX + idx * (tabW + tabGap);
        int tw = (idx == tabs.length - 1) ? (innerX + innerW - tx) : tabW;
        canvas.fill(tx + 1, tabsY + 17, tx + tw - 1, tabsY + 19, Palette.ACCENT);

        // Mode / Fair-play banner
        if (hasActiveBanner()) {
            paintBanner(canvas, innerX, bannerY, innerW);
        }

        // Scrollable signature header cards & section headers
        ScrollCanvas scroll = new ScrollCanvas(canvas, bodyTop, bodyBottom, scrollY);
        for (SectionHeader sh : sectionHeaders) {
            scroll.text(sh.title, innerX + 2, sh.baseY, Palette.ACCENT);
        }
        if (currentTab == Tab.HUD) {
            paintHudPreviewCard(scroll, innerX, bodyTop + 4, innerW);
        } else {
            paintSensesSummaryCard(scroll, innerX, bodyTop + 4, innerW);
        }

        ScrollCanvas.drawScrollbar(canvas, panelX + panelW - 5, bodyTop + 2, bodyBottom - 2,
                scrollY, maxScroll, contentHeight);

        // Reply Toast bar above footer
        long now = System.currentTimeMillis();
        if (!localToastMessage.isEmpty() && (now - localToastTimeMs) < 6000L) {
            int toastY = bodyBottom + 1;
            int bg = localToastOk ? Palette.EMERALD_BG : Palette.CORAL_BG;
            int brd = localToastOk ? Palette.EMERALD_BORDER : Palette.CORAL_BORDER;
            int fg = localToastOk ? Palette.ACCENT : Palette.CORAL;
            canvas.fill(innerX, toastY, innerX + innerW, toastY + 14, bg);
            canvas.frame(innerX, toastY, innerX + innerW, toastY + 14, brd);
            canvas.text(Txt.literal(localToastMessage), innerX + 5, toastY + 3, fg);
        }

        // Legacy 1.16.5 - 1.19.2 hover tooltip card
        Component legacyTip = Btn.hoveredTipText(allWidgets, mouseX, mouseY);
        if (legacyTip != null) {
            int twTip = Math.min(panelW - 12, canvas.textWidth(legacyTip) + 10);
            int txTip = Math.max(4, Math.min(this.width - twTip - 4, mouseX + 8));
            int tyTip = Math.max(4, mouseY - 16);
            canvas.fill(txTip, tyTip, txTip + twTip, tyTip + 14, 0xF00B0F17);
            canvas.frame(txTip, tyTip, txTip + twTip, tyTip + 14, Palette.ACCENT);
            canvas.text(legacyTip, txTip + 5, tyTip + 3, Palette.TEXT_PRIMARY);
        }
    }

    private void paintBanner(Canvas canvas, int x, int y, int w) {
        if (currentTab == Tab.HUD) {
            canvas.fill(x, y, x + w, y + 15, Palette.AMBER_BG);
            canvas.frame(x, y, x + w, y + 15, Palette.AMBER_BORDER);
            canvas.text(Txt.literal("🔒 ").append(Txt.translatable("gui.mobsenses.hud.locked_by_server")),
                    x + 5, y + 4, Palette.AMBER);
            return;
        }
        if (!isRemoteMultiplayer()) {
            canvas.fill(x, y, x + w, y + 15, Palette.EMERALD_BG);
            canvas.frame(x, y, x + w, y + 15, Palette.EMERALD_BORDER);
            canvas.text(Txt.literal("● ").append(Txt.translatable("gui.mobsenses.server.singleplayer")),
                    x + 5, y + 4, Palette.ACCENT);
        } else if (MobSenses.serverLink().connected()) {
            if (MobSenses.serverLink().isAdmin()) {
                canvas.fill(x, y, x + w, y + 15, Palette.EMERALD_BG);
                canvas.frame(x, y, x + w, y + 15, Palette.EMERALD_BORDER);
                canvas.text(Txt.literal("● ").append(Txt.translatable("gui.mobsenses.server.connected")),
                        x + 5, y + 4, Palette.ACCENT);
            } else {
                canvas.fill(x, y, x + w, y + 15, Palette.AMBER_BG);
                canvas.frame(x, y, x + w, y + 15, Palette.AMBER_BORDER);
                canvas.text(Txt.literal("🔒 ").append(Txt.translatable("gui.mobsenses.server.readonly")),
                        x + 5, y + 4, Palette.AMBER);
            }
        } else {
            canvas.fill(x, y, x + w, y + 15, Palette.SKY_BG);
            canvas.frame(x, y, x + w, y + 15, Palette.SKY_BORDER);
            canvas.text(Txt.translatable("gui.mobsenses.server.not_installed"), x + 5, y + 4, Palette.SKY);
        }
    }

    /** 1. HUD Tab Card: Interactive mini-viewport rendering the real StealthHudOverlay. */
    private void paintHudPreviewCard(Canvas canvas, int x, int y, int w) {
        int h = 60;
        canvas.fill(x, y, x + w, y + h, Palette.PREVIEW_VIEWPORT_BG);
        canvas.frame(x, y, x + w, y + h, Palette.CARD_BORDER);
        canvas.hLine(x + 4, x + w - 4, y + h / 2, Palette.PREVIEW_GRID);
        canvas.vLine(x + w / 2, y + 4, y + h - 4, Palette.PREVIEW_GRID);

        long now = System.currentTimeMillis();
        StealthHudState stateToDraw = previewHud;
        if ((now - lastPulseTimeMs) > 6500L
                && this.minecraft != null && this.minecraft.player != null && this.minecraft.level != null
                && MobSenses.hud().overallExposure() > 5) {
            stateToDraw = MobSenses.hud();
        } else if ((now - lastPulseTimeMs) > 3800L) {
            triggerPreviewPulse(previewStage);
        }
        StealthHudOverlay.renderPreview(canvas, x, y, w, h, stateToDraw, MobSenses.clientSettings());
    }

    /**
     * 2. SENSES Tab Card: Live Server Telemetry, Fair-Play Badge, Acoustic Wall Attenuation
     * (Air / Wood / Stone / Wool for Step, Voice, Silenced & Gunshot), and Sight/Scent Meters.
     */
    private void paintSensesSummaryCard(Canvas canvas, int x, int y, int w) {
        int h = 112;
        canvas.fill(x, y, x + w, y + h, Palette.CARD_BG);
        canvas.frame(x, y, x + w, y + h, Palette.CARD_BORDER);
        canvas.fill(x, y, x + 2, y + h, Palette.ACCENT);

        ServerLink link = MobSenses.serverLink();
        boolean remote = link.connected();
        ServerSettings s = activeServerSettings();
        long now = System.currentTimeMillis();

        String platform = remote && !link.serverPlatform().isEmpty()
                ? link.serverPlatform()
                : MobSenses.platform().toString();
        if ("unknown ?".equals(platform) && this.minecraft != null) {
            platform = "Local World";
        }
        String world = resolveCurrentWorldId();
        String shortWorld = world.startsWith("minecraft:") ? world.substring("minecraft:".length()) : world;
        long noiseMin = remote ? link.noiseLastMinute() : MobSenses.noise().lastMinute(now);
        long alertedMin = remote ? link.alertedLastMinute() : MobSenses.alerted().lastMinute(now);
        boolean hudAllowed = remote ? link.allowClientHud() : s.allowClientHud();

        // Row 1: Platform & Fair-play HUD badge
        canvas.text(Txt.literal("Mob Senses • " + platform), x + 6, y + 4, Palette.TEXT_PRIMARY);
        Component hudBadge = hudAllowed
                ? Txt.literal("HUD Allowed").withStyle(ChatFormatting.GREEN)
                : Txt.literal("🔒 HUD Locked").withStyle(ChatFormatting.GOLD);
        canvas.right(hudBadge, x + w - 6, y + 4, hudAllowed ? Palette.ACCENT : Palette.AMBER);

        // Row 2: Active World & Live Server Activity
        canvas.text(Txt.literal("World: " + shortWorld), x + 6, y + 15, Palette.SKY);
        canvas.right(Txt.literal(noiseMin + " events/min • " + alertedMin + " alerted/min"),
                x + w - 6, y + 15, Palette.ACCENT);
        canvas.hLine(x + 6, x + w - 6, y + 25, Palette.CARD_BORDER);

        // Row 3: Acoustic Wall Attenuation Legend
        Preset p = s.presetOf(world);
        double hMult = s.hearing();
        double wMult = s.wallMuffling();
        boolean through = s.throughWallHearing();

        canvas.text(Txt.literal("Acoustic:"), x + 6, y + 28, Palette.TEXT_PRIMARY);
        Component legend = Txt.empty()
                .append(Txt.literal("Air ").withStyle(ChatFormatting.GREEN))
                .append(Txt.literal("• Wood ").withStyle(ChatFormatting.AQUA))
                .append(Txt.literal("• Stone ").withStyle(ChatFormatting.YELLOW))
                .append(Txt.literal("• Wool").withStyle(ChatFormatting.RED));
        canvas.right(legend, x + w - 6, y + 28, Palette.TEXT_SECONDARY);

        double stepAir = NoiseModel.effectiveRange(s.stepRange(), p, hMult);
        double voiceAir = s.voice() ? NoiseModel.effectiveRange(s.voiceRange(), p, hMult) : 0.0;
        double silAir = NoiseModel.effectiveRange(s.gunshotSilencedRange(), p, hMult);
        double gunAir = NoiseModel.effectiveRange(s.gunshotRange(), p, hMult);
        double scaleMax = Math.max(48.0, Math.max(gunAir, Math.max(silAir, Math.max(voiceAir, stepAir))));

        drawAttenuationRow(canvas, x + 6, y + 39, w - 12, "Step", stepAir, wMult, through, scaleMax);
        drawAttenuationRow(canvas, x + 6, y + 49, w - 12, "Voice", voiceAir, wMult, through, scaleMax);
        drawAttenuationRow(canvas, x + 6, y + 59, w - 12, "Silenced", silAir, wMult, through, scaleMax);
        drawAttenuationRow(canvas, x + 6, y + 69, w - 12, "Gunshot", gunAir, wMult, through, scaleMax);
        canvas.hLine(x + 6, x + w - 6, y + 79, Palette.CARD_BORDER);

        // Sight & Blood Scent meters
        double baseSight = (p == Preset.OFF) ? 0.0 : (s.sightRange() * s.sight());
        double dayWalk = computeSightPreview(s, p, baseSight, 0, 15, 0, false);
        double darkSneak = computeSightPreview(s, p, baseSight, 0, 0, 0, true);
        double bloodR = (p == Preset.OFF || !s.scentEnabled()) ? 0.0 : (s.scentRange() * p.hearing());
        double sightScaleMax = Math.max(32.0, Math.max(dayWalk, bloodR));

        drawSingleRangeRow(canvas, x + 6, y + 82, w - 12, "Daylight", dayWalk, sightScaleMax, Palette.AMBER);
        drawSingleRangeRow(canvas, x + 6, y + 92, w - 12, "Dark Sneak", darkSneak, sightScaleMax, Palette.ACCENT);
        drawSingleRangeRow(canvas, x + 6, y + 102, w - 12, "Blood Trail", bloodR, sightScaleMax, Palette.CORAL);
    }

    private static void drawAttenuationRow(
            Canvas canvas, int x, int y, int totalW,
            String name, double airRange, double wallMuffling, boolean throughWall, double scaleMax) {
        double woodR = wallRange(airRange, 0.7, wallMuffling, throughWall);
        double stoneR = wallRange(airRange, 1.0, wallMuffling, throughWall);
        double woolR = wallRange(airRange, 1.4, wallMuffling, throughWall);

        int labelW = 48;
        int readoutW = 88;
        int trackX = x + labelW;
        int trackW = Math.max(32, totalW - labelW - readoutW - 4);

        canvas.text(Txt.literal(name), x, y, Palette.TEXT_SECONDARY);
        canvas.fill(trackX, y + 2, trackX + trackW, y + 6, 0xFF0A0E15);

        fillBarFraction(canvas, trackX, y + 2, trackW, 4, airRange / scaleMax, 0x9934D399);
        fillBarFraction(canvas, trackX, y + 2, trackW, 4, woodR / scaleMax, 0xBB38BDF8);
        fillBarFraction(canvas, trackX, y + 2, trackW, 4, stoneR / scaleMax, 0xDDFBBF24);
        fillBarFraction(canvas, trackX, y + 2, trackW, 4, woolR / scaleMax, 0xFFF87171);

        String nums = Math.round(airRange) + "/" + Math.round(woodR) + "/" + Math.round(stoneR) + "/" + Math.round(woolR) + "m";
        canvas.right(Txt.literal(nums), x + totalW, y, Palette.TEXT_PRIMARY);
    }

    private static double wallRange(double air, double thickness, double muffling, boolean throughWall) {
        if (air <= 0.0) {
            return 0.0;
        }
        if (!throughWall && thickness >= 0.85) {
            return 0.0;
        }
        return NoiseModel.audibleRange(air, thickness, muffling);
    }

    private static double computeSightPreview(
            ServerSettings s, Preset p, double baseRange,
            int blockLight, int skyLight, int heldLight, boolean sneaking) {
        if (baseRange <= 0.0 || p == null || p == Preset.OFF) {
            return 0.0;
        }
        double factor = SightModel.visibilityFactor(blockLight, skyLight, heldLight, sneaking, false, s);
        double minVis = s.darknessStealthEnabled() ? s.darknessMinVisibility() : 1.0;
        double scaled = 1.0 + (factor - 1.0) * p.sight();
        return Math.max(2.0, baseRange * Math.max(minVis, scaled));
    }

    private static void drawSingleRangeRow(
            Canvas canvas, int x, int y, int totalW,
            String label, double meters, double scaleMax, int barColor) {
        int labelW = 64;
        int readoutW = 38;
        int trackX = x + labelW;
        int trackW = Math.max(32, totalW - labelW - readoutW - 4);

        canvas.text(Txt.literal(label), x, y, Palette.TEXT_SECONDARY);
        canvas.fill(trackX, y + 2, trackX + trackW, y + 6, 0xFF0A0E15);
        fillBarFraction(canvas, trackX, y + 2, trackW, 4, meters / scaleMax, barColor);
        canvas.right(Txt.literal(String.format(Locale.ROOT, "%.1fm", meters)), x + totalW, y, Palette.TEXT_PRIMARY);
    }

    private static void fillBarFraction(Canvas canvas, int x, int y, int trackW, int h, double frac, int argb) {
        if (!(frac > 0.0)) {
            return;
        }
        int fillW = Math.max(1, Math.min(trackW, (int) Math.round(frac * trackW)));
        canvas.fill(x, y, x + fillW, y + h, argb);
    }

    private static Component toggleLabel(String key, boolean on) {
        Component state = Txt.translatable(on ? "gui.mobsenses.on" : "gui.mobsenses.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
        return Txt.empty().append(Txt.translatable(key)).append(Txt.literal(": ")).append(state);
    }

    private static Component kvLabel(String key, Component value) {
        return Txt.empty().append(Txt.translatable(key)).append(Txt.literal(": ")).append(value);
    }

    private static String fmtMult(double v) {
        return String.format(Locale.ROOT, "%.2f×", v);
    }

    private static String fmtBlocks(double v) {
        return Math.round(v) + " m";
    }

    private static String fmtBlocksHalf(double v) {
        return Math.abs(v - Math.round(v)) < 1.0e-3
                ? (Math.round(v) + " m")
                : String.format(Locale.ROOT, "%.1f m", v);
    }

    private static String fmtPct01(double v) {
        return Math.round(v * 100.0) + "%";
    }

    @Override
    public void onClose() {
        flushPendingSlider();
        MobSenses.clientSettings().save();
        Mc.openScreen(this.minecraft, this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
