package com.spotcore.client;

import com.spotcore.api.SpotCore;
import com.spotcore.impl.DefaultWorldSession;
import com.spotcore.impl.cache.BoundedSurfaceCache;
import com.spotcore.impl.cache.BoundedTextureCache;
import com.spotcore.impl.chunks.BoundedChunkTracker;
import com.spotcore.impl.v1_21_11.FabricWorldEvents;
import com.spotcore.impl.v1_21_11.MinecraftWorldAccess;
import net.fabricmc.api.ClientModInitializer;

/**
 * Registers bounded in-memory services. GPU texture objects remain outside
 * TextureCache; this cache stores CPU-side RGBA buffers only.
 */
public final class SpotCoreClient implements ClientModInitializer {
    private static final int MAX_SURFACE_ENTRIES = 65_536;
    // Max render distance 32 -> 65x65 = 4225 chunks; keep headroom so a loaded
    // chunk is never evicted (eviction would read as "unloaded").
    private static final int MAX_TRACKED_CHUNKS = 16_384;
    private static final long MAX_TEXTURE_CACHE_BYTES = 32L * 1024L * 1024L;

    @Override
    public void onInitializeClient() {
        BoundedSurfaceCache surfaces = new BoundedSurfaceCache(MAX_SURFACE_ENTRIES);
        BoundedTextureCache textures = new BoundedTextureCache(MAX_TEXTURE_CACHE_BYTES);
        BoundedChunkTracker chunks = new BoundedChunkTracker(MAX_TRACKED_CHUNKS);
        DefaultWorldSession session = new DefaultWorldSession();

        // Everything tied to the current world is dropped on every epoch change.
        session.addResetListener(surfaces::clear);
        session.addResetListener(textures::clear);
        session.addResetListener(chunks::clear);

        SpotCore.registerServices(new MinecraftWorldAccess(), surfaces, textures, chunks, session);
        FabricWorldEvents.register(session, chunks, surfaces);
    }
}
