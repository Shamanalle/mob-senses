package com.kasper.mobsenses.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Unbound-by-default keybinding ("Open Mob Senses Settings") for Minecraft 1.16.5 - 1.21.11.
 * <p>
 * Minecraft 1.21.9 replaced {@code KeyMapping}'s {@code String} category parameter with
 * {@code KeyMapping.Category}, and 1.21.11 dropped the legacy constructor completely. Bands compiled
 * against 1.21.8 fall back via reflection on {@link LinkageError}.
 */
public final class SettingsKey {

    public static final String ID = "key.mobsenses.settings";
    public static final String CATEGORY = "key.categories.mobsenses";

    private SettingsKey() {
    }

    public static KeyMapping create() {
        int unknownKey = InputConstants.UNKNOWN.getValue();
        try {
            return new KeyMapping(ID, unknownKey, CATEGORY);
        } catch (LinkageError e) {
            return createWithCategoryObject(ID, unknownKey);
        }
    }

    private static KeyMapping createWithCategoryObject(String name, int unknownKey) {
        try {
            for (Constructor<?> ctor : KeyMapping.class.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 3 && p[0] == String.class && p[1] == int.class && p[2] != String.class) {
                    Object category = findCategory(p[2]);
                    if (category != null) {
                        return (KeyMapping) ctor.newInstance(name, unknownKey, category);
                    }
                }
            }
            for (Constructor<?> ctor : KeyMapping.class.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 4 && p[0] == String.class && p[1] == InputConstants.Type.class
                        && p[2] == int.class && p[3] != String.class) {
                    Object category = findCategory(p[3]);
                    if (category != null) {
                        return (KeyMapping) ctor.newInstance(name, InputConstants.Type.KEYSYM, unknownKey, category);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    private static Object findCategory(Class<?> categoryType) throws IllegalAccessException {
        Object first = null;
        for (Field field : categoryType.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == categoryType) {
                Object value = field.get(null);
                if (value != null && String.valueOf(value).toLowerCase(java.util.Locale.ROOT).contains("misc")) {
                    return value;
                }
                if (first == null) {
                    first = value;
                }
            }
        }
        return first;
    }
}
