package com.kasper.mobsenses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The answer to a command: lines of text, each of a kind the platform colours its own way (chat
 * components with interactive clickable spans on the mod loaders and Paper, colour codes on legacy
 * Bukkit, plain text in the console and GUI footer).
 */
public final class Reply {

    public enum Kind {
        /** The first line of a status or help. */
        TITLE,
        /** An ordinary line. */
        INFO,
        /** A change that was made. */
        DONE,
        /** A command that was refused. */
        ERROR,
        /** A hint: an example, a suggestion, or interactive chips. */
        HINT
    }

    /** Interactive click action on a {@link Span}. */
    public enum Action {
        /** Immediately runs a command (e.g. {@code /mobsenses preset hardcore}). */
        RUN_COMMAND,
        /** Places a command prefix into the player's chat input box. */
        SUGGEST_COMMAND,
        /** Opens an external URL in the player's browser. */
        OPEN_URL
    }

    /**
     * One styled or interactive segment within a {@link Line}.
     *
     * @param text   visible text of the span
     * @param kind   colour/style category of the span
     * @param action optional click action, or {@code null} for plain text
     * @param value  command string or URL for {@code action}, or {@code null}
     * @param hover  optional tooltip text shown on hover, or {@code null}
     */
    @com.github.bsideup.jabel.Desugar
    public record Span(String text, Kind kind, Action action, String value, String hover) {

        public Span {
            text = text == null ? "" : text;
            kind = kind == null ? Kind.INFO : kind;
        }

        public Span(String text, Kind kind) {
            this(text, kind, null, null, null);
        }

        public boolean clickable() {
            return action != null && value != null && !value.isEmpty();
        }

        public static Span run(String text, Kind kind, String command, String hover) {
            return new Span(text, kind, Action.RUN_COMMAND, command, hover);
        }

        public static Span suggest(String text, Kind kind, String command, String hover) {
            return new Span(text, kind, Action.SUGGEST_COMMAND, command, hover);
        }

        public static Span url(String text, Kind kind, String url, String hover) {
            return new Span(text, kind, Action.OPEN_URL, url, hover);
        }
    }

    /**
     * One reply line: {@code text} is the clean plain-text form (used by the console, {@link #toString()},
     * and the GUI status footer), while {@code spans} holds rich clickable segments for in-game chat when
     * non-empty.
     */
    @com.github.bsideup.jabel.Desugar
    public record Line(Kind kind, String text, List<Span> spans) {

        public Line {
            kind = kind == null ? Kind.INFO : kind;
            text = text == null ? "" : text;
            spans = spans == null || spans.isEmpty() ? Collections.<Span>emptyList() : Jv.copyOf(spans);
        }

        public Line(Kind kind, String text) {
            this(kind, text, Collections.<Span>emptyList());
        }

        public boolean hasSpans() {
            return !spans.isEmpty();
        }
    }

    private final List<Line> lines = new ArrayList<>();

    public Reply add(Kind kind, String text) {
        lines.add(new Line(kind, text));
        return this;
    }

    public Reply add(Line line) {
        if (line != null) {
            lines.add(line);
        }
        return this;
    }

    /**
     * Adds a line whose plain {@link Line#text()} stays untouched (for console logs and smoke tests) while
     * attaching trailing interactive chips in {@link Line#spans()} for players in chat.
     */
    public Reply addWithChips(Kind kind, String plainText, List<Span> chips) {
        if (chips == null || chips.isEmpty()) {
            return add(kind, plainText);
        }
        List<Span> spans = new ArrayList<>(chips.size() * 2 + 1);
        if (plainText != null && !plainText.isEmpty()) {
            spans.add(new Span(plainText, kind));
        }
        for (Span chip : chips) {
            if (chip == null || chip.text().isEmpty()) {
                continue;
            }
            if (!spans.isEmpty()) {
                spans.add(new Span(" ", Kind.HINT));
            }
            spans.add(chip);
        }
        lines.add(new Line(kind, plainText, spans));
        return this;
    }

    /**
     * Adds a dedicated {@link Kind#HINT} chip row whose plain text and spans both list the chips
     * separated by spaces (e.g. {@code [«] [»]}).
     */
    public Reply addChipRow(List<Span> chips) {
        if (chips == null || chips.isEmpty()) {
            return this;
        }
        StringBuilder plain = new StringBuilder();
        List<Span> spans = new ArrayList<>(chips.size() * 2);
        for (Span chip : chips) {
            if (chip == null || chip.text().isEmpty()) {
                continue;
            }
            if (!spans.isEmpty()) {
                plain.append(' ');
                spans.add(new Span(" ", Kind.HINT));
            }
            plain.append(chip.text());
            spans.add(chip);
        }
        if (!spans.isEmpty()) {
            lines.add(new Line(Kind.HINT, plain.toString(), spans));
        }
        return this;
    }

    /** Builds a {@code [label]} chip that runs {@code command} when clicked. */
    public static Span runChip(String label, String command) {
        return new Span("[" + label + "]", Kind.HINT, Action.RUN_COMMAND, command, command);
    }

    /** Builds a {@code [label]} chip with custom kind (e.g. {@link Kind#DONE} for active choice) that runs {@code command}. */
    public static Span runChip(String label, Kind kind, String command, String hover) {
        return new Span("[" + label + "]", kind, Action.RUN_COMMAND, command, hover != null ? hover : command);
    }

    /** Builds a {@code [label]} chip that suggests {@code command} in chat when clicked. */
    public static Span suggestChip(String label, String command) {
        return new Span("[" + label + "]", Kind.HINT, Action.SUGGEST_COMMAND, command, command);
    }

    /** Builds a {@code [label]} chip that opens {@code url} when clicked. */
    public static Span urlChip(String label, String url) {
        return new Span("[" + label + "]", Kind.HINT, Action.OPEN_URL, url, url);
    }

    /** Builds clickable {@code [off] [subtle] [default] [hardcore]} chips for {@code /mobsenses preset}. */
    public static List<Span> presetChips(Preset active) {
        List<Span> chips = new ArrayList<>(Preset.values().length);
        for (Preset p : Preset.values()) {
            String cmd = "/mobsenses preset " + p.id();
            Kind k = p == active ? Kind.DONE : Kind.HINT;
            chips.add(runChip(p.id(), k, cmd, cmd));
        }
        return chips;
    }

    /** Builds clickable {@code [on] [off]} chips for a toggle subcommand (e.g. {@code voice} or {@code debug}). */
    public static List<Span> toggleChips(String subcommand, boolean currentlyOn) {
        String onCmd = "/mobsenses " + subcommand + " on";
        String offCmd = "/mobsenses " + subcommand + " off";
        return Jv.listOf(
                runChip("on", currentlyOn ? Kind.DONE : Kind.HINT, onCmd, onCmd),
                runChip("off", !currentlyOn ? Kind.DONE : Kind.HINT, offCmd, offCmd));
    }

    /** Builds clickable {@code [true] [false]} chips for {@code /mobsenses set <key>}. */
    public static List<Span> boolChips(String optionKey, boolean currentlyTrue) {
        String tCmd = "/mobsenses set " + optionKey + " true";
        String fCmd = "/mobsenses set " + optionKey + " false";
        return Jv.listOf(
                runChip("true", currentlyTrue ? Kind.DONE : Kind.HINT, tCmd, tCmd),
                runChip("false", !currentlyTrue ? Kind.DONE : Kind.HINT, fCmd, fCmd));
    }

    public List<Line> lines() {
        return Collections.unmodifiableList(lines);
    }

    /** First plain-text line of this reply (ideal for single-line GUI status footers). */
    public String summaryText() {
        return lines.isEmpty() ? "" : lines.get(0).text();
    }

    /** Whether the command was refused (its first line is an error). */
    public boolean refused() {
        return !lines.isEmpty() && lines.get(0).kind() == Kind.ERROR;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (Line l : lines) {
            b.append(l.text()).append('\n');
        }
        return b.toString();
    }
}
