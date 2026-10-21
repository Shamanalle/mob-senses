package com.kasper.mobsenses.server;

import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.MobSenses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's log on the mod loaders, which all ship SLF4J with Minecraft. */
public final class Slf4jLog implements ModLog.Sink {

    private final Logger log = LoggerFactory.getLogger(MobSenses.NAME);

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void warn(String message, Throwable error) {
        if (error == null) {
            log.warn(message);
        } else {
            log.warn(message, error);
        }
    }
}
