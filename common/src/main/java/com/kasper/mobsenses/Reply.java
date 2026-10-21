package com.kasper.mobsenses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The answer to a command: lines of text, each of a kind the platform colours its own way (chat
 * components on the mod loaders, colour codes on Bukkit, plain text in the console).
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
        /** A hint: an example, a suggestion. */
        HINT
    }

    /** One line. */
    @com.github.bsideup.jabel.Desugar
    public record Line(Kind kind, String text) {
    }

    private final List<Line> lines = new ArrayList<>();

    public Reply add(Kind kind, String text) {
        lines.add(new Line(kind, text));
        return this;
    }

    public List<Line> lines() {
        return Collections.unmodifiableList(lines);
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
