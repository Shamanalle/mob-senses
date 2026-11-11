package com.kasper.mobsenses.server;

import com.kasper.mobsenses.Reply;
import com.kasper.mobsenses.compat.Txt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Renders {@link Reply.Line}s into Minecraft chat {@link Component}s with colours, clickable
 * {@code [chip]} actions, URL links, and hover tooltips.
 */
public final class ReplyComponents {

    private ReplyComponents() {
    }

    public static Component render(Reply.Line line) {
        if (line == null) {
            return Txt.empty();
        }
        if (!line.hasSpans()) {
            Style style = Style.EMPTY.withColor(color(line.kind(), false));
            if (line.kind() == Reply.Kind.TITLE) {
                style = style.withBold(true);
            }
            return Txt.empty().append(ChatLink.of(line.text())).withStyle(style);
        }
        MutableComponent out = Txt.literal("");
        for (Reply.Span span : line.spans()) {
            Style style = Style.EMPTY.withColor(color(span.kind(), span.clickable()));
            if (span.kind() == Reply.Kind.TITLE) {
                style = style.withBold(true);
            }
            if (span.clickable()) {
                ClickEvent click = ChatLink.click(actionName(span.action()), span.value());
                if (click != null) {
                    style = style.withClickEvent(click);
                }
                if (span.action() == Reply.Action.OPEN_URL && !span.text().startsWith("[")) {
                    style = style.withUnderlined(true);
                }
            }
            if (span.hover() != null && !span.hover().isEmpty()) {
                HoverEvent hover = ChatLink.hover(Txt.literal(span.hover()).withStyle(ChatFormatting.GRAY));
                if (hover != null) {
                    style = style.withHoverEvent(hover);
                }
            }
            out.append(Txt.literal(span.text()).withStyle(style));
        }
        return out;
    }

    private static ChatFormatting color(Reply.Kind kind, boolean clickable) {
        if (kind == null) {
            return clickable ? ChatFormatting.AQUA : ChatFormatting.WHITE;
        }
        switch (kind) {
            case TITLE:
                return ChatFormatting.GOLD;
            case DONE:
                return ChatFormatting.GREEN;
            case ERROR:
                return ChatFormatting.RED;
            case HINT:
                return clickable ? ChatFormatting.AQUA : ChatFormatting.GRAY;
            default:
                return clickable ? ChatFormatting.AQUA : ChatFormatting.WHITE;
        }
    }

    private static String actionName(Reply.Action action) {
        switch (action) {
            case RUN_COMMAND:
                return "run_command";
            case SUGGEST_COMMAND:
                return "suggest_command";
            case OPEN_URL:
            default:
                return "open_url";
        }
    }
}
