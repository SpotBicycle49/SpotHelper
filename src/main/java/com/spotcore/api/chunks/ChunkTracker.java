package com.spotcore.api.chunks;

/**
 * Tracks chunk availability for the current world session.
 * This tracker records observations; it does not load chunks.
 */
public interface ChunkTracker {
    /** Receives chunk state changes. Called on the thread that reported the change. */
    interface Listener {
        void onChunkChanged(int chunkX, int chunkZ, boolean loaded);
    }

    void markLoaded(int chunkX, int chunkZ);
    void markUnloaded(int chunkX, int chunkZ);
    boolean isKnownLoaded(int chunkX, int chunkZ);
    void clear();
    int size();

    /**
     * Registers a listener for actual load/unload transitions (repeated reports
     * of the same state are ignored; {@link #clear()} does not notify).
     * Meant for a small, fixed set of long-lived consumers.
     */
    void addListener(Listener listener);
}
