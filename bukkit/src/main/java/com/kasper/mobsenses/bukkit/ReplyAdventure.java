package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.Reply;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

/**
 * Renders {@link Reply.Line}s on Bukkit / Spigot / Paper / Purpur / Folia:
 * <ul>
 *   <li>On {@link ConsoleCommandSender}, sends clean plain text ({@link Reply.Line#text()}) so console logs
 *       and automated smoke tests stay free of interactive chat button clutter.</li>
 *   <li>On Paper/Purpur/Folia, sends rich Adventure {@link Component}s with clickable {@code [chip]} buttons
 *       ({@code RUN_COMMAND}, {@code SUGGEST_COMMAND}, {@code OPEN_URL}) and hover tooltips.</li>
 *   <li>On pure Spigot (without Adventure), falls back cleanly to {@link ChatColor} legacy strings.</li>
 * </ul>
 */
public final class ReplyAdventure {

    private ReplyAdventure() {
    }

    public static void send(CommandSender to, Reply.Line line) {
        if (to == null || line == null) {
            return;
        }
        if (to instanceof ConsoleCommandSender) {
            to.sendMessage(line.text());
            return;
        }
        try {
            Rich.send(to, line);
        } catch (LinkageError e) {
            to.sendMessage(legacy(line));
        }
    }

    @SuppressWarnings("deprecation")
    static String legacy(Reply.Line line) {
        if (!line.hasSpans()) {
            return legacyColor(line.kind(), false) + line.text();
        }
        StringBuilder b = new StringBuilder();
        for (Reply.Span span : line.spans()) {
            b.append(legacyColor(span.kind(), span.clickable())).append(span.text());
        }
        return b.toString();
    }

    @SuppressWarnings("deprecation")
    private static String legacyColor(Reply.Kind kind, boolean clickable) {
        if (kind == null) {
            return clickable ? ChatColor.AQUA.toString() : ChatColor.WHITE.toString();
        }
        switch (kind) {
            case TITLE:
                return ChatColor.GOLD.toString() + ChatColor.BOLD;
            case DONE:
                return ChatColor.GREEN.toString();
            case ERROR:
                return ChatColor.RED.toString();
            case HINT:
                return clickable ? ChatColor.AQUA.toString() : ChatColor.GRAY.toString();
            default:
                return clickable ? ChatColor.AQUA.toString() : ChatColor.WHITE.toString();
        }
    }

    /** Isolated Adventure calls so pure Spigot only fails loading this nested class. */
    private static final class Rich {

        static void send(CommandSender to, Reply.Line line) {
            if (!line.hasSpans()) {
                Component single = Component.text(line.text(), color(line.kind(), false));
                if (line.kind() == Reply.Kind.TITLE) {
                    single = single.decorate(TextDecoration.BOLD);
                }
                to.sendMessage(single);
                return;
            }
            TextComponent.Builder out = Component.text();
            for (Reply.Span span : line.spans()) {
                Component c = Component.text(span.text(), color(span.kind(), span.clickable()));
                if (span.kind() == Reply.Kind.TITLE) {
                    c = c.decorate(TextDecoration.BOLD);
                }
                if (span.clickable()) {
                    switch (span.action()) {
                        case RUN_COMMAND:
                            c = c.clickEvent(ClickEvent.runCommand(span.value()));
                            break;
                        case SUGGEST_COMMAND:
                            c = c.clickEvent(ClickEvent.suggestCommand(span.value()));
                            break;
                        case OPEN_URL:
                            c = c.clickEvent(ClickEvent.openUrl(span.value()));
                            if (!span.text().startsWith("[")) {
                                c = c.decorate(TextDecoration.UNDERLINED);
                            }
                            break;
                        default:
                            break;
                    }
                }
                if (span.hover() != null && !span.hover().isEmpty()) {
                    c = c.hoverEvent(HoverEvent.showText(Component.text(span.hover(), NamedTextColor.GRAY)));
                }
                out.append(c);
            }
            to.sendMessage(out.build());
        }

        private static NamedTextColor color(Reply.Kind kind, boolean clickable) {
            if (kind == null) {
                return clickable ? NamedTextColor.AQUA : NamedTextColor.WHITE;
            }
            switch (kind) {
                case TITLE:
                    return NamedTextColor.GOLD;
                case DONE:
                    return NamedTextColor.GREEN;
                case ERROR:
                    return NamedTextColor.RED;
                case HINT:
                    return clickable ? NamedTextColor.AQUA : NamedTextColor.GRAY;
                default:
                    return clickable ? NamedTextColor.AQUA : NamedTextColor.WHITE;
            }
        }
    }
}
