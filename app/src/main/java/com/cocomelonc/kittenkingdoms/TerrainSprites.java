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

/**
 * Loads and caches the CC0 Kenney terrain/building sprites once (in the constructor, never
 * per-frame) and picks which one applies to a given tile. See third_party/kenney/ for the
 * license text.
 */
final class TerrainSprites {
    /**
     * Kept as CC0 source-art references for contributors who want to export another tileset.
     * The runtime renderer uses the composable shoreline so all 256 neighbor masks are valid.
     */
    @SuppressWarnings("unused")
    private static final int[] CC0_SHORE_SOURCE_ASSETS = {
            R.drawable.water_edge_n,
            R.drawable.water_edge_s,
            R.drawable.water_edge_e,
            R.drawable.water_edge_w,
            R.drawable.water_edge_ne,
            R.drawable.water_edge_nw,
            R.drawable.water_edge_se,
            R.drawable.water_edge_sw,
            R.drawable.water_inner_ne,
            R.drawable.water_inner_nw,
            R.drawable.water_inner_se,
            R.drawable.water_inner_sw,
    };

    private final Bitmap waterPlain;
    private final Bitmap waterLily;
    private final Bitmap[] forestProps;
    private final Bitmap[] stoneProps;
    private final Bitmap[] grassProps;
    final Bitmap lumberCampBadge;
    final Bitmap quarryBadge;
    final Bitmap crystalMineBadge;

    TerrainSprites(Resources resources) {
        waterPlain = decode(resources, R.drawable.water_plain);
        waterLily = decode(resources, R.drawable.water_lily);
        forestProps = new Bitmap[]{
                decode(resources, R.drawable.tree_green),
                decode(resources, R.drawable.tree_dark),
                decode(resources, R.drawable.tree_fruit),
                decode(resources, R.drawable.pine_green),
        };
        stoneProps = new Bitmap[]{
                decode(resources, R.drawable.rock_grey_big),
                decode(resources, R.drawable.rock_grey_mid),
        };
        grassProps = new Bitmap[]{
                decode(resources, R.drawable.bush_round),
                decode(resources, R.drawable.mushroom_red),
                decode(resources, R.drawable.mushroom_brown),
        };
        lumberCampBadge = decode(resources, R.drawable.badge_lumber_camp);
        quarryBadge = decode(resources, R.drawable.badge_quarry);
        crystalMineBadge = decode(resources, R.drawable.badge_crystal_mine);
    }

    private static Bitmap decode(Resources resources, int resId) {
        return BitmapFactory.decodeResource(resources, resId);
    }

    /** The seamless CC0 water surface; shore geometry is composed for every neighbor mask. */
    Bitmap waterBase() {
        return waterPlain;
    }

    /** A decoration prop for a non-water tile, or {@code null} for a bare tile this time. */
    Bitmap propFor(int terrainId, int seedValue) {
        switch (terrainId) {
            case TerrainType.FOREST:
                return Math.floorMod(seedValue, 3) == 0 ? null
                        : forestProps[Math.floorMod(seedValue / 3, forestProps.length)];
            case TerrainType.STONE_OUTCROP:
                return Math.floorMod(seedValue, 4) == 0 ? null
                        : stoneProps[Math.floorMod(seedValue / 4, stoneProps.length)];
            case TerrainType.GRASS:
                return Math.floorMod(seedValue, 11) != 0 ? null
                        : grassProps[Math.floorMod(seedValue / 11, grassProps.length)];
            default:
                return null;
        }
    }

    /** An occasional lily pad drawn on top of a water tile's base bitmap, or {@code null}. */
    Bitmap waterLilyFor(int seedValue) {
        return Math.floorMod(seedValue, 9) == 0 ? waterLily : null;
    }
}
