package com.kasper.mobsenses.client;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uniform 2D scale push/pop across {@code PoseStack} (1.16.5 - 1.21.5) and
 * {@code Matrix3x2fStack} (1.21.6 - 26.x) via cached reflection.
 */
public final class PoseScaler {

    private static final class Ops {
        final Method poseGetter;
        final Method push;
        final Method pop;
        final Method scale2;
        final Method scale3;

        Ops(Method poseGetter, Method push, Method pop, Method scale2, Method scale3) {
            this.poseGetter = poseGetter;
            this.push = push;
            this.pop = pop;
            this.scale2 = scale2;
            this.scale3 = scale3;
        }
    }

    private static final Map<Class<?>, Ops> CACHE = new ConcurrentHashMap<>();

    private PoseScaler() {
    }

    public static boolean push(Object graphicsOrStack, float factor) {
        if (graphicsOrStack == null) {
            return false;
        }
        Ops ops = CACHE.computeIfAbsent(graphicsOrStack.getClass(), PoseScaler::resolve);
        if (ops == null || ops.push == null || ops.pop == null || (ops.scale2 == null && ops.scale3 == null)) {
            return false;
        }
        try {
            Object stack = ops.poseGetter == null ? graphicsOrStack : ops.poseGetter.invoke(graphicsOrStack);
            if (stack == null) {
                return false;
            }
            ops.push.invoke(stack);
            if (ops.scale2 != null) {
                ops.scale2.invoke(stack, factor, factor);
            } else {
                ops.scale3.invoke(stack, factor, factor, 1.0F);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static void pop(Object graphicsOrStack) {
        if (graphicsOrStack == null) {
            return;
        }
        Ops ops = CACHE.get(graphicsOrStack.getClass());
        if (ops == null || ops.pop == null) {
            return;
        }
        try {
            Object stack = ops.poseGetter == null ? graphicsOrStack : ops.poseGetter.invoke(graphicsOrStack);
            if (stack != null) {
                ops.pop.invoke(stack);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Ops resolve(Class<?> type) {
        Method poseGetter = findPoseGetter(type);
        Class<?> stackType = poseGetter != null ? poseGetter.getReturnType() : type;
        Method push = findZeroArg(stackType, "pushPose", "pushMatrix", "method_22903", "m_85836_");
        Method pop = findZeroArg(stackType, "popPose", "popMatrix", "method_22909", "m_85849_");
        Method scale2 = findMethod(stackType, float.class, float.class);
        Method scale3 = findMethod(stackType, float.class, float.class, float.class);
        return new Ops(poseGetter, push, pop, scale2, scale3);
    }

    private static Method findPoseGetter(Class<?> type) {
        for (String name : new String[]{"pose", "method_51448", "m_280168_"}) {
            try {
                Method m = type.getMethod(name);
                if (m.getParameterCount() == 0 && m.getReturnType() != void.class) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Method findZeroArg(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, Class<?>... params) {
        for (String name : new String[]{"scale", "method_22905", "m_85841_"}) {
            try {
                return type.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
