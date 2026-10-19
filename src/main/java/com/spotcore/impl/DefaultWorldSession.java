package com.spotcore.impl;

import com.spotcore.api.WorldSession;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class DefaultWorldSession implements WorldSession {
    private final AtomicLong epoch = new AtomicLong(1);
    // Written once at startup, read on rare world changes: copy-on-write is fine.
    private final List<Runnable> resetListeners = new CopyOnWriteArrayList<>();

    @Override
    public long epoch() {
        return epoch.get();
    }

    @Override
    public void advance() {
        // Bump the epoch first so anything computed before the reset is rejected
        // by epoch checks even if a listener throws.
        epoch.incrementAndGet();
        for (Runnable listener : resetListeners) {
            listener.run();
        }
    }

    @Override
    public void addResetListener(Runnable listener) {
        resetListeners.add(Objects.requireNonNull(listener, "listener"));
    }
}
