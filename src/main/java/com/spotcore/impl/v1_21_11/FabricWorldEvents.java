package com.spotcore.impl.v1_21_11;

import com.spotcore.api.cache.SurfaceCache;
import com.spotcore.api.chunks.ChunkTracker;
import com.spotcore.api.WorldSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Feeds Minecraft/Fabric lifecycle events into the version-neutral services.
 * This is the only place that knows which events drive invalidation, so a
 * future Minecraft version only needs to replace this class.
 *
 * Not covered yet: single block changes (no Fabric client event exists; needs a
 * mixin into the client network handler).
 */
public final class FabricWorldEvents {
    private FabricWorldEvents() {}

    public static void register(WorldSession session, ChunkTracker chunks, SurfaceCache surfaces) {
        // Chunk events are delivered on the client thread.
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            int cx = chunk.getPos().x;
            int cz = chunk.getPos().z;
            chunks.markLoaded(cx, cz);
            // A (re)loaded chunk may differ from anything sampled before it.
            surfaces.invalidateChunk(cx, cz);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            int cx = chunk.getPos().x;
            int cz = chunk.getPos().z;
            chunks.markUnloaded(cx, cz);
            surfaces.invalidateChunk(cx, cz);
        });

        // New world object: join, respawn into another dimension, world swap.
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> session.advance());

        // Leaving a server/world: drop everything so nothing leaks into the next one.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> session.advance());
    }
}
