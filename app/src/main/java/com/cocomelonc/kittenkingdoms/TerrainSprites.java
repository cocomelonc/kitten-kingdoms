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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.SparseArray;

/**
 * The one and only world-art source: Kenney's CC0 Roguelike/RPG sheet also used by
 * crystal-trail. Every ground tile, shoreline, prop, building, and world-map marker is cut from
 * this sheet and enlarged with nearest-neighbor scaling, so no foreign flat-color tiles can leak
 * into the map. See {@code third_party/kenney/KENNEY_LICENSE.txt}.
 */
final class TerrainSprites {
    private static final int SHEET_COLUMNS = 57;
    private static final int SOURCE_CELL = 16;
    private static final int SOURCE_GAP = 1;
    private static final int OUTPUT_CELL = 64;

    private static final int GRASS = index(0, 5);
    private static final int GRASS_DARK = index(1, 5);
    private static final int GRASS_ROCKS = index(1, 9);
    private static final int DIRT = index(0, 6);
    private static final int SAND = index(0, 8);
    private static final int[] FLOWERS = {
            index(6, 1), index(7, 0), index(9, 1),
            index(10, 0), index(12, 1), index(13, 0),
    };
    private static final int[] WATER_PLAIN = {index(0, 0), index(0, 1)};
    private static final int WATER_NW = index(0, 2);
    private static final int WATER_N = index(0, 3);
    private static final int WATER_NE = index(0, 4);
    private static final int WATER_W = index(1, 2);
    private static final int WATER_E = index(1, 4);
    private static final int WATER_SW = index(2, 2);
    private static final int WATER_S = index(2, 3);
    private static final int WATER_SE = index(2, 4);
    private static final int WATER_INNER_NW = index(2, 1);
    private static final int WATER_INNER_NE = index(2, 0);
    private static final int WATER_INNER_SW = index(1, 1);
    private static final int WATER_INNER_SE = index(1, 0);

    private final Bitmap sheet;
    private final SparseArray<Bitmap> tileCache = new SparseArray<>();
    private final Bitmap[] forestProps;
    private final Bitmap[] stoneProps;
    private final Bitmap[] grassProps;
    private final Bitmap[] buildingSprites;
    private final Bitmap[] settlementSprites;
    private final Bitmap waterLily;

    TerrainSprites(Resources resources) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        sheet = BitmapFactory.decodeResource(resources, R.drawable.roguelike_16, options);
        if (sheet == null) {
            throw new IllegalStateException("Kenney tileset could not be decoded");
        }

        forestProps = new Bitmap[]{
                prop(10, 13, 2, 1), prop(10, 15, 2, 1),
                prop(10, 23, 2, 1), prop(10, 16, 2, 1),
        };
        stoneProps = new Bitmap[]{prop(21, 54, 1, 1), prop(21, 55, 1, 1)};
        grassProps = new Bitmap[]{
                prop(9, 19, 1, 1), prop(4, 48, 1, 1), prop(3, 48, 1, 1),
        };
        waterLily = prop(11, 25, 1, 1);

        buildingSprites = new Bitmap[]{
                prop(10, 48, 2, 2), // Town Hall: beige pavilion
                prop(23, 54, 1, 1), // Fishing Dock: water rock
                prop(10, 13, 2, 1), // Lumber Camp
                prop(21, 54, 1, 1), // Quarry
                prop(10, 24, 1, 1), // Catnip Farm
                prop(4, 48, 1, 1),  // Weaver's Cottage
                prop(10, 46, 2, 2), // Kitten Cottage: green pavilion
                prop(11, 43, 1, 1), // Storage Barn
                prop(10, 50, 2, 1), // Scholar's Den
                tileAt(FLOWERS[0]),  // Cozy Plaza
                prop(24, 41, 1, 1), // Crystal Mine
        };
        settlementSprites = new Bitmap[]{
                prop(10, 46, 2, 2), prop(10, 48, 2, 2),
                prop(10, 50, 2, 1), prop(24, 41, 1, 1),
        };
    }

    Bitmap terrainFor(WorldMap map, int row, int col, int seedValue) {
        switch (map.terrainAt(row, col)) {
            case TerrainType.WATER:
                return waterFor(map, row, col, seedValue);
            case TerrainType.FOREST:
                return tileAt(Math.floorMod(seedValue, 5) == 0 ? GRASS : GRASS_DARK);
            case TerrainType.HILL:
                return tileAt(Math.floorMod(seedValue, 4) == 0 ? SAND : DIRT);
            case TerrainType.STONE_OUTCROP:
                return tileAt(GRASS_ROCKS);
            case TerrainType.GRASS:
            default:
                if (Math.floorMod(seedValue, 37) == 0) {
                    return tileAt(FLOWERS[Math.floorMod(seedValue / 37, FLOWERS.length)]);
                }
                return tileAt(Math.floorMod(seedValue, 13) == 0 ? GRASS_DARK : GRASS);
        }
    }

    Bitmap fogTile(int seedValue) {
        return tileAt(Math.floorMod(seedValue, 5) == 0 ? GRASS_ROCKS : GRASS_DARK);
    }

    private Bitmap waterFor(WorldMap map, int row, int col, int seedValue) {
        boolean north = isLand(map, row - 1, col);
        boolean south = isLand(map, row + 1, col);
        boolean west = isLand(map, row, col - 1);
        boolean east = isLand(map, row, col + 1);
        if (north && west) {
            return tileAt(WATER_NW);
        }
        if (north && east) {
            return tileAt(WATER_NE);
        }
        if (south && west) {
            return tileAt(WATER_SW);
        }
        if (south && east) {
            return tileAt(WATER_SE);
        }
        if (north) {
            return tileAt(WATER_N);
        }
        if (south) {
            return tileAt(WATER_S);
        }
        if (west) {
            return tileAt(WATER_W);
        }
        if (east) {
            return tileAt(WATER_E);
        }
        if (isLand(map, row - 1, col - 1)) {
            return tileAt(WATER_INNER_NW);
        }
        if (isLand(map, row - 1, col + 1)) {
            return tileAt(WATER_INNER_NE);
        }
        if (isLand(map, row + 1, col - 1)) {
            return tileAt(WATER_INNER_SW);
        }
        if (isLand(map, row + 1, col + 1)) {
            return tileAt(WATER_INNER_SE);
        }
        return tileAt(WATER_PLAIN[Math.floorMod(seedValue, WATER_PLAIN.length)]);
    }

    private boolean isLand(WorldMap map, int row, int col) {
        return !map.inBounds(row, col) || map.terrainAt(row, col) != TerrainType.WATER;
    }

    Bitmap propFor(int terrainId, int seedValue) {
        switch (terrainId) {
            case TerrainType.FOREST:
                return Math.floorMod(seedValue, 3) == 0 ? null
                        : forestProps[Math.floorMod(seedValue / 3, forestProps.length)];
            case TerrainType.STONE_OUTCROP:
                return Math.floorMod(seedValue, 4) == 0 ? null
                        : stoneProps[Math.floorMod(seedValue / 4, stoneProps.length)];
            case TerrainType.GRASS:
                return Math.floorMod(seedValue, 17) != 0 ? null
                        : grassProps[Math.floorMod(seedValue / 17, grassProps.length)];
            default:
                return null;
        }
    }

    Bitmap waterLilyFor(int seedValue) {
        return Math.floorMod(seedValue, 11) == 0 ? waterLily : null;
    }

    Bitmap buildingFor(int buildingTypeId) {
        return buildingSprites[buildingTypeId];
    }

    Bitmap settlementFor(int settlementId) {
        return settlementSprites[Math.floorMod(settlementId, settlementSprites.length)];
    }

    private Bitmap tileAt(int tileIndex) {
        Bitmap cached = tileCache.get(tileIndex);
        if (cached != null) {
            return cached;
        }
        int row = tileIndex / SHEET_COLUMNS;
        int col = tileIndex % SHEET_COLUMNS;
        Bitmap source = Bitmap.createBitmap(sheet,
                col * (SOURCE_CELL + SOURCE_GAP), row * (SOURCE_CELL + SOURCE_GAP),
                SOURCE_CELL, SOURCE_CELL);
        Bitmap scaled = Bitmap.createScaledBitmap(source, OUTPUT_CELL, OUTPUT_CELL, false);
        source.recycle();
        tileCache.put(tileIndex, scaled);
        return scaled;
    }

    private Bitmap prop(int row, int col, int rows, int cols) {
        Bitmap result = Bitmap.createBitmap(cols * OUTPUT_CELL, rows * OUTPUT_CELL,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint nearest = new Paint();
        nearest.setFilterBitmap(false);
        nearest.setAntiAlias(false);
        for (int dr = 0; dr < rows; dr++) {
            for (int dc = 0; dc < cols; dc++) {
                canvas.drawBitmap(tileAt(index(row + dr, col + dc)),
                        dc * OUTPUT_CELL, dr * OUTPUT_CELL, nearest);
            }
        }
        return result;
    }

    private static int index(int row, int col) {
        return row * SHEET_COLUMNS + col;
    }
}
