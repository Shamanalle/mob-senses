package com.kasper.mobsenses.server;

import com.kasper.mobsenses.Jv;
import com.kasper.mobsenses.compat.Txt;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds Minecraft {@link ClickEvent} and {@link HoverEvent} instances across both pre-1.21.5
 * (class + action enum/constant) and 1.21.5+ (sealed interface + nested records) without binary
 * incompatibilities across obfuscated or Mojang mappings.
 */
public final class ChatLink {

    private static final Pattern URL = Pattern.compile("https?://\\S+[^\\s.,;:!?)\\]]");
    private static volatile boolean failed;
    private static volatile boolean hoverFailed;

    private ChatLink() {
    }

    public static Component of(String text) {
        if (text == null || text.isEmpty()) {
            return Txt.literal("");
        }
        Matcher m = URL.matcher(text);
        if (failed || !m.find()) {
            return Txt.literal(text);
        }
        MutableComponent out = Txt.literal("");
        int last = 0;
        do {
            out.append(Txt.literal(text.substring(last, m.start())));
            String url = m.group();
            ClickEvent click = openUrl(url);
            MutableComponent link = Txt.literal(url);
            if (click != null) {
                link = link.withStyle(Style.EMPTY.withClickEvent(click).withUnderlined(true));
            }
            out.append(link);
            last = m.end();
        } while (m.find());
        out.append(Txt.literal(text.substring(last)));
        return out;
    }

    private static ClickEvent openUrl(String url) {
        ClickEvent click = make("open_url", url);
        if (click == null) {
            failed = true;
        }
        return click;
    }

    public static ClickEvent click(String action, String value) {
        return failed ? null : make(action, value);
    }

    public static HoverEvent hover(Component text) {
        if (hoverFailed || text == null) {
            return null;
        }
        try {
            if (HoverEvent.class.isInterface()) {
                for (Class<?> nested : HoverEvent.class.getDeclaredClasses()) {
                    Class<?>[] parts = Jv.recordTypes(nested);
                    if (parts != null && parts.length == 1 && parts[0] == Component.class) {
                        Constructor<?> c = nested.getDeclaredConstructor(Component.class);
                        c.setAccessible(true);
                        return (HoverEvent) c.newInstance(text);
                    }
                }
            } else {
                for (Class<?> nested : HoverEvent.class.getDeclaredClasses()) {
                    Object showText = constantNamed(nested, "show_text");
                    if (showText == null) {
                        continue;
                    }
                    for (Constructor<?> c : HoverEvent.class.getDeclaredConstructors()) {
                        Class<?>[] types = c.getParameterTypes();
                        if (types.length == 2 && types[0] == nested) {
                            c.setAccessible(true);
                            return (HoverEvent) c.newInstance(showText, text);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        hoverFailed = true;
        return null;
    }

    private static Object constantNamed(Class<?> type, String name) {
        for (Field f : type.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != type) {
                continue;
            }
            try {
                f.setAccessible(true);
                Object constant = f.get(null);
                for (Method method : type.getMethods()) {
                    if (method.getParameterCount() == 0 && method.getReturnType() == String.class
                            && !Modifier.isStatic(method.getModifiers())
                            && name.equals(method.invoke(constant))) {
                        return constant;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static ClickEvent make(String action, String value) {
        try {
            if (ClickEvent.class.isInterface()) {
                for (Class<?> nested : ClickEvent.class.getDeclaredClasses()) {
                    Class<?>[] parts = Jv.recordTypes(nested);
                    if (parts == null || parts.length != 1) {
                        continue;
                    }
                    Object arg = parts[0] == URI.class ? URI.create(value)
                            : parts[0] == String.class ? value : null;
                    if (arg == null) {
                        continue;
                    }
                    Constructor<?> c = nested.getDeclaredConstructor(parts[0]);
                    c.setAccessible(true);
                    Object event = c.newInstance(arg);
                    if (hasAction(event, action)) {
                        return (ClickEvent) event;
                    }
                }
            } else {
                for (Constructor<?> c : ClickEvent.class.getDeclaredConstructors()) {
                    Class<?>[] types = c.getParameterTypes();
                    if (types.length == 2 && types[0].isEnum() && types[1] == String.class) {
                        Object constant = actionNamed(types[0], action);
                        if (constant != null) {
                            c.setAccessible(true);
                            return (ClickEvent) c.newInstance(constant, value);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean hasAction(Object event, String action) {
        for (Method method : event.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType().isEnum()) {
                try {
                    Object constant = method.invoke(event);
                    if (constant instanceof Enum<?> && actionNamed(((Enum<?>) constant).getDeclaringClass(), action) == constant) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static Object actionNamed(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                return constant;
            }
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == String.class) {
                    try {
                        if (name.equals(method.invoke(constant))) {
                            return constant;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }
}
