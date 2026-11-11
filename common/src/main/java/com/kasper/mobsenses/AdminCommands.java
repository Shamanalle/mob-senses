package com.kasper.mobsenses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code /mobsenses}, the same on every loader and on Bukkit: the platform passes the text after the
 * command and sends the {@link Reply} back.
 */
public final class AdminCommands {

    public static final String NAME = "mobsenses";
    public static final String HUD_COMMAND_NAME = "mshud";
    public static final String HUD_COMMAND_ALIAS = "mobsenseshud";

    public static final String PERM_ADMIN = "mobsenses.admin";

    private static final List<String> SUBCOMMANDS = Jv.listOf(
            "status", "preset", "hearing", "sight", "voice", "world",
            "set", "get", "reset", "hud", "debug", "reload", "help");
    private static final List<String> CATEGORIES = Jv.listOf(
            "core", "server", "guns", "sounds", "stealth", "voice", "sight", "scent", "mobs", "all");
    private static final List<String> HUD_MODES = Jv.listOf(
            "on", "off", "tactical", "minimal", "compact", "full", "auto_hide", "range", "style",
            "opacity", "offset", "bar", "reset",
            "top_left", "top_right", "bottom_left", "bottom_right", "top_center", "hotbar");
    private static final List<String> HUD_BARS = Jv.listOf("noise", "light", "scent", "voice", "range");
    /** What {@code world <id>} takes besides a preset: the world goes back to the server's preset. */
    private static final String INHERIT = "inherit";

    /** What the platform knows about who asked. */
    public interface Context {
        /** The language of the reply ("ru_ru"); empty for the settings' language. */
        String language();

        /** The id of the sender's world ("minecraft:overworld"), or null for the console. */
        String world();

        /** The ids of the server's worlds, for {@code world} and its suggestions. */
        List<String> worlds();

        /** Whether this command is running on a dedicated server with no local client HUD. */
        default boolean isDedicatedServer() {
            return false;
        }

        /** Whether the sender holds the given permission node (defaults to {@code true}). */
        default boolean hasPermission(String node) {
            return true;
        }
    }

    private static final Context CLIENT_CTX = new Context() {
        @Override
        public String language() {
            return "";
        }

        @Override
        public String world() {
            return null;
        }

        @Override
        public List<String> worlds() {
            return Collections.emptyList();
        }
    };

    private AdminCommands() {
    }

    public static List<String> subcommands() {
        return SUBCOMMANDS;
    }

    public static Reply execute(String args, Context ctx) {
        ServerSettings s = MobSenses.settings();
        String lang = Jv.isBlank(ctx.language()) ? s.language() : ctx.language();
        String[] a = args == null || Jv.isBlank(args) ? new String[0] : args.trim().split("\\s+");
        try {
            String sub = a.length == 0 ? "status" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "status":
                    return status(lang, s);
                case "help":
                    return help(lang);
                case "preset":
                    return preset(lang, s, a);
                case "hearing":
                case "sight":
                    return multiplier(lang, s, sub, a);
                case "voice":
                    return voice(lang, s, a);
                case "world":
                    return world(lang, s, ctx, a);
                case "get":
                    return setOrGet(lang, s, a, true);
                case "set":
                    return setOrGet(lang, s, a, false);
                case "reset":
                    return reset(lang, s, a);
                case "hud":
                    if (ctx.isDedicatedServer()) {
                        return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "hud.client_only"));
                    }
                    return hud(lang, a);
                case "debug":
                    return debug(lang, a);
                case "reload":
                    s.load();
                    MobSenses.onSettingsChanged();
                    return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "reload.done", fileName(s)));
                default:
                    return unknown(lang, a[0]);
            }
        } catch (RuntimeException e) {
            ModLog.failure("/" + NAME + " " + args, e);
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "error"));
        }
    }

    /**
     * Executes the client-only {@code /mshud} ({@code /mobsenseshud}) command directly on the player's
     * client without requiring operator permissions or server round-trips.
     */
    public static Reply executeHud(String args, String languageCode) {
        String lang = Jv.isBlank(languageCode) ? MobSenses.settings().language() : languageCode;
        try {
            if (args == null || Jv.isBlank(args)) {
                return hud(lang, new String[]{"hud"});
            }
            String[] extra = args.trim().split("\\s+");
            String[] tokens = new String[extra.length + 1];
            tokens[0] = "hud";
            System.arraycopy(extra, 0, tokens, 1, extra.length);
            return hud(lang, tokens);
        } catch (RuntimeException e) {
            ModLog.failure("/" + HUD_COMMAND_NAME + " " + args, e);
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "error"));
        }
    }

    /** Suggestions for the client-only {@code /mshud} command. */
    public static List<String> hudSuggestions(String typed) {
        String clean = typed == null ? "" : typed.replaceFirst("^\\s+", "");
        return suggestions("hud " + clean, CLIENT_CTX);
    }

    private static Reply status(String lang, ServerSettings s) {
        long now = System.currentTimeMillis();
        Reply r = new Reply();
        r.add(Reply.Kind.TITLE, ServerText.get(lang, "status.title", BuildInfo.version(), MobSenses.platform()));
        r.addWithChips(Reply.Kind.INFO,
                ServerText.get(lang, "status.preset", presetName(lang, s.preset())),
                Reply.presetChips(s.preset()));
        for (String world : s.worlds().keySet()) {
            r.add(Reply.Kind.INFO, ServerText.get(lang, "status.world", world, presetName(lang, s.worlds().get(world))));
        }
        if (!s.allowClientHud()) {
            r.add(Reply.Kind.INFO, ServerText.get(lang, "status.hud_locked"));
        }
        r.add(Reply.Kind.INFO, ServerText.get(lang, "status.senses",
                ServerSettings.format(s.hearing()), ServerSettings.format(s.sight())));
        String voice = !s.voice() ? "status.voice.off" : MobSenses.voiceChatInstalled() ? "status.voice.on" : "status.voice.missing";
        r.addWithChips(Reply.Kind.INFO, ServerText.get(lang, voice), Reply.toggleChips("voice", s.voice()));
        r.add(Reply.Kind.INFO, ServerText.get(lang, "status.noise", MobSenses.noise().lastMinute(now)));
        r.add(Reply.Kind.INFO, ServerText.get(lang, "status.alerted", MobSenses.alerted().lastMinute(now)));
        if (MobSenses.debug()) {
            r.add(Reply.Kind.INFO, ServerText.get(lang, "debug.on"));
        }
        r.add(Reply.Kind.HINT, ServerText.get(lang, "status.file", fileName(s)));
        return r;
    }

    private static Reply help(String lang) {
        Reply r = new Reply().add(Reply.Kind.TITLE, ServerText.get(lang, "help.title"));
        for (String sub : SUBCOMMANDS) {
            r.add(Reply.Kind.INFO, ServerText.get(lang, "help." + sub));
        }
        return r;
    }

    private static Reply preset(String lang, ServerSettings s, String[] a) {
        if (a.length < 2) {
            return new Reply()
                    .add(Reply.Kind.INFO, ServerText.get(lang, "status.preset", presetName(lang, s.preset())))
                    .addWithChips(Reply.Kind.HINT, ServerText.get(lang, "preset.list", presetIds()), Reply.presetChips(s.preset()));
        }
        Preset p = Preset.byId(a[1]);
        if (p == null) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "preset.unknown", a[1], presetIds()));
        }
        s.setPreset(p);
        s.save();
        MobSenses.onSettingsChanged();
        return new Reply().addWithChips(Reply.Kind.DONE, ServerText.get(lang, "preset.set", presetName(lang, p)),
                Reply.presetChips(p));
    }

    private static Reply multiplier(String lang, ServerSettings s, String sense, String[] a) {
        String name = ServerText.get(lang, "sense." + sense);
        double current = sense.equals("hearing") ? s.hearing() : s.sight();
        if (a.length < 2) {
            return new Reply().add(Reply.Kind.INFO, ServerText.get(lang, "multiplier.show", name, ServerSettings.format(current)))
                    .add(Reply.Kind.HINT, ServerText.get(lang, "help." + sense));
        }
        double value;
        try {
            value = Double.parseDouble(a[1].replace(',', '.'));
        } catch (NumberFormatException e) {
            value = Double.NaN;
        }
        if (!ServerSettings.validMultiplier(value)) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "multiplier.bad", a[1],
                    ServerSettings.format(ServerSettings.MIN_MULTIPLIER), ServerSettings.format(ServerSettings.MAX_MULTIPLIER)));
        }
        if (sense.equals("hearing")) {
            s.setHearing(value);
        } else {
            s.setSight(value);
        }
        s.save();
        MobSenses.onSettingsChanged();
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "multiplier.set", name, ServerSettings.format(value)));
    }

    private static Reply voice(String lang, ServerSettings s, String[] a) {
        if (a.length < 2) {
            return new Reply()
                    .addWithChips(Reply.Kind.INFO, ServerText.get(lang, s.voice() ? "status.voice.on" : "status.voice.off"),
                            Reply.toggleChips("voice", s.voice()))
                    .add(Reply.Kind.HINT, ServerText.get(lang, "help.voice"));
        }
        String v = a[1].toLowerCase(Locale.ROOT);
        if (!v.equals("on") && !v.equals("off")) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "voice.usage"));
        }
        s.setVoice(v.equals("on"));
        s.save();
        MobSenses.onSettingsChanged();
        Reply r = new Reply().addWithChips(Reply.Kind.DONE, ServerText.get(lang, s.voice() ? "voice.on" : "voice.off"),
                Reply.toggleChips("voice", s.voice()));
        if (s.voice() && !MobSenses.voiceChatInstalled()) {
            r.add(Reply.Kind.HINT, ServerText.get(lang, "status.voice.missing"));
        }
        return r;
    }

    private static Reply debug(String lang, String[] a) {
        boolean next;
        if (a.length < 2) {
            next = !MobSenses.debug();
        } else {
            String v = a[1].toLowerCase(Locale.ROOT);
            if (!v.equals("on") && !v.equals("off")) {
                return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "debug.usage"));
            }
            next = v.equals("on");
        }
        MobSenses.setDebug(next);
        MobSenses.onSettingsChanged();
        return new Reply().addWithChips(Reply.Kind.DONE, ServerText.get(lang, next ? "debug.on" : "debug.off"),
                Reply.toggleChips("debug", next));
    }

    private static Reply world(String lang, ServerSettings s, Context ctx, String[] a) {
        if (a.length < 3) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "world.usage"))
                    .add(Reply.Kind.HINT, ServerText.get(lang, "world.known", String.join(", ", ctx.worlds())));
        }
        String world = a[1].equalsIgnoreCase("here") && ctx.world() != null ? ctx.world() : a[1];
        if (!ctx.worlds().isEmpty() && !ctx.worlds().contains(world) && !s.worlds().containsKey(world)) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "world.unknown", world))
                    .add(Reply.Kind.HINT, ServerText.get(lang, "world.known", String.join(", ", ctx.worlds())));
        }
        if (a[2].equalsIgnoreCase(INHERIT)) {
            s.setWorldPreset(world, null);
            s.save();
            MobSenses.onSettingsChanged();
            return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "world.inherit", world, presetName(lang, s.preset())));
        }
        Preset p = Preset.byId(a[2]);
        if (p == null) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "preset.unknown", a[2], presetIds() + ", " + INHERIT));
        }
        s.setWorldPreset(world, p);
        s.save();
        MobSenses.onSettingsChanged();
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "world.set", world, presetName(lang, p)));
    }

    private static Reply setOrGet(String lang, ServerSettings s, String[] a, boolean getOnly) {
        if (a.length < 2) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "set.usage"));
        }
        String key = a[1].toLowerCase(Locale.ROOT);
        if (getOnly || a.length == 2) {
            List<String> catKeys = ServerSettings.categoryKeys(key);
            if (!catKeys.isEmpty()) {
                Reply r = new Reply().add(Reply.Kind.TITLE, "Mob Senses [" + key + "]");
                for (String ck : catKeys) {
                    String val = s.getOption(ck);
                    if (val != null) {
                        r.addWithChips(Reply.Kind.INFO,
                                ServerText.get(lang, "set.show", ck, val.isEmpty() ? "(none)" : val),
                                optionInspectChips(ck, val));
                    }
                }
                return r;
            }
            String cur = s.getOption(key);
            if (cur == null) {
                return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "set.bad", key, "?"));
            }
            String canon = ServerSettings.canonicalOptionKey(key);
            return new Reply().addWithChips(Reply.Kind.INFO,
                    ServerText.get(lang, "set.show", key, cur.isEmpty() ? "(none)" : cur),
                    optionInspectChips(canon, cur));
        }
        StringBuilder valBuilder = new StringBuilder(a[2]);
        for (int i = 3; i < a.length; i++) {
            valBuilder.append(' ').append(a[i]);
        }
        String val = valBuilder.toString();
        if (!s.setOption(key, val)) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "set.bad", key, val));
        }
        s.save();
        String updated = s.getOption(key);
        String displayVal = updated != null && updated.isEmpty() ? "(none)" : updated;
        MobSenses.onSettingsChanged();
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "set.done", key, displayVal));
    }

    private static List<Reply.Span> optionInspectChips(String key, String currentValue) {
        if ("true".equalsIgnoreCase(currentValue) || "false".equalsIgnoreCase(currentValue)) {
            return Reply.boolChips(key, "true".equalsIgnoreCase(currentValue));
        }
        if ("preset".equals(key)) {
            return Reply.presetChips(Preset.byId(currentValue));
        }
        return Jv.listOf(
                Reply.suggestChip("set", "/mobsenses set " + key + " "),
                Reply.runChip("reset", "/mobsenses reset " + key));
    }

    private static Reply reset(String lang, ServerSettings s, String[] a) {
        if (a.length < 2) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "set.usage"));
        }
        String key = a[1].toLowerCase(Locale.ROOT);
        String canon = ServerSettings.canonicalOptionKey(key);
        if (!s.resetOption(key)) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "set.bad", key, "default"));
        }
        s.save();
        MobSenses.onSettingsChanged();
        if ("all".equals(canon)) {
            return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "set.done", "all", "default"));
        }
        String updated = s.getOption(key);
        String displayVal = updated != null && updated.isEmpty() ? "(none)" : updated;
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "set.done", key, displayVal));
    }

    private static Reply hud(String lang, String[] a) {
        ClientSettings cs = MobSenses.clientSettings();
        long now = System.currentTimeMillis();
        if (a.length < 2) {
            if (cs.enabled()) {
                MobSenses.hud().triggerPreview(now);
            }
            String summary = (cs.enabled() ? "on" : "off")
                    + ", style=" + cs.style().id()
                    + ", position=" + cs.position().id()
                    + ", opacity=" + cs.opacity() + "%"
                    + (cs.compact() ? ", compact" : "")
                    + (cs.autoHide() ? ", auto_hide" : "")
                    + (!cs.showRange() ? ", range=off" : "");
            return new Reply()
                    .add(Reply.Kind.INFO, ServerText.get(lang, "hud.done", summary))
                    .add(Reply.Kind.HINT, ServerText.get(lang, "hud.usage"));
        }
        String mode = a[1].toLowerCase(Locale.ROOT);
        String appliedLabel = mode;
        switch (mode) {
            case "on":
            case "true":
                cs.setEnabled(true);
                MobSenses.hud().setEnabled(true);
                break;
            case "off":
            case "false":
                cs.setEnabled(false);
                MobSenses.hud().setEnabled(false);
                break;
            case "tactical":
                cs.setStyle(ClientSettings.Style.TACTICAL);
                cs.setEnabled(true);
                MobSenses.hud().setEnabled(true);
                appliedLabel = "style=tactical";
                break;
            case "minimal":
                cs.setStyle(ClientSettings.Style.MINIMAL);
                cs.setEnabled(true);
                MobSenses.hud().setEnabled(true);
                appliedLabel = "style=minimal";
                break;
            case "style": {
                if (a.length < 3) {
                    return new Reply().add(Reply.Kind.INFO, ServerText.get(lang, "hud.done", "style=" + cs.style().id()));
                }
                String st = a[2].toLowerCase(Locale.ROOT);
                if (!"tactical".equals(st) && !"minimal".equals(st)) {
                    return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "hud.usage"));
                }
                cs.setStyle(ClientSettings.Style.byId(st));
                cs.setEnabled(true);
                MobSenses.hud().setEnabled(true);
                appliedLabel = "style=" + cs.style().id();
                break;
            }
            case "compact":
                cs.setCompact(true);
                cs.setStyle(ClientSettings.Style.TACTICAL);
                cs.setEnabled(true);
                MobSenses.hud().setEnabled(true);
                break;
            case "full":
                cs.setCompact(false);
                cs.setStyle(ClientSettings.Style.TACTICAL);
                cs.setEnabled(true);
                MobSenses.hud().setEnabled(true);
                break;
            case "auto_hide":
            case "autohide":
                cs.setAutoHide(!cs.autoHide());
                appliedLabel = "auto_hide=" + cs.autoHide();
                break;
            case "range": {
                if (a.length >= 3) {
                    Boolean forced = parseToggle(a[2]);
                    if (forced == null) {
                        return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "hud.usage"));
                    }
                    cs.setShowRange(forced);
                } else {
                    cs.setShowRange(!cs.showRange());
                }
                appliedLabel = "show_range=" + cs.showRange();
                break;
            }
            case "reset":
                cs.reset();
                MobSenses.hud().setEnabled(true);
                appliedLabel = "reset";
                break;
            case "opacity": {
                if (a.length < 3) {
                    return new Reply().add(Reply.Kind.INFO, ServerText.get(lang, "hud.done", "opacity=" + cs.opacity() + "%"));
                }
                try {
                    int op = Integer.parseInt(a[2]);
                    cs.setOpacity(op);
                    appliedLabel = "opacity=" + cs.opacity() + "%";
                } catch (NumberFormatException e) {
                    return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "hud.usage"));
                }
                break;
            }
            case "offset": {
                if (a.length < 4) {
                    return new Reply().add(Reply.Kind.INFO, ServerText.get(lang, "hud.done", "x=" + cs.x() + ", y=" + cs.y()));
                }
                try {
                    int ox = Integer.parseInt(a[2]);
                    int oy = Integer.parseInt(a[3]);
                    cs.setX(ox);
                    cs.setY(oy);
                    appliedLabel = "offset=" + cs.x() + "," + cs.y();
                } catch (NumberFormatException e) {
                    return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "hud.usage"));
                }
                break;
            }
            case "bar": {
                if (a.length < 3) {
                    return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "hud.usage"));
                }
                String bar = a[2].toLowerCase(Locale.ROOT);
                Boolean forced = null;
                if (a.length >= 4) {
                    forced = parseToggle(a[3]);
                    if (forced == null) {
                        return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "hud.usage"));
                    }
                }
                switch (bar) {
                    case "noise":
                        cs.setShowNoise(forced != null ? forced : !cs.showNoise());
                        appliedLabel = "show_noise=" + cs.showNoise();
                        break;
                    case "light":
                        cs.setShowLight(forced != null ? forced : !cs.showLight());
                        appliedLabel = "show_light=" + cs.showLight();
                        break;
                    case "scent":
                        cs.setShowScent(forced != null ? forced : !cs.showScent());
                        appliedLabel = "show_scent=" + cs.showScent();
                        break;
                    case "voice":
                        cs.setShowVoice(forced != null ? forced : !cs.showVoice());
                        appliedLabel = "show_voice=" + cs.showVoice();
                        break;
                    case "range":
                        cs.setShowRange(forced != null ? forced : !cs.showRange());
                        appliedLabel = "show_range=" + cs.showRange();
                        break;
                    default:
                        return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "hud.usage"));
                }
                break;
            }
            default: {
                ClientSettings.Anchor anchor = ClientSettings.Anchor.byId(mode);
                if (anchor == null) {
                    return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "hud.usage"));
                }
                cs.setPosition(anchor);
                cs.setEnabled(true);
                MobSenses.hud().setEnabled(true);
                break;
            }
        }
        cs.save();
        if (cs.enabled()) {
            MobSenses.hud().triggerPreview(now);
        }
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "hud.done", appliedLabel));
    }

    private static Boolean parseToggle(String s) {
        if ("on".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("off".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Reply unknown(String lang, String typed) {
        Reply r = new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "unknown", typed));
        String near = closest(typed.toLowerCase(Locale.ROOT), SUBCOMMANDS);
        if (near != null) {
            r.addWithChips(Reply.Kind.HINT, ServerText.get(lang, "unknown.suggest", near),
                    Jv.listOf(Reply.suggestChip("/mobsenses " + near, "/mobsenses " + near)));
        } else {
            r.addWithChips(Reply.Kind.HINT, ServerText.get(lang, "unknown.help"),
                    Jv.listOf(Reply.runChip("help", "/mobsenses help")));
        }
        return r;
    }

    /** Words to suggest for what has been typed so far after the command. */
    public static List<String> suggestions(String typed, Context ctx) {
        String t = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        String[] words = t.split("\\s+", -1);
        if (words.length > 0 && words[0].isEmpty() && words.length > 1) {
            words = java.util.Arrays.copyOfRange(words, 1, words.length);
        }
        List<String> options;
        if (words.length <= 1) {
            options = SUBCOMMANDS;
        } else if (words.length == 2) {
            switch (words[0]) {
                case "preset":
                    options = presetList();
                    break;
                case "voice":
                case "debug":
                    options = Jv.listOf("on", "off");
                    break;
                case "hearing":
                case "sight":
                    options = Jv.listOf("0.5", "1", "1.5", "2");
                    break;
                case "world":
                    options = new ArrayList<>(ctx.worlds());
                    if (ctx.world() != null) {
                        options.add(0, "here");
                    }
                    break;
                case "set":
                    options = ServerSettings.optionKeys();
                    break;
                case "get": {
                    options = new ArrayList<>(CATEGORIES);
                    options.addAll(ServerSettings.optionKeys());
                    break;
                }
                case "reset": {
                    options = new ArrayList<>();
                    options.add("all");
                    options.addAll(ServerSettings.optionKeys());
                    break;
                }
                case "hud":
                    options = HUD_MODES;
                    break;
                default:
                    options = Collections.emptyList();
            }
        } else if (words.length == 3 && words[0].equals("world")) {
            options = new ArrayList<>(presetList());
            options.add(INHERIT);
        } else if (words.length == 3 && words[0].equals("set")) {
            options = suggestOptionValues(words[1]);
        } else if (words.length == 3 && words[0].equals("hud")) {
            switch (words[1]) {
                case "style":
                    options = Jv.listOf("tactical", "minimal");
                    break;
                case "range":
                    options = Jv.listOf("on", "off");
                    break;
                case "opacity":
                    options = Jv.listOf("50", "75", "90", "100");
                    break;
                case "offset":
                    options = Jv.listOf("6", "10", "16", "24");
                    break;
                case "bar":
                    options = HUD_BARS;
                    break;
                default:
                    options = Collections.emptyList();
                    break;
            }
        } else if (words.length == 4 && words[0].equals("hud")) {
            if ("bar".equals(words[1])) {
                options = Jv.listOf("on", "off");
            } else if ("offset".equals(words[1])) {
                options = Jv.listOf("6", "10", "16", "24");
            } else {
                options = Collections.emptyList();
            }
        } else {
            options = Collections.emptyList();
        }
        String last = words.length == 0 ? "" : words[words.length - 1];
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                out.add(o);
            }
        }
        return out;
    }

    private static List<String> suggestOptionValues(String key) {
        if (key == null) {
            return Collections.emptyList();
        }
        switch (ServerSettings.canonicalOptionKey(key)) {
            case "preset":
                return presetList();
            case "voice":
            case "through_wall_hearing":
            case "surface_sound_scaling":
            case "held_light_enabled":
            case "darkness_stealth_enabled":
            case "scent_enabled":
            case "blood_particles":
            case "allow_client_hud":
                return Jv.listOf("true", "false");
            case "gunshot_range":
                return Jv.listOf("32", "44", "64", "96", "128", "default");
            case "gunshot_silenced_range":
                return Jv.listOf("0", "8", "12", "16", "24", "default");
            case "bullet_impact_range":
                return Jv.listOf("0", "12", "20", "32", "default");
            case "wall_muffling":
            case "hearing":
            case "sight":
                return Jv.listOf("0", "0.5", "1", "1.5", "2");
            case "max_alerted_mobs":
                return Jv.listOf("4", "8", "16", "24", "32");
            case "horde_alert_range":
                return Jv.listOf("0", "8", "16", "24");
            case "horde_max_allies":
                return Jv.listOf("0", "2", "4", "8", "12");
            case "rain_muffling":
            case "thunder_muffling":
            case "feather_falling_muffling":
                return Jv.listOf("0", "0.25", "0.45", "0.6");
            default: {
                String cur = MobSenses.settings().getOption(key);
                return cur != null && !cur.isEmpty() ? Jv.listOf(cur, "default") : Jv.listOf("default");
            }
        }
    }

    private static List<String> presetList() {
        List<String> ids = new ArrayList<>();
        for (Preset p : Preset.values()) {
            ids.add(p.id());
        }
        return ids;
    }

    private static String presetIds() {
        return String.join(", ", presetList());
    }

    private static String presetName(String lang, Preset p) {
        return ServerText.get(lang, p.nameKey()) + " (" + p.id() + ")";
    }

    /** The settings file as the admin finds it: relative to the server's folder when it is inside it. */
    private static String fileName(ServerSettings s) {
        if (s.file() == null) {
            return ServerSettings.FILE_NAME;
        }
        java.nio.file.Path file = s.file().toAbsolutePath().normalize();
        java.nio.file.Path here = java.nio.file.Paths.get("").toAbsolutePath().normalize();
        return file.startsWith(here) ? here.relativize(file).toString() : file.toString();
    }

    /** The word within two typos of {@code typed}, or null. */
    static String closest(String typed, List<String> words) {
        String best = null;
        int bestDistance = 3;
        for (String w : words) {
            int d = distance(typed, w);
            if (d < bestDistance) {
                best = w;
                bestDistance = d;
            }
        }
        return best;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
