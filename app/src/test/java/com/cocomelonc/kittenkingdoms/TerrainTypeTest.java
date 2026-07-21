/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TerrainTypeTest {
    @Test
    public void registryContainsExactlyFiveOrderedEntries() {
        TerrainType[] all = TerrainType.createAll();
        assertEquals(TerrainType.COUNT, all.length);
        for (int i = 0; i < all.length; i++) {
            assertEquals(i, all[i].id);
        }
    }

    @Test
    public void onlyGrassIsBuildable() {
        for (TerrainType terrain : TerrainType.createAll()) {
            assertEquals(terrain.id == TerrainType.GRASS, terrain.buildable);
        }
    }

    @Test
    public void waterAndStoneOutcropAreNotWalkable() {
        TerrainType[] all = TerrainType.createAll();
        assertFalse(all[TerrainType.WATER].walkable);
        assertFalse(all[TerrainType.STONE_OUTCROP].walkable);
        assertTrue(all[TerrainType.GRASS].walkable);
        assertTrue(all[TerrainType.FOREST].walkable);
        assertTrue(all[TerrainType.HILL].walkable);
    }
}
