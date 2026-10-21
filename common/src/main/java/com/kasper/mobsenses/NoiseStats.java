package com.kasper.mobsenses;

/**
 * How much noise the server heard lately, for {@code /mobsenses status}: game events counted in
 * one-second buckets over the last minute. Safe to call from any thread (Folia runs regions on several).
 */
public final class NoiseStats {

    private static final int SECONDS = 60;

    private final long[] buckets = new long[SECONDS];
    private final long[] bucketSecond = new long[SECONDS];
    private long total;

    /** One game event at {@code nowMillis}. */
    public synchronized void record(long nowMillis) {
        long second = nowMillis / 1000L;
        int i = (int) (second % SECONDS);
        if (bucketSecond[i] != second) {
            bucketSecond[i] = second;
            buckets[i] = 0;
        }
        buckets[i]++;
        total++;
    }

    /** Events in the 60 seconds up to {@code nowMillis}. */
    public synchronized long lastMinute(long nowMillis) {
        long second = nowMillis / 1000L;
        long sum = 0;
        for (int i = 0; i < SECONDS; i++) {
            if (second - bucketSecond[i] < SECONDS && bucketSecond[i] <= second) {
                sum += buckets[i];
            }
        }
        return sum;
    }

    /** Events since the server started. */
    public synchronized long total() {
        return total;
    }
}
