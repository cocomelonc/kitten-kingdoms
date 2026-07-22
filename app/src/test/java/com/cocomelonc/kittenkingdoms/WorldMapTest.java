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
    public void generatedWalkableNetworkHasNoDeadEndsAcrossSeeds() {
        int[] rowStep = {-1, 1, 0, 0};
        int[] colStep = {0, 0, -1, 1};
        for (long seed = 0; seed < 128; seed++) {
            WorldMap map = new WorldMap(seed);
            boolean[][] reached = new boolean[WorldMap.SIZE][WorldMap.SIZE];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            reached[WorldMap.START_ROW][WorldMap.START_COL] = true;
            queue.add(WorldMap.START_ROW * WorldMap.SIZE + WorldMap.START_COL);
            int reachedCount = 0;
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int currentRow = current / WorldMap.SIZE;
                int currentCol = current % WorldMap.SIZE;
                reachedCount++;
                for (int direction = 0; direction < rowStep.length; direction++) {
                    int nextRow = currentRow + rowStep[direction];
                    int nextCol = currentCol + colStep[direction];
                    if (!map.isWalkable(nextRow, nextCol) || reached[nextRow][nextCol]) {
                        continue;
                    }
                    reached[nextRow][nextCol] = true;
                    queue.add(nextRow * WorldMap.SIZE + nextCol);
                }
            }
            int walkableCount = 0;
            for (int row = 0; row < WorldMap.SIZE; row++) {
                for (int col = 0; col < WorldMap.SIZE; col++) {
                    if (!map.isWalkable(row, col)) {
                        continue;
                    }
                    walkableCount++;
                    int exits = 0;
                    for (int direction = 0; direction < rowStep.length; direction++) {
                        if (map.isWalkable(row + rowStep[direction], col + colStep[direction])) {
                            exits++;
                        }
                    }
                    assertTrue("Dead end for seed " + seed + " at " + row + "," + col,
                            exits >= 2);
                }
            }
            assertEquals("Disconnected walkable region for seed " + seed,
                    walkableCount, reachedCount);
        }
    }

    @Test
    public void visibleForestTreesAreNeverWalkable() {
        WorldMap map = new WorldMap();
        int blockingTrees = 0;
        for (int row = 0; row < WorldMap.SIZE; row++) {
            for (int col = 0; col < WorldMap.SIZE; col++) {
                if (map.hasBlockingProp(row, col)) {
                    blockingTrees++;
                    assertFalse("Tree cell is walkable at " + row + "," + col,
                            map.isWalkable(row, col));
                }
            }
        }
        assertTrue("Generated world should contain blocking trees", blockingTrees > 0);
    }

    @Test
    public void buildingsCannotCreateADeadEndButOpenMeadowPlacementRemainsValid() {
        WorldMap map = new WorldMap();

        assertFalse("Blocking the top edge would trap the corner",
                map.canOccupyWithoutBlockingRoutes(0, 1));
        assertTrue("A normal opening-meadow building should preserve routes",
                map.canOccupyWithoutBlockingRoutes(WorldMap.START_ROW, WorldMap.START_COL + 1));
    }

    @Test
    public void revealAroundOnlyMarksTilesWithinRadius() {
        WorldMap map = new WorldMap();
        int centerRow = 40;
        int centerCol = 40;
        map.revealAround(centerRow, centerCol);
        assertTrue(map.isExplored(centerRow, centerCol));
        assertTrue(map.isExplored(centerRow + 3, centerCol));
        assertTrue(map.isExplored(centerRow + 6, centerCol));
        assertFalse(map.isExplored(centerRow + 7, centerCol));
    }

    @Test
    public void fullyExploringReachableLandRevealsEveryCellForEveryGeneratedWorld() {
        for (long seed = 0; seed < 128; seed++) {
            WorldMap map = new WorldMap(seed);
            boolean[][] allReachableLandExplored = new boolean[WorldMap.SIZE][WorldMap.SIZE];
            for (int row = 0; row < WorldMap.SIZE; row++) {
                for (int col = 0; col < WorldMap.SIZE; col++) {
                    allReachableLandExplored[row][col] = map.isWalkable(row, col);
                }
            }

            map.restoreExplored(allReachableLandExplored);

            for (int row = 0; row < WorldMap.SIZE; row++) {
                for (int col = 0; col < WorldMap.SIZE; col++) {
                    assertTrue("Permanently hidden tile for seed " + seed + " at " + row + "," + col,
                            map.isExplored(row, col));
                }
            }
        }
    }

    @Test
    public void openingMeadowShowsEveryTerrainNeededByTheGame() {
        WorldMap map = new WorldMap();
        map.revealAround(WorldMap.START_ROW, WorldMap.START_COL);
        boolean[] visible = new boolean[TerrainType.COUNT];
        for (int row = 0; row < WorldMap.SIZE; row++) {
            for (int col = 0; col < WorldMap.SIZE; col++) {
                if (map.isExplored(row, col)) {
                    visible[map.terrainAt(row, col)] = true;
                }
            }
        }
        for (int terrain = 0; terrain < TerrainType.COUNT; terrain++) {
            assertTrue("Opening meadow is missing terrain id " + terrain, visible[terrain]);
        }
    }

    @Test
    public void waterHasNoIsolatedOrDiagonalOnlyTiles() {
        WorldMap map = new WorldMap();
        for (int row = 1; row < WorldMap.SIZE - 1; row++) {
            for (int col = 1; col < WorldMap.SIZE - 1; col++) {
                if (map.terrainAt(row, col) != TerrainType.WATER) {
                    continue;
                }
                int neighbors = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if ((dr != 0 || dc != 0)
                                && map.terrainAt(row + dr, col + dc) == TerrainType.WATER) {
                            neighbors++;
                        }
                    }
                }
                assertTrue("Water spike at " + row + "," + col, neighbors >= 3);
            }
        }
    }

    @Test
    public void everyWaterCardinalMaskExistsInTheKenneyBlobTileset() {
        WorldMap map = new WorldMap();
        for (int row = 1; row < WorldMap.SIZE - 1; row++) {
            for (int col = 1; col < WorldMap.SIZE - 1; col++) {
                if (map.terrainAt(row, col) != TerrainType.WATER) {
                    continue;
                }
                int landMask = 0;
                landMask |= map.terrainAt(row - 1, col) == TerrainType.WATER ? 0 : 1;
                landMask |= map.terrainAt(row + 1, col) == TerrainType.WATER ? 0 : 2;
                landMask |= map.terrainAt(row, col - 1) == TerrainType.WATER ? 0 : 4;
                landMask |= map.terrainAt(row, col + 1) == TerrainType.WATER ? 0 : 8;
                int landSides = Integer.bitCount(landMask);
                boolean oppositePair = landMask == 3 || landMask == 12;
                assertTrue("Unsupported Kenney water mask " + landMask + " at " + row + "," + col,
                        landSides <= 2 && !oppositePair);
            }
        }
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
