package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.AdminCommands;
import com.kasper.mobsenses.Jv;
import com.kasper.mobsenses.LinkProtocol;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.Reply;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The Bukkit / Spigot / Paper / Purpur / Folia plugin: full server-side parity with the mod through
 * the Bukkit and Paper APIs, including {@link LinkProtocol} custom payload channels ({@code mobsenses:hello},
 * {@code mobsenses:state}, {@code mobsenses:admin}, {@code mobsenses:admin_reply}), rich Adventure chat
 * replies, Simple Voice Chat integration, {@code mobsenses.bypass} staff silence, and Folia region-safe
 * scheduling.
 */
public final class MobSensesBukkit extends JavaPlugin implements Listener {

    private static final int STATE_CHECK_TICKS = 10;
    private static final int TELEMETRY_PUSH_TICKS = 100;

    /** Online modded players who completed the {@code mobsenses:hello} handshake (UUID -> client language). */
    private final Map<UUID, String> moddedPlayers = new ConcurrentHashMap<>();
    /** Last synced state fingerprint per modded player so world/permission/HUD changes push immediately. */
    private final Map<UUID, String> lastStateFingerprints = new ConcurrentHashMap<>();

    private volatile Scheduling scheduling;
    private int tickCounter;

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
        TacticalBukkitListener.register(this);

        Bukkit.getMessenger().registerOutgoingPluginChannel(this, LinkProtocol.STATE_CHANNEL);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, LinkProtocol.ADMIN_REPLY_CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, LinkProtocol.HELLO_CHANNEL,
                (channel, player, message) -> onHello(player, message));
        Bukkit.getMessenger().registerIncomingPluginChannel(this, LinkProtocol.ADMIN_CHANNEL,
                (channel, player, message) -> onAdmin(player, message));
        Bukkit.getPluginManager().registerEvents(this, this);

        MobSenses.setSettingsListener(this::onSettingsChanged);

        Scheduling sched = hasClass("io.papermc.paper.threadedregions.RegionizedServer")
                ? (Scheduling) modern("FoliaScheduling", "create", null) : new BukkitScheduling();
        this.scheduling = sched;
        if (sched != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                sched.onPlayer(this, online, () -> refreshPlayerState(online, true));
            }
            sched.everyTick(this, () -> {
                BukkitSenses.tick(this, sched);
                MobSenses.serverTick();
                int t = ++tickCounter;
                if ((t % STATE_CHECK_TICKS) == 0 && !moddedPlayers.isEmpty()) {
                    boolean forceTelemetry = (t % TELEMETRY_PUSH_TICKS) == 0;
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (moddedPlayers.containsKey(online.getUniqueId())) {
                            sched.onPlayer(this, online, () -> refreshPlayerState(online, forceTelemetry));
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onDisable() {
        MobSenses.setSettingsListener(null);
        Scheduling sched = this.scheduling;
        if (sched != null) {
            sched.cancelAll(this);
        }
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this);
        moddedPlayers.clear();
        lastStateFingerprints.clear();
        BukkitSenses.clear();
        MobSenses.coalescer().clear();
        MobSenses.scent().clear();
        MobSenses.voice().clear();
    }

    private void onSettingsChanged() {
        broadcastStateToModdedPlayers();
    }

    private void broadcastStateToModdedPlayers() {
        Scheduling sched = this.scheduling;
        if (sched == null || moddedPlayers.isEmpty()) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (moddedPlayers.containsKey(online.getUniqueId())) {
                sched.onPlayer(this, online, () -> refreshPlayerState(online, true));
            }
        }
    }

    private void onHello(Player player, byte[] message) {
        if (player == null) {
            return;
        }
        LinkProtocol.Hello hello = LinkProtocol.decodeHello(message);
        if (hello == null) {
            return;
        }
        moddedPlayers.put(player.getUniqueId(), hello.language());
        Scheduling sched = this.scheduling;
        if (sched != null) {
            sched.onPlayer(this, player, () -> refreshPlayerState(player, true));
        }
    }

    private void onAdmin(Player player, byte[] message) {
        if (player == null) {
            return;
        }
        Properties reqProps = LinkProtocol.decodePacket(message);
        if (reqProps == null) {
            return;
        }
        Scheduling sched = this.scheduling;
        if (sched == null) {
            return;
        }
        sched.onPlayer(this, player, () -> {
            AdminCommands.Context ctx = context(player);
            Properties replyProps = LinkProtocol.executeAdmin(reqProps, ctx);
            byte[] replyBytes = LinkProtocol.encodePacket(replyProps);
            player.sendPluginMessage(this, LinkProtocol.ADMIN_REPLY_CHANNEL, replyBytes);
            broadcastStateToModdedPlayers();
        });
    }

    private void refreshPlayerState(Player player, boolean forceState) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!moddedPlayers.containsKey(id)) {
            return;
        }
        World w = player.getWorld();
        if (w == null) {
            return;
        }
        String worldName = w.getName();
        boolean admin = isAdmin(player);
        long now = System.currentTimeMillis();
        int playerAlert = MobSenses.playerAlertLevel(id, now);
        String fp = worldName + '|' + admin + '|' + MobSenses.debug()
                + '|' + MobSenses.settings().allowClientHud()
                + '|' + MobSenses.settings().presetOf(worldName).id()
                + '|' + playerAlert;
        String prevFp = lastStateFingerprints.put(id, fp);
        if (forceState || !fp.equals(prevFp)) {
            sendState(player, worldName, admin, now, playerAlert);
        }
    }

    private void sendState(Player player, String worldName, boolean admin, long now, int playerAlert) {
        byte[] bytes = LinkProtocol.encodePacket(LinkProtocol.buildState(
                MobSenses.settings(),
                worldName,
                admin,
                MobSenses.debug(),
                MobSenses.voiceChatInstalled(),
                MobSenses.noise().lastMinute(now),
                MobSenses.alerted().lastMinute(now),
                MobSenses.platform().toString(),
                playerAlert));
        player.sendPluginMessage(this, LinkProtocol.STATE_CHANNEL, bytes);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Scheduling sched = this.scheduling;
        Player p = event.getPlayer();
        if (sched != null) {
            sched.onPlayer(this, p, () -> refreshPlayerState(p, false));
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Scheduling sched = this.scheduling;
        Player p = event.getPlayer();
        if (sched != null) {
            sched.onPlayer(this, p, () -> refreshPlayerState(p, true));
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Scheduling sched = this.scheduling;
        Player p = event.getPlayer();
        if (sched != null) {
            sched.onPlayer(this, p, () -> refreshPlayerState(p, true));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        moddedPlayers.remove(id);
        lastStateFingerprints.remove(id);
        BukkitSenses.forgetPlayer(id);
        MobSenses.clearPlayerAlert(id);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isAdmin(sender)) {
            ReplyAdventure.send(sender, new Reply.Line(Reply.Kind.ERROR, "You do not have permission to use this command."));
            return true;
        }
        Reply reply = AdminCommands.execute(String.join(" ", args), context(sender));
        for (Reply.Line line : reply.lines()) {
            ReplyAdventure.send(sender, line);
        }
        if (args.length > 0 && ("debug".equalsIgnoreCase(args[0]) || "reload".equalsIgnoreCase(args[0]))) {
            broadcastStateToModdedPlayers();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!isAdmin(sender)) {
            return Collections.emptyList();
        }
        return AdminCommands.suggestions(String.join(" ", args), context(sender));
    }

    String resolveLanguage(Player player) {
        if (player == null) {
            return MobSenses.settings().language();
        }
        String loc = locale(player);
        if (Jv.isBlank(loc)) {
            loc = moddedPlayers.get(player.getUniqueId());
        }
        return Jv.isBlank(loc) ? MobSenses.settings().language() : loc;
    }

    static boolean isAdmin(CommandSender sender) {
        if (sender == null) {
            return false;
        }
        if (!(sender instanceof Player)) {
            return true;
        }
        return sender.isOp() || sender.hasPermission(AdminCommands.PERM_ADMIN);
    }

    private AdminCommands.Context context(final CommandSender sender) {
        final Player player = sender instanceof Player ? (Player) sender : null;
        final String language = player != null ? resolveLanguage(player) : "";
        final String world;
        if (player != null) {
            Location loc = player.getLocation();
            world = loc.getWorld() != null ? loc.getWorld().getName() : player.getWorld().getName();
        } else {
            world = null;
        }
        final List<String> worlds = new ArrayList<>();
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

            @Override
            public boolean hasPermission(String node) {
                return isAdmin(sender);
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
