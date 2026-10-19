package com.spothelper.util;

/** Левый верхний угол окна HandledScreen (protected x/y), открытый через миксин. */
public interface ScreenOrigin {
    int spothelper$originX();
    int spothelper$originY();
}
