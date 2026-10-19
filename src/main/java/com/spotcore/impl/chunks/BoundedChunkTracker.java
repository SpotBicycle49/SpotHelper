package com.spotcore.impl.chunks;

import com.spotcore.api.chunks.ChunkTracker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Bounded tracker; it never requests chunk loads. */
public final class BoundedChunkTracker implements ChunkTracker {
    private final int maxEntries;
    private final Map<Long, Boolean> loaded = new LinkedHashMap<>(128, 0.75f, true);
    // Written once at startup; read on every chunk event.
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public BoundedChunkTracker(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public void markLoaded(int chunkX, int chunkZ) {
        boolean added;
        synchronized (this) {
            added = loaded.put(key(chunkX, chunkZ), Boolean.TRUE) == null;
            trim();
        }
        // Listeners run outside the lock so they can never deadlock with it.
        if (added) notifyListeners(chunkX, chunkZ, true);
    }

    @Override
    public void markUnloaded(int chunkX, int chunkZ) {
        boolean removed;
        synchronized (this) {
            removed = loaded.remove(key(chunkX, chunkZ)) != null;
        }
        if (removed) notifyListeners(chunkX, chunkZ, false);
    }

    @Override
    public synchronized boolean isKnownLoaded(int chunkX, int chunkZ) {
        return Boolean.TRUE.equals(loaded.get(key(chunkX, chunkZ)));
    }

    @Override
    public synchronized void clear() {
        loaded.clear();
    }

    @Override
    public synchronized int size() {
        return loaded.size();
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void notifyListeners(int chunkX, int chunkZ, boolean isLoaded) {
        for (Listener listener : listeners) {
            listener.onChunkChanged(chunkX, chunkZ, isLoaded);
        }
    }

    private void trim() {
        while (loaded.size() > maxEntries) {
            Long eldest = loaded.keySet().iterator().next();
            loaded.remove(eldest);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
