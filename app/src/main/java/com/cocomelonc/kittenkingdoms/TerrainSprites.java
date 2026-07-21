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
    private static final int EDGE_N = 0;
    private static final int EDGE_S = 1;
    private static final int EDGE_E = 2;
    private static final int EDGE_W = 3;
    private static final int EDGE_NE = 4;
    private static final int EDGE_NW = 5;
    private static final int EDGE_SE = 6;
    private static final int EDGE_SW = 7;
    private static final int INNER_NE = 0;
    private static final int INNER_NW = 1;
    private static final int INNER_SE = 2;
    private static final int INNER_SW = 3;

    private final Bitmap[] waterEdge;
    private final Bitmap[] waterInner;
    private final Bitmap waterPlain;
    private final Bitmap waterLily;
    private final Bitmap[] forestProps;
    private final Bitmap[] stoneProps;
    private final Bitmap[] grassProps;
    final Bitmap lumberCampBadge;
    final Bitmap quarryBadge;
    final Bitmap crystalMineBadge;

    TerrainSprites(Resources resources) {
        waterEdge = new Bitmap[]{
                decode(resources, R.drawable.water_edge_n),
                decode(resources, R.drawable.water_edge_s),
                decode(resources, R.drawable.water_edge_e),
                decode(resources, R.drawable.water_edge_w),
                decode(resources, R.drawable.water_edge_ne),
                decode(resources, R.drawable.water_edge_nw),
                decode(resources, R.drawable.water_edge_se),
                decode(resources, R.drawable.water_edge_sw),
        };
        waterInner = new Bitmap[]{
                decode(resources, R.drawable.water_inner_ne),
                decode(resources, R.drawable.water_inner_nw),
                decode(resources, R.drawable.water_inner_se),
                decode(resources, R.drawable.water_inner_sw),
        };
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

    /**
     * The shoreline bitmap for a water tile, or {@code null} if a non-grass, non-water neighbor
     * makes autotiling ambiguous (the caller should keep its plain color fill in that case).
     * Mirrors crystal-trail's {@code tools/kenney.py} {@code _water_tile()} 3x3-blob selection.
     */
    Bitmap waterBitmapFor(WorldMap map, int row, int col) {
        boolean grassN = isGrassLike(map, row - 1, col);
        boolean grassS = isGrassLike(map, row + 1, col);
        boolean grassW = isGrassLike(map, row, col - 1);
        boolean grassE = isGrassLike(map, row, col + 1);
        if (ambiguous(map, row - 1, col) || ambiguous(map, row + 1, col)
                || ambiguous(map, row, col - 1) || ambiguous(map, row, col + 1)) {
            return null;
        }
        if (grassN && grassW) {
            return waterEdge[EDGE_NW];
        }
        if (grassN && grassE) {
            return waterEdge[EDGE_NE];
        }
        if (grassS && grassW) {
            return waterEdge[EDGE_SW];
        }
        if (grassS && grassE) {
            return waterEdge[EDGE_SE];
        }
        if (grassN) {
            return waterEdge[EDGE_N];
        }
        if (grassS) {
            return waterEdge[EDGE_S];
        }
        if (grassW) {
            return waterEdge[EDGE_W];
        }
        if (grassE) {
            return waterEdge[EDGE_E];
        }
        if (isGrassLike(map, row - 1, col - 1)) {
            return waterInner[INNER_NW];
        }
        if (isGrassLike(map, row - 1, col + 1)) {
            return waterInner[INNER_NE];
        }
        if (isGrassLike(map, row + 1, col - 1)) {
            return waterInner[INNER_SW];
        }
        if (isGrassLike(map, row + 1, col + 1)) {
            return waterInner[INNER_SE];
        }
        return waterPlain;
    }

    private boolean ambiguous(WorldMap map, int row, int col) {
        if (!map.inBounds(row, col)) {
            return false;
        }
        int terrainId = map.terrainAt(row, col);
        return terrainId != TerrainType.GRASS && terrainId != TerrainType.WATER;
    }

    private boolean isGrassLike(WorldMap map, int row, int col) {
        return !map.inBounds(row, col) || map.terrainAt(row, col) == TerrainType.GRASS;
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
