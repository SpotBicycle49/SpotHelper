package com.spotcore.impl.cache;

import com.spotcore.api.cache.TextureCache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** LRU cache for CPU-side RGBA pixel buffers, bounded by bytes. */
public final class BoundedTextureCache implements TextureCache {
    private final long maxBytes;
    private long currentBytes;
    private final LinkedHashMap<Long, byte[]> entries = new LinkedHashMap<>(128, 0.75f, true);

    public BoundedTextureCache(long maxBytes) {
        if (maxBytes < 4) {
            throw new IllegalArgumentException("maxBytes must be >= 4");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public synchronized Optional<byte[]> get(long key) {
        byte[] data = entries.get(key);
        return data == null ? Optional.empty() : Optional.of(data.clone());
    }

    @Override
    public synchronized void put(long key, byte[] rgbaPixels) {
        if (rgbaPixels == null) {
            throw new IllegalArgumentException("rgbaPixels cannot be null");
        }
        if (rgbaPixels.length > maxBytes) {
            entries.remove(key);
            recalculateBytes();
            return;
        }
        byte[] copy = rgbaPixels.clone();
        byte[] previous = entries.put(key, copy);
        if (previous != null) currentBytes -= previous.length;
        currentBytes += copy.length;
        trim();
    }

    @Override
    public synchronized void invalidate(long key) {
        byte[] removed = entries.remove(key);
        if (removed != null) currentBytes -= removed.length;
    }

    @Override
    public synchronized void clear() {
        entries.clear();
        currentBytes = 0;
    }

    @Override
    public synchronized int size() {
        return entries.size();
    }

    private void trim() {
        var iterator = entries.entrySet().iterator();
        while (currentBytes > maxBytes && iterator.hasNext()) {
            Map.Entry<Long, byte[]> eldest = iterator.next();
            currentBytes -= eldest.getValue().length;
            iterator.remove();
        }
    }

    private void recalculateBytes() {
        currentBytes = entries.values().stream().mapToLong(bytes -> bytes.length).sum();
    }
}
