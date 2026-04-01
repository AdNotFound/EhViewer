package com.hippo.glgallery;

import java.util.BitSet;

final class DoublePageHelper {
    private static final float SPREAD_ASPECT_RATIO_THRESHOLD = 1.2f;

    private DoublePageHelper() {}

    static boolean isSpread(int width, int height) {
        return width > 0 && height > 0 && (float) width / height >= SPREAD_ASPECT_RATIO_THRESHOLD;
    }

    static int getSlotForIndex(int index, boolean doublePageOffset) {
        if (doublePageOffset) {
            return index == 0 ? 0 : (index + 1) / 2;
        }
        return index / 2;
    }

    static int getSlotStart(int slot, boolean doublePageOffset) {
        if (doublePageOffset) {
            return slot == 0 ? 0 : slot * 2 - 1;
        }
        return slot * 2;
    }

    static int getPairStart(int index, boolean doublePageMode, boolean doublePageOffset, BitSet spreads) {
        if (!doublePageMode || index < 0) {
            return index;
        }
        if (spreads.get(index)) {
            return index;
        }

        int slot = getSlotForIndex(index, doublePageOffset);
        int slotStart = getSlotStart(slot, doublePageOffset);
        if (slotStart != index && spreads.get(slotStart)) {
            return index;
        }
        return slotStart;
    }

    static int getPairSize(int index, int size, boolean doublePageMode, boolean doublePageOffset, BitSet spreads) {
        if (!doublePageMode) {
            return 1;
        }
        if (index < 0 || index >= size) {
            return 1;
        }
        if (spreads.get(index)) {
            return 1;
        }
        if (doublePageOffset && index == 0) {
            return 1;
        }
        if (index >= size - 1) {
            return 1;
        }
        if (spreads.get(index + 1)) {
            return 1;
        }

        int slot = getSlotForIndex(index, doublePageOffset);
        int slotStart = getSlotStart(slot, doublePageOffset);
        if (index == slotStart) {
            return 2;
        }
        return spreads.get(slotStart) ? 1 : 1;
    }

    static float getNextDoubleTapScale(
            float currentScale,
            float baseWidth,
            float baseHeight,
            float containerWidth,
            float containerHeight) {
        float fitScale = 1.0f;
        float fillScale = fitScale;
        if (baseWidth > 0f && baseHeight > 0f && containerWidth > 0f && containerHeight > 0f) {
            fillScale = Math.max(containerWidth / baseWidth, containerHeight / baseHeight);
            if (fillScale < fitScale) {
                fillScale = fitScale;
            }
        }
        float maxScale = Math.max(fillScale * 2.0f, 2.5f);
        float[] stops = fillScale > fitScale + 0.01f
                ? new float[] { fitScale, fillScale, maxScale }
                : new float[] { fitScale, maxScale };
        for (float stop : stops) {
            if (currentScale < stop - 0.01f) {
                return stop;
            }
        }
        return fitScale;
    }
}
