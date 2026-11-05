package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.MobSensesVoicePlugin;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.Bukkit;

/**
 * Registers {@link MobSensesVoicePlugin} with Simple Voice Chat's {@link BukkitVoicechatService}
 * when the Simple Voice Chat plugin is installed on Bukkit / Paper / Folia.
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
}
