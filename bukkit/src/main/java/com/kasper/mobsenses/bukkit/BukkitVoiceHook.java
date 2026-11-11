package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.MobSensesVoicePlugin;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Registers {@link MobSensesVoicePlugin} with Simple Voice Chat's {@link BukkitVoicechatService}
 * when the Simple Voice Chat plugin is installed on Bukkit / Paper / Folia.
 * <p>
 * Players holding {@code mobsenses.bypass} (or in Spectator mode) have their voice frames discarded
 * without emitting {@code MobSenses.voiceEvent} (see {@link #shouldEmitVoice(Player)}).
 */
final class BukkitVoiceHook {

    private BukkitVoiceHook() {
    }

    static void register() {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            service.registerPlugin(new MobSensesVoicePlugin());
            MobSenses.markVoiceChatActive(true);
        }
    }

    static boolean shouldEmitVoice(Player player) {
        return player != null && !BukkitSenses.isBypassed(player);
    }
}
