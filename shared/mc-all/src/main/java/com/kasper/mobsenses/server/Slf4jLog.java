package com.kasper.mobsenses.server;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** The mod's log on the mod loaders (Log4j 2 ships with every Minecraft version from 1.16.5 to 26.x). */
public final class Slf4jLog implements ModLog.Sink {

    private final Logger log = LogManager.getLogger(MobSenses.NAME);

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
