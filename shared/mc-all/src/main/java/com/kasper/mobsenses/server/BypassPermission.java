package com.kasper.mobsenses.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional permission check for {@code mobsenses.bypass} across Fabric, Forge, and NeoForge.
 * <p>
 * Queries Fabric Permissions API ({@code me.lucko.fabric.api.permissions.v0.Permissions}) or
 * LuckPerms API ({@code net.luckperms.api.LuckPermsProvider}) via reflection when installed,
 * defaulting strictly to {@code false} (never granted automatically to operators).
 */
public final class BypassPermission {

    public static final String NODE = "mobsenses.bypass";

    private static volatile boolean initialized;
    private static Method fabricPermissionsCheck;
    private static Method luckPermsGet;
    private static Method lpGetUserManager;
    private static Method lpGetUser;
    private static Method lpGetCachedData;
    private static Method lpGetPermissionData;
    private static Method lpCheckPermission;
    private static Method lpAsBoolean;

    private BypassPermission() {
    }

    public static boolean hasBypass(Player player) {
        if (player == null) {
            return false;
        }
        ensureInitialized();
        if (fabricPermissionsCheck != null) {
            try {
                Object res = fabricPermissionsCheck.invoke(null, player, NODE, Boolean.FALSE);
                if (res instanceof Boolean && (Boolean) res) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (luckPermsGet != null) {
            try {
                Object api = luckPermsGet.invoke(null);
                if (api != null) {
                    Object userMgr = lpGetUserManager.invoke(api);
                    Object user = userMgr != null ? lpGetUser.invoke(userMgr, player.getUUID()) : null;
                    if (user != null) {
                        Object cached = lpGetCachedData.invoke(user);
                        Object permData = cached != null ? lpGetPermissionData.invoke(cached) : null;
                        Object tristate = permData != null ? lpCheckPermission.invoke(permData, NODE) : null;
                        if (tristate != null) {
                            Object boolVal = lpAsBoolean.invoke(tristate);
                            if (boolVal instanceof Boolean) {
                                return (Boolean) boolVal;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (BypassPermission.class) {
            if (initialized) {
                return;
            }
            try {
                Class<?> perms = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
                fabricPermissionsCheck = perms.getMethod("check", Entity.class, String.class, boolean.class);
            } catch (Throwable ignored) {
            }
            try {
                Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
                Class<?> apiCls = Class.forName("net.luckperms.api.LuckPerms");
                Class<?> userMgrCls = Class.forName("net.luckperms.api.model.user.UserManager");
                Class<?> userCls = Class.forName("net.luckperms.api.model.user.User");
                Class<?> cachedCls = Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
                Class<?> permDataCls = Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
                Class<?> tristateCls = Class.forName("net.luckperms.api.util.Tristate");

                luckPermsGet = provider.getMethod("get");
                lpGetUserManager = apiCls.getMethod("getUserManager");
                lpGetUser = userMgrCls.getMethod("getUser", UUID.class);
                lpGetCachedData = userCls.getMethod("getCachedData");
                lpGetPermissionData = cachedCls.getMethod("getPermissionData");
                lpCheckPermission = permDataCls.getMethod("checkPermission", String.class);
                lpAsBoolean = tristateCls.getMethod("asBoolean");
            } catch (Throwable ignored) {
            }
            initialized = true;
        }
    }
}
