package com.spothelper.minimap;

public final class MinimapClamp {
    public static final int[] RADII = {16, 24, 32, 48, 64, 96};
    private MinimapClamp() {}
    public static int clampRadius(int r) {
        int best = RADII[0];
        for (int v : RADII) if (Math.abs(v - r) < Math.abs(best - r)) best = v;
        return best;
    }
}