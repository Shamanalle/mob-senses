package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.AdminCommands;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.Reply;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * The Bukkit / Spigot / Paper / Purpur / Folia plugin: full server-side parity with the mod through
 * the Bukkit and Paper APIs. Game events come from {@code GenericGameEvent} on 1.17+ (see
 * {@link GameEventListener}) and from standard Bukkit events on 1.16.5 (see {@link LegacyNoiseListeners}).
 */
public final class MobSensesBukkit extends JavaPlugin {

    @Override
    public void onEnable() {
        ModLog.use(new ModLog.Sink() {
            @Override
            public void info(String message) {
                getLogger().info(message);
            }

            @Override
            public void warn(String message, Throwable error) {
                getLogger().log(Level.WARNING, message, error);
            }
        });
        boolean hasVoiceChat = Bukkit.getPluginManager().getPlugin("voicechat") != null;
        MobSenses.start(getDataFolder().toPath(), new MobSenses.Platform(Bukkit.getName(), minecraftVersion()),
                hasVoiceChat);
        if (hasVoiceChat && hasClass("de.maxhenkel.voicechat.api.BukkitVoicechatService")) {
            try {
                BukkitVoiceHook.register();
            } catch (Throwable t) {
                ModLog.failure("Registering Simple Voice Chat plugin", t);
            }
        }
        if (hasClass("org.bukkit.event.world.GenericGameEvent")) {
            modern("GameEventListener", "register", this);
        } else {
            LegacyNoiseListeners.register(this);
        }
        Scheduling scheduling = hasClass("io.papermc.paper.threadedregions.RegionizedServer")
                ? (Scheduling) modern("FoliaScheduling", "create", null) : new BukkitScheduling();
        if (scheduling != null) {
            scheduling.everyTick(this, () -> {
                BukkitSenses.tick(this, scheduling);
                MobSenses.serverTick();
            });
        }
    }

    @Override
    public void onDisable() {
        BukkitSenses.clear();
        MobSenses.coalescer().clear();
        MobSenses.scent().clear();
        MobSenses.voice().clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Reply reply = AdminCommands.execute(String.join(" ", args), context(sender));
        for (Reply.Line line : reply.lines()) {
            sender.sendMessage(color(line.kind()) + line.text());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return AdminCommands.suggestions(String.join(" ", args), context(sender));
    }

    private static AdminCommands.Context context(CommandSender sender) {
        Player player = sender instanceof Player ? (Player) sender : null;
        String language = player == null ? "" : locale(player);
        String world = player == null ? null : player.getWorld().getName();
        List<String> worlds = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            worlds.add(w.getName());
        }
        return new AdminCommands.Context() {
            @Override
            public String language() {
                return language;
            }

            @Override
            public String world() {
                return world;
            }

            @Override
            public List<String> worlds() {
                return worlds;
            }

            @Override
            public boolean isDedicatedServer() {
                return true;
            }
        };
    }

    @SuppressWarnings("deprecation")
    private static String locale(Player player) {
        try {
            return player.getLocale();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String color(Reply.Kind kind) {
        switch (kind) {
            case TITLE:
                return ChatColor.GOLD.toString();
            case DONE:
                return ChatColor.GREEN.toString();
            case ERROR:
                return ChatColor.RED.toString();
            case HINT:
                return ChatColor.GRAY.toString();
            default:
                return ChatColor.WHITE.toString();
        }
    }

    /** "1.20.1" out of "1.20.1-R0.1-SNAPSHOT". */
    private static String minecraftVersion() {
        String v = Bukkit.getBukkitVersion();
        int dash = v.indexOf('-');
        return dash > 0 ? v.substring(0, dash) : v;
    }

    /**
     * Calls a static method of a class of the "modern" source set (compiled against a newer API, so the
     * rest of the plugin may not name it). {@code plugin} is its argument, or null for none.
     */
    private Object modern(String className, String method, JavaPlugin plugin) {
        try {
            Class<?> cls = Class.forName("com.kasper.mobsenses.bukkit." + className);
            return plugin == null ? cls.getMethod(method).invoke(null)
                    : cls.getMethod(method, org.bukkit.plugin.Plugin.class).invoke(null, plugin);
        } catch (ReflectiveOperationException | LinkageError e) {
            ModLog.failure("Starting " + className, e);
            return null;
        }
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
