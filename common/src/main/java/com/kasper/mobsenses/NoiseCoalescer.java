package com.kasper.mobsenses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coalesces noise events per chunk within a server tick and dispatches them to nearby listeners within
 * a strict per-event and per-tick ray-tracing budget.
 * <p>
 * When an explosion destroys thirty blocks in one tick or a contraption clicks repeatedly in the same
 * chunk, all events in that chunk coalesce into a single {@link CoalescedNoise} keeping the loudest
 * event's range, coordinates and kind. At the end of the tick, coalesced noises are processed loudest
 * first so important sounds (explosions, bells, fights) always win when the tick budget is saturated.
 */
public final class NoiseCoalescer {

    /** Default cap on coalesced chunk events processed in a single world tick. */
    public static final int DEFAULT_MAX_EVENTS_PER_TICK = 16;
    /** Default cap on 5-ray wall traces performed for a single coalesced noise event. */
    public static final int DEFAULT_MAX_TRACES_PER_EVENT = 16;
    /** Default cap on 5-ray wall traces performed across all events in a single world tick. */
    public static final int DEFAULT_MAX_TRACES_PER_TICK = 64;
    /** Maximum distinct chunks queued in one world before a tick drains them. */
    public static final int MAX_QUEUED_CHUNKS = 128;

    /** One coalesced noise in a chunk for the current tick. */
    @com.github.bsideup.jabel.Desugar
    public record CoalescedNoise(String world, int chunkX, int chunkZ, NoiseModel.Kind kind, String eventId,
                                 double x, double y, double z, double range, int mergedCount) {
    }

    /** Temporary loud sound source that masks quieter nearby noises (explosions, bells, note blocks, loud gunfire). */
    @com.github.bsideup.jabel.Desugar
    public record SoundCover(String world, double x, double y, double z,
                             double radius, double coverPower, long expiresAtMs) {
    }

    private static final int MAX_COVERS_PER_WORLD = 32;
    private static final long MIN_COVER_TTL_MS = 1800L;
    private static final long MAX_COVER_TTL_MS = 3500L;

    /** A candidate listener mob with its position (feet and eye Y). */
    @com.github.bsideup.jabel.Desugar
    public record Candidate<M>(M mob, double x, double y, double z, double eyeY) {
    }

    /** One listener mob that heard a noise after wall occlusion was checked. */
    @com.github.bsideup.jabel.Desugar
    public record Alerted<M>(M mob, CoalescedNoise noise, double distance, double wallThickness, double audibleRange) {
    }

    /** Measures acoustic wall thickness between the noise and a listener's eyes. */
    @FunctionalInterface
    public interface WallTracer {
        double thickness(double fromX, double fromY, double fromZ, double toX, double toY, double toZ);
    }

    /** Finds candidate listener mobs within {@code range} blocks of {@code (x, y, z)}. */
    @FunctionalInterface
    public interface CandidateFinder<M> {
        List<Candidate<M>> find(double x, double y, double z, double range);
    }

    private final int maxEventsPerTick;
    private final int maxTracesPerEvent;
    private final int maxTracesPerTick;
    private final boolean customBudget;

    /** Per-world map of chunk key -> loudest noise in that chunk during the current tick. */
    private final Map<String, Map<Long, CoalescedNoise>> byWorld = new HashMap<>();
    private final Map<String, java.util.List<SoundCover>> coversByWorld = new HashMap<>();

    public NoiseCoalescer() {
        this(DEFAULT_MAX_EVENTS_PER_TICK, DEFAULT_MAX_TRACES_PER_EVENT, DEFAULT_MAX_TRACES_PER_TICK, false);
    }

    public NoiseCoalescer(int maxEventsPerTick, int maxTracesPerEvent, int maxTracesPerTick) {
        this(maxEventsPerTick, maxTracesPerEvent, maxTracesPerTick, true);
    }

    private NoiseCoalescer(int maxEventsPerTick, int maxTracesPerEvent, int maxTracesPerTick, boolean customBudget) {
        this.maxEventsPerTick = Math.max(1, maxEventsPerTick);
        this.maxTracesPerEvent = Math.max(1, maxTracesPerEvent);
        this.maxTracesPerTick = Math.max(1, maxTracesPerTick);
        this.customBudget = customBudget;
    }

    public int maxEventsPerTick() {
        return maxEventsPerTick;
    }

    public int maxTracesPerEvent() {
        return maxTracesPerEvent;
    }

    public int maxTracesPerTick() {
        return maxTracesPerTick;
    }

    /** Packs {@code (chunkX, chunkZ)} into a single 64-bit key. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Records a noise in {@code world} at {@code (x, y, z)} with effective radius {@code range}.
     * Multiple noises in the same chunk in the same tick coalesce into one, keeping the loudest.
     */
    public synchronized void record(String world, NoiseModel.Kind kind, String eventId,
                                    double x, double y, double z, double range) {
        if (world == null || kind == null || !(range >= NoiseModel.MIN_AUDIBLE_RANGE)) {
            return;
        }
        int chunkX = ((int) Math.floor(x)) >> 4;
        int chunkZ = ((int) Math.floor(z)) >> 4;
        long key = chunkKey(chunkX, chunkZ);
        Map<Long, CoalescedNoise> chunks = byWorld.computeIfAbsent(world, w -> new HashMap<>());
        CoalescedNoise existing = chunks.get(key);
        if (existing == null) {
            if (chunks.size() >= MAX_QUEUED_CHUNKS) {
                return;
            }
            chunks.put(key, new CoalescedNoise(world, chunkX, chunkZ, kind, eventId, x, y, z, range, 1));
        } else if (range > existing.range()) {
            chunks.put(key, new CoalescedNoise(world, chunkX, chunkZ, kind, eventId, x, y, z, range, existing.mergedCount() + 1));
        } else {
            chunks.put(key, new CoalescedNoise(world, chunkX, chunkZ, existing.kind(), existing.eventId(),
                    existing.x(), existing.y(), existing.z(), existing.range(), existing.mergedCount() + 1));
        }
    }

    /**
     * Drains the coalesced noises queued for {@code world}, sorted from loudest to quietest, capped at
     * {@link #maxEventsPerTick()}.
     */
    public synchronized List<CoalescedNoise> drainWorld(String world) {
        Map<Long, CoalescedNoise> chunks = byWorld.remove(world);
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        List<CoalescedNoise> list = new ArrayList<>(chunks.values());
        list.sort(Comparator.comparingDouble(CoalescedNoise::range).reversed());
        if (list.size() > maxEventsPerTick) {
            return Jv.copyOf(list.subList(0, maxEventsPerTick));
        }
        return Jv.copyOf(list);
    }

    /** Drains all worlds' coalesced noises. */
    public synchronized List<CoalescedNoise> drainAll() {
        if (byWorld.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> worlds = new ArrayList<>(byWorld.keySet());
        List<CoalescedNoise> out = new ArrayList<>();
        for (String w : worlds) {
            out.addAll(drainWorld(w));
        }
        return Jv.copyOf(out);
    }

    /** Clears all queued noises without processing them. */
    /**
     * Registers a loud sound event as temporary acoustic cover that muffles quieter nearby noises.
     */
    public synchronized void registerSoundCover(String world, NoiseModel.Kind kind,
                                                double x, double y, double z,
                                                double range, long nowMs) {
        if (world == null || kind == null || !(range >= 18.0)) {
            return;
        }
        boolean loudSource = kind == NoiseModel.Kind.EXPLOSION
                || kind == NoiseModel.Kind.BELL
                || kind == NoiseModel.Kind.NOTE_BLOCK
                || (kind == NoiseModel.Kind.GUNSHOT && range >= 20.0);
        if (!loudSource) {
            return;
        }
        long ttl = Math.max(MIN_COVER_TTL_MS, Math.min(MAX_COVER_TTL_MS, Math.round(range * 65.0)));
        double coverRadius = Math.max(10.0, range * 0.65);
        long nextExpiry = nowMs + ttl;
        java.util.List<SoundCover> list = coversByWorld.computeIfAbsent(world, k -> new java.util.ArrayList<>());
        pruneCoversLocked(list, nowMs);
        for (int i = 0; i < list.size(); i++) {
            SoundCover existing = list.get(i);
            if (distSq(x, y, z, existing.x(), existing.y(), existing.z()) <= 16.0) {
                list.set(i, new SoundCover(
                        world,
                        x, y, z,
                        Math.max(existing.radius(), coverRadius),
                        Math.max(existing.coverPower(), range),
                        Math.max(existing.expiresAtMs(), nextExpiry)));
                return;
            }
        }
        if (list.size() >= MAX_COVERS_PER_WORLD) {
            list.remove(0);
        }
        list.add(new SoundCover(world, x, y, z, coverRadius, range, nextExpiry));
    }

    /**
     * Applies acoustic masking from recent louder sounds in {@code world}. Returns the muffled range,
     * or {@code 0.0} when completely drowned out by nearby noise cover.
     */
    public synchronized double applySoundCover(String world, NoiseModel.Kind kind,
                                                double x, double y, double z,
                                                double range, long nowMs) {
        if (world == null || kind == null || !(range > 0.0)) {
            return range;
        }
        if (!isMaskableKind(kind)) {
            return range;
        }
        java.util.List<SoundCover> list = coversByWorld.get(world);
        if (list == null || list.isEmpty()) {
            return range;
        }
        pruneCoversLocked(list, nowMs);
        if (list.isEmpty()) {
            return range;
        }
        double bestFactor = 1.0;
        for (int i = 0; i < list.size(); i++) {
            SoundCover c = list.get(i);
            if (range >= c.coverPower() * 0.85) {
                continue;
            }
            double dx = x - c.x();
            double dy = y - c.y();
            double dz = z - c.z();
            double rSq = c.radius() * c.radius();
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq > rSq) {
                continue;
            }
            double ratio = Math.sqrt(dSq) / c.radius();
            double factor = 0.25 + 0.50 * ratio;
            if (factor < bestFactor) {
                bestFactor = factor;
            }
        }
        double covered = range * bestFactor;
        return covered < NoiseModel.MIN_AUDIBLE_RANGE ? 0.0 : covered;
    }

    private static boolean isMaskableKind(NoiseModel.Kind kind) {
        switch (kind) {
            case STEP:
            case BLOCK_BREAK:
            case BLOCK_PLACE:
            case DOOR:
            case CONTAINER:
            case PROJECTILE:
            case EAT:
                return true;
            default:
                return false;
        }
    }

    private static void pruneCoversLocked(java.util.List<SoundCover> list, long nowMs) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (nowMs >= list.get(i).expiresAtMs()) {
                list.remove(i);
            }
        }
    }

    public synchronized void clear() {
        byWorld.clear();
        coversByWorld.clear();
    }

    /**
     * Processes the coalesced noises of {@code world} for one tick: finds candidate listeners, traces walls
     * within the per-event and per-tick budgets (closest candidates first), and returns all listeners that
     * heard a noise.
     */
    public <M> List<Alerted<M>> dispatchWorld(String world, CandidateFinder<M> finder, WallTracer tracer) {
        List<CoalescedNoise> events = drainWorld(world);
        if (events.isEmpty()) {
            return Collections.emptyList();
        }
        return dispatchEvents(events, finder, tracer);
    }

    /**
     * Runs the budget-limited listener selection and wall occlusion checks for a list of coalesced noises.
     */
    public <M> List<Alerted<M>> dispatchEvents(List<CoalescedNoise> events, CandidateFinder<M> finder, WallTracer tracer) {
        if (events.isEmpty()) {
            return Collections.emptyList();
        }
        List<Alerted<M>> alerted = new ArrayList<>();
        int maxAlertsPerEvent = customBudget ? maxTracesPerEvent : MobSenses.settings().maxAlertedMobs();
        int effectiveEventTraceCap = customBudget ? maxTracesPerEvent : Math.max(maxTracesPerEvent, maxAlertsPerEvent * 2);
        int tickTracesRemaining = customBudget ? maxTracesPerTick : Math.max(maxTracesPerTick, effectiveEventTraceCap * 2);
        int eventsProcessed = 0;

        for (CoalescedNoise noise : events) {
            if (eventsProcessed >= maxEventsPerTick || tickTracesRemaining <= 0) {
                break;
            }
            eventsProcessed++;

            double range = noise.range();
            double rangeSq = range * range;
            List<Candidate<M>> raw = finder.find(noise.x(), noise.y(), noise.z(), range);
            if (raw == null || raw.isEmpty()) {
                continue;
            }

            List<Candidate<M>> inSphere = new ArrayList<>(raw.size());
            for (Candidate<M> c : raw) {
                if (distSq(noise.x(), noise.y(), noise.z(), c.x(), c.y(), c.z()) <= rangeSq) {
                    inSphere.add(c);
                }
            }
            if (inSphere.isEmpty()) {
                continue;
            }

            inSphere.sort(Comparator.comparingDouble(c -> distSq(noise.x(), noise.y(), noise.z(), c.x(), c.y(), c.z())));

            int eventTracesRemaining = Math.min(effectiveEventTraceCap, tickTracesRemaining);
            int eventAlerted = 0;
            double sourceY = noise.y() + 0.5;
            for (Candidate<M> c : inSphere) {
                if (eventTracesRemaining <= 0 || tickTracesRemaining <= 0 || eventAlerted >= maxAlertsPerEvent) {
                    break;
                }
                eventTracesRemaining--;
                tickTracesRemaining--;

                double dist = Math.sqrt(distSq(noise.x(), noise.y(), noise.z(), c.x(), c.y(), c.z()));
                double thickness = tracer != null
                        ? tracer.thickness(noise.x(), sourceY, noise.z(), c.x(), c.eyeY(), c.z())
                        : 0.0;
                double audible = NoiseModel.audibleRange(range, thickness);
                if (dist <= audible) {
                    alerted.add(new Alerted<>(c.mob(), noise, dist, thickness, audible));
                    eventAlerted++;
                }
            }
        }
        return alerted;
    }

    private static double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return dx * dx + dy * dy + dz * dz;
    }
}
