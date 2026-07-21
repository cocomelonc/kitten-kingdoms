/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/** Data-driven terrain kinds forming the world map. */
final class TerrainType {
    static final int GRASS = 0;
    static final int FOREST = 1;
    static final int HILL = 2;
    static final int WATER = 3;
    static final int STONE_OUTCROP = 4;
    static final int COUNT = 5;
    static final int NONE = -1;

    final int id;
    final int color;
    final boolean walkable;
    final boolean buildable;

    private TerrainType(int id, int color, boolean walkable, boolean buildable) {
        if (id < 0 || id >= COUNT) {
            throw new IllegalArgumentException("Terrain id out of range: " + id);
        }
        this.id = id;
        this.color = color;
        this.walkable = walkable;
        this.buildable = buildable;
    }

    static TerrainType[] createAll() {
        TerrainType[] all = new TerrainType[]{
                grass(), forest(), hill(), water(), stoneOutcrop()
        };
        if (all.length != COUNT) {
            throw new IllegalStateException("Terrain registry must contain exactly " + COUNT + " entries");
        }
        for (int i = 0; i < all.length; i++) {
            if (all[i].id != i) {
                throw new IllegalStateException("Terrain registry order must match id: " + i);
            }
        }
        return all;
    }

    private static TerrainType grass() {
        return new TerrainType(GRASS, 0xFFBFE0A0, true, true);
    }

    private static TerrainType forest() {
        return new TerrainType(FOREST, 0xFF6FA05C, true, false);
    }

    private static TerrainType hill() {
        return new TerrainType(HILL, 0xFFD8C79A, true, false);
    }

    private static TerrainType water() {
        return new TerrainType(WATER, 0xFF7FBFD8, false, false);
    }

    private static TerrainType stoneOutcrop() {
        return new TerrainType(STONE_OUTCROP, 0xFFA8A2AC, false, false);
    }
}
