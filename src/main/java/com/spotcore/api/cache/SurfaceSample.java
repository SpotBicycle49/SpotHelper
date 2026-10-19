package com.spotcore.api.cache;

/**
 * Immutable result for a world column. A missing sample is represented with
 * {@link #missing()} rather than a fabricated surface height.
 */
public record SurfaceSample(
    int blockX,
    int blockZ,
    int surfaceY,
    String blockId,
    long worldEpoch
) {
    public static SurfaceSample missing(int blockX, int blockZ, long worldEpoch) {
        return new SurfaceSample(blockX, blockZ, Integer.MIN_VALUE, "", worldEpoch);
    }

    public boolean isPresent() {
        return surfaceY != Integer.MIN_VALUE && blockId != null && !blockId.isEmpty();
    }
}
