package com.spotcore.impl.cache;

import com.spotcore.api.cache.SurfaceCache;
import com.spotcore.api.cache.SurfaceSample;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Simple access-ordered LRU cache. Safe to use from more than one thread.
 */
public final class BoundedSurfaceCache implements SurfaceCache {
    private final int maxEntries;
    private final Map<Long, SurfaceSample> entries;

    public BoundedSurfaceCache(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        this.maxEntries = maxEntries;
        this.entries = new LinkedHashMap<>(Math.min(maxEntries, 4096), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, SurfaceSample> eldest) {
                return size() > BoundedSurfaceCache.this.maxEntries;
            }
        };
    }

    @Override
    public synchronized Optional<SurfaceSample> get(int blockX, int blockZ, long worldEpoch) {
        SurfaceSample sample = entries.get(key(blockX, blockZ));
        if (sample == null || sample.worldEpoch() != worldEpoch) {
            return Optional.empty();
        }
        return Optional.of(sample);
    }

    @Override
    public synchronized void put(SurfaceSample sample) {
        entries.put(key(sample.blockX(), sample.blockZ()), sample);
    }

    @Override
    public synchronized void invalidateColumn(int blockX, int blockZ) {
        entries.remove(key(blockX, blockZ));
    }

    @Override
    public synchronized void invalidateChunk(int chunkX, int chunkZ) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        // Fixed 256 removals; cheaper and simpler than scanning the whole map.
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                entries.remove(key(baseX + dx, baseZ + dz));
            }
        }
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }

    @Override
    public synchronized int size() {
        return entries.size();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
