package com.spotcore.api.cache;

import java.util.Optional;

/**
 * Bounded cache for computed world-column samples. This class is thread-safe
 * and does not call Minecraft APIs.
 */
public interface SurfaceCache {
    Optional<SurfaceSample> get(int blockX, int blockZ, long worldEpoch);
    void put(SurfaceSample sample);
    void invalidateColumn(int blockX, int blockZ);

    /** Drops all cached columns that belong to the given chunk (16x16 columns). */
    void invalidateChunk(int chunkX, int chunkZ);

    void clear();
    int size();
}
