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

import java.util.ArrayDeque;

public final class WorldMapTest {
    @Test
    public void walkableTerrainIsFullyConnectedFromStart() {
        WorldMap map = new WorldMap();
        int size = WorldMap.SIZE;
        boolean[][] reached = new boolean[size][size];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        reached[WorldMap.START_ROW][WorldMap.START_COL] = true;
        queue.add(WorldMap.START_ROW * size + WorldMap.START_COL);
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};

        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int row = current / size;
            int col = current % size;
            for (int i = 0; i < 4; i++) {
                int nr = row + dr[i];
                int nc = col + dc[i];
                if (nr < 0 || nr >= size || nc < 0 || nc >= size || reached[nr][nc]) {
                    continue;
                }
                if (!map.isWalkable(nr, nc)) {
                    continue;
                }
                reached[nr][nc] = true;
                queue.add(nr * size + nc);
            }
        }

        int walkableCount = 0;
        int reachedCount = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (map.isWalkable(r, c)) {
                    walkableCount++;
                    if (reached[r][c]) {
                        reachedCount++;
                    }
                }
            }
        }
        assertTrue("No walkable terrain found", walkableCount > 0);
        assertEquals("Some walkable terrain is unreachable from the start tile",
                walkableCount, reachedCount);
    }

    @Test
    public void revealAroundOnlyMarksTilesWithinRadius() {
        WorldMap map = new WorldMap();
        int centerRow = 40;
        int centerCol = 40;
        map.revealAround(centerRow, centerCol);
        assertTrue(map.isExplored(centerRow, centerCol));
        assertTrue(map.isExplored(centerRow + 3, centerCol));
        assertFalse(map.isExplored(centerRow + 4, centerCol));
        assertFalse(map.isExplored(centerRow, centerCol + 6));
    }

    @Test
    public void occupiedTileIsNeitherWalkableNorBuildable() {
        WorldMap map = new WorldMap();
        int row = WorldMap.START_ROW;
        int col = WorldMap.START_COL;
        assertTrue("Precondition: tile should be walkable before occupying", map.isWalkable(row, col));
        map.markOccupied(row, col);
        assertFalse(map.isWalkable(row, col));
        assertFalse(map.isBuildable(row, col));
    }
}
