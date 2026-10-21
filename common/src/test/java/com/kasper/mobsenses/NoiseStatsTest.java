package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoiseStatsTest {

    @Test
    void countsTheLastMinuteOnly() {
        NoiseStats n = new NoiseStats();
        long t = 1_000_000_000L;
        n.record(t);
        n.record(t + 500);
        n.record(t + 30_000);
        assertEquals(3, n.lastMinute(t + 30_000));
        // The first two are older than a minute now
        assertEquals(1, n.lastMinute(t + 61_000));
        assertEquals(0, n.lastMinute(t + 200_000));
        assertEquals(3, n.total());
    }

    @Test
    void anOldBucketIsReusedForANewSecond() {
        NoiseStats n = new NoiseStats();
        long t = 5_000_000L;
        n.record(t);
        n.record(t + 60_000);
        assertEquals(1, n.lastMinute(t + 60_000));
    }
}
