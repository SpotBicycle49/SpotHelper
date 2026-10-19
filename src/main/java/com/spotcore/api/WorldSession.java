package com.spotcore.api;

/**
 * Tracks world/session identity so caches can be invalidated on disconnect,
 * world changes, or dimension changes without depending on Minecraft classes.
 */
public interface WorldSession {
    long epoch();

    /**
     * Starts a new session epoch (new world, dimension change, disconnect) and
     * then notifies reset listeners so dependent caches can drop their data.
     */
    void advance();

    /**
     * Registers a callback run after every {@link #advance()}. Intended for a
     * small, fixed set of services registered once at startup.
     */
    void addResetListener(Runnable listener);
}
