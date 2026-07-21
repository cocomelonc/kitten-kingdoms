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

/** Two-frame pixel animation for each decorative animal species. */
final class WildlifeSprites {
    private static final int FRAME_SIZE = 32;
    private static final int FRAME_COUNT = 2;
    private static final int SPECIES_COUNT = 4;

    private final Bitmap[][] frames = new Bitmap[SPECIES_COUNT][FRAME_COUNT];

    WildlifeSprites(Resources resources) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap sheet = BitmapFactory.decodeResource(resources, R.drawable.wildlife, options);
        if (sheet == null || sheet.getWidth() != 64 || sheet.getHeight() != 128) {
            throw new IllegalStateException("Wildlife sheet must be 64x128");
        }
        for (int species = 0; species < SPECIES_COUNT; species++) {
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                frames[species][frame] = Bitmap.createBitmap(sheet,
                        frame * FRAME_SIZE, species * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
            }
        }
        sheet.recycle();
    }

    Bitmap frame(int species, int frame) {
        int safeSpecies = Math.max(0, Math.min(SPECIES_COUNT - 1, species));
        return frames[safeSpecies][Math.floorMod(frame, FRAME_COUNT)];
    }
}
