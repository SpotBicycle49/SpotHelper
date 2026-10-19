package com.spotcore.api;

import com.spotcore.api.cache.SurfaceCache;
import com.spotcore.api.cache.TextureCache;
import com.spotcore.api.chunks.ChunkTracker;

import java.util.Objects;

/**
 * Stable entry point for SpotCore services. Keep Minecraft/Yarn types out of
 * this public API so feature code can survive mapping/version changes.
 */
public final class SpotCore {
    private static WorldAccess worldAccess;
    private static SurfaceCache surfaceCache;
    private static TextureCache textureCache;
    private static ChunkTracker chunkTracker;
    private static WorldSession worldSession;

    private SpotCore() {}

    public static synchronized void registerServices(
        WorldAccess access,
        SurfaceCache surfaces,
        TextureCache textures,
        ChunkTracker chunks,
        WorldSession session
    ) {
        worldAccess = Objects.requireNonNull(access, "access");
        surfaceCache = Objects.requireNonNull(surfaces, "surfaces");
        textureCache = Objects.requireNonNull(textures, "textures");
        chunkTracker = Objects.requireNonNull(chunks, "chunks");
        worldSession = Objects.requireNonNull(session, "session");
    }

    public static WorldAccess world() {
        return require(worldAccess, "WorldAccess");
    }

    public static SurfaceCache surfaces() {
        return require(surfaceCache, "SurfaceCache");
    }

    public static TextureCache textures() {
        return require(textureCache, "TextureCache");
    }

    public static ChunkTracker chunks() {
        return require(chunkTracker, "ChunkTracker");
    }

    public static WorldSession session() {
        return require(worldSession, "WorldSession");
    }

    public static boolean isReady() {
        return worldAccess != null && surfaceCache != null && textureCache != null
            && chunkTracker != null && worldSession != null;
    }

    private static <T> T require(T service, String name) {
        if (service == null) {
            throw new IllegalStateException("SpotCore " + name + " is not initialized.");
        }
        return service;
    }
}
