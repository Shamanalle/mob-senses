package com.kasper.mobsenses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code /mobsenses}, the same on every loader and on Bukkit: the platform passes the text after the
 * command and sends the {@link Reply} back. Only admins (operators, the console) reach it; the platform
 * checks that.
 */
public final class AdminCommands {

    public static final String NAME = "mobsenses";

    private static final List<String> SUBCOMMANDS = Jv.listOf("status", "preset", "hearing", "sight", "voice", "world", "reload", "help");
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
    }

    private AdminCommands() {
    }

    public static Reply execute(String args, Context ctx) {
        ServerSettings s = MobSenses.settings();
        String lang = Jv.isBlank(ctx.language()) ? s.language() : ctx.language();
        String[] a = args == null || Jv.isBlank(args) ? new String[0] : args.trim().split("\\s+");
        try {
            String sub = a.length == 0 ? "status" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "status":
                    return status(lang, s, ctx);
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
                case "reload":
                    s.load();
                    return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "reload.done", fileName(s)));
                default:
                    return unknown(lang, a[0]);
            }
        } catch (RuntimeException e) {
            ModLog.failure("/" + NAME + " " + args, e);
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "error"));
        }
    }

    private static Reply status(String lang, ServerSettings s, Context ctx) {
        Reply r = new Reply();
        r.add(Reply.Kind.TITLE, ServerText.get(lang, "status.title", BuildInfo.version(), MobSenses.platform()));
        r.add(Reply.Kind.INFO, ServerText.get(lang, "status.preset", presetName(lang, s.preset())));
        for (String world : s.worlds().keySet()) {
            r.add(Reply.Kind.INFO, ServerText.get(lang, "status.world", world, presetName(lang, s.worlds().get(world))));
        }
        r.add(Reply.Kind.INFO, ServerText.get(lang, "status.senses",
                ServerSettings.format(s.hearing()), ServerSettings.format(s.sight())));
        String voice = !s.voice() ? "status.voice.off" : MobSenses.voiceChatInstalled() ? "status.voice.on" : "status.voice.missing";
        r.add(Reply.Kind.INFO, ServerText.get(lang, voice));
        r.add(Reply.Kind.INFO, ServerText.get(lang, "status.noise", MobSenses.noise().lastMinute(System.currentTimeMillis())));
        r.add(Reply.Kind.HINT, ServerText.get(lang, "status.early"));
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
                    .add(Reply.Kind.HINT, ServerText.get(lang, "preset.list", presetIds()));
        }
        Preset p = Preset.byId(a[1]);
        if (p == null) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "preset.unknown", a[1], presetIds()));
        }
        s.setPreset(p);
        s.save();
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "preset.set", presetName(lang, p)));
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
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "multiplier.set", name, ServerSettings.format(value)));
    }

    private static Reply voice(String lang, ServerSettings s, String[] a) {
        if (a.length < 2) {
            return new Reply().add(Reply.Kind.INFO, ServerText.get(lang, s.voice() ? "status.voice.on" : "status.voice.off"))
                    .add(Reply.Kind.HINT, ServerText.get(lang, "help.voice"));
        }
        String v = a[1].toLowerCase(Locale.ROOT);
        if (!v.equals("on") && !v.equals("off")) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "voice.usage"));
        }
        s.setVoice(v.equals("on"));
        s.save();
        Reply r = new Reply().add(Reply.Kind.DONE, ServerText.get(lang, s.voice() ? "voice.on" : "voice.off"));
        if (s.voice() && !MobSenses.voiceChatInstalled()) {
            r.add(Reply.Kind.HINT, ServerText.get(lang, "status.voice.missing"));
        }
        return r;
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
            return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "world.inherit", world, presetName(lang, s.preset())));
        }
        Preset p = Preset.byId(a[2]);
        if (p == null) {
            return new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "preset.unknown", a[2], presetIds() + ", " + INHERIT));
        }
        s.setWorldPreset(world, p);
        s.save();
        return new Reply().add(Reply.Kind.DONE, ServerText.get(lang, "world.set", world, presetName(lang, p)));
    }

    private static Reply unknown(String lang, String typed) {
        Reply r = new Reply().add(Reply.Kind.ERROR, ServerText.get(lang, "unknown", typed));
        String near = closest(typed.toLowerCase(Locale.ROOT), SUBCOMMANDS);
        if (near != null) {
            r.add(Reply.Kind.HINT, ServerText.get(lang, "unknown.suggest", near));
        } else {
            r.add(Reply.Kind.HINT, ServerText.get(lang, "unknown.help"));
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
                default:
                    options = Collections.emptyList();
            }
        } else if (words.length == 3 && words[0].equals("world")) {
            options = new ArrayList<>(presetList());
            options.add(INHERIT);
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
