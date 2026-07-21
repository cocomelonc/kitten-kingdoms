/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Cached four-direction, four-frame pixel-art walk cycle for the player kitten. */
final class KittenSprites {
    static final int DOWN = 0;
    static final int LEFT = 1;
    static final int RIGHT = 2;
    static final int UP = 3;

    private static final int FRAME_SIZE = 32;
    private static final int FRAME_COUNT = 4;
    private static final int DIRECTION_COUNT = 4;

    private final Bitmap[][] frames = new Bitmap[DIRECTION_COUNT][FRAME_COUNT];

    KittenSprites(Resources resources) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap sheet = BitmapFactory.decodeResource(resources, R.drawable.kitten_walk, options);
        if (sheet == null || sheet.getWidth() != FRAME_SIZE * FRAME_COUNT
                || sheet.getHeight() != FRAME_SIZE * DIRECTION_COUNT) {
            throw new IllegalStateException("Kitten walk sheet must be 128x128");
        }
        for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                frames[direction][frame] = Bitmap.createBitmap(sheet,
                        frame * FRAME_SIZE, direction * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
            }
        }
        sheet.recycle();
    }

    Bitmap frame(int direction, int frame) {
        int safeDirection = Math.max(0, Math.min(DIRECTION_COUNT - 1, direction));
        return frames[safeDirection][Math.floorMod(frame, FRAME_COUNT)];
    }
}
