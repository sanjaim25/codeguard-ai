package com.codeguard.security;

import java.time.*;
import java.util.*;

/** Bounded per-process fixed windows; deploy a shared ingress limiter across replicas. */
public final class RequestRateLimiter {
    private record Window(long minute, int requests) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int capacity;
    private long lastCleanup = Long.MIN_VALUE;
    public RequestRateLimiter(Clock clock, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Rate limit capacity must be positive");
        this.clock = clock; this.capacity = capacity;
    }
    /** Zero means allowed, otherwise seconds until the next window. */
    public synchronized long acquire(String key, int limit) {
        long second = clock.instant().getEpochSecond(), minute = Math.floorDiv(second, 60);
        if (minute != lastCleanup) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute() != minute);
            lastCleanup = minute;
        }
        Window current = windows.get(key);
        long retryAfter = 60 - Math.floorMod(second, 60);
        if (current == null && windows.size() >= capacity) return retryAfter;
        int count = current == null ? 0 : current.requests();
        if (count >= limit) return retryAfter;
        windows.put(key, new Window(minute, count + 1)); return 0;
    }
}
