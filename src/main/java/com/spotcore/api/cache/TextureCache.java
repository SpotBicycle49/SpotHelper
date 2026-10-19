package com.spotcore.api.cache;

import java.util.Optional;

/**
 * Generic bounded cache for prepared CPU-side image data.
 * The library deliberately does not store Minecraft GPU objects here because
 * their lifetime and thread rules are version/render-backend specific.
 */
public interface TextureCache {
    Optional<byte[]> get(long key);
    void put(long key, byte[] rgbaPixels);
    void invalidate(long key);
    void clear();
    int size();
}
