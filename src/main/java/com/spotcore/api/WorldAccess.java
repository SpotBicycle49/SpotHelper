package com.spotcore.api;

/**
 * Version-neutral, read-only access to the client world.
 * Methods return simple Java values rather than Minecraft classes.
 */
public interface WorldAccess {
    boolean isWorldAvailable();
    boolean isChunkLoaded(int chunkX, int chunkZ);
    double playerX();
    double playerY();
    double playerZ();
    int playerBlockX();
    int playerBlockY();
    int playerBlockZ();

    /**
     * Returns the top solid/world-surface Y coordinate for the given column,
     * or Integer.MIN_VALUE if the world/chunk is unavailable.
     */
    int surfaceY(int blockX, int blockZ);

    /**
     * Returns the registry identifier of the block at the given position,
     * or an empty string if the position cannot be queried safely.
     */
    String blockId(int blockX, int blockY, int blockZ);
}
