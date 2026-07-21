/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The single continuous world grid: deterministic terrain, fog-of-war, and building occupancy.
 * Terrain is always regenerated from {@link #TERRAIN_SEED}; only fog and occupancy are saved.
 */
final class WorldMap {
    static final int SIZE = 96;
    static final long TERRAIN_SEED = 3826L;
    static final int START_ROW = SIZE / 2;
    static final int START_COL = SIZE / 2;
    private static final int PROTECTED_RADIUS = 3;
    private static final int REVEAL_RADIUS = 3;
    private static final int GUARANTEE_CHECK_RADIUS = 13;
    private static final int GUARANTEE_PATCH_RADIUS = 4;
    private static final int GUARANTEE_ANCHOR_DISTANCE = 11;

    private final byte[][] terrain;
    private final boolean[][] explored;
    private final boolean[][] occupied;
    private final TerrainType[] terrainTypes;

    WorldMap() {
        this(TERRAIN_SEED);
    }

    WorldMap(long seed) {
        terrainTypes = TerrainType.createAll();
        terrain = generateTerrain(seed);
        explored = new boolean[SIZE][SIZE];
        occupied = new boolean[SIZE][SIZE];
    }

    int terrainAt(int row, int col) {
        return terrain[row][col];
    }

    boolean inBounds(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    boolean isWalkable(int row, int col) {
        return inBounds(row, col) && terrainTypes[terrain[row][col]].walkable && !occupied[row][col];
    }

    boolean isBuildable(int row, int col) {
        return inBounds(row, col) && terrainTypes[terrain[row][col]].buildable && !occupied[row][col];
    }

    boolean isExplored(int row, int col) {
        return inBounds(row, col) && explored[row][col];
    }

    boolean isOccupied(int row, int col) {
        return inBounds(row, col) && occupied[row][col];
    }

    void markOccupied(int row, int col) {
        occupied[row][col] = true;
    }

    boolean hasAdjacentTerrain(int row, int col, int terrainId) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int nr = row + dr;
                int nc = col + dc;
                if (inBounds(nr, nc) && terrain[nr][nc] == terrainId) {
                    return true;
                }
            }
        }
        return false;
    }

    void revealAround(int row, int col) {
        int minRow = Math.max(0, row - REVEAL_RADIUS);
        int maxRow = Math.min(SIZE - 1, row + REVEAL_RADIUS);
        int minCol = Math.max(0, col - REVEAL_RADIUS);
        int maxCol = Math.min(SIZE - 1, col + REVEAL_RADIUS);
        float radiusSquared = REVEAL_RADIUS * REVEAL_RADIUS;
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                float dr = r - row;
                float dc = c - col;
                if (dr * dr + dc * dc <= radiusSquared) {
                    explored[r][c] = true;
                }
            }
        }
    }

    boolean[][] exploredSnapshot() {
        boolean[][] copy = new boolean[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(explored[r], 0, copy[r], 0, SIZE);
        }
        return copy;
    }

    void restoreExplored(boolean[][] snapshot) {
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(snapshot[r], 0, explored[r], 0, SIZE);
        }
    }

    private static byte[][] generateTerrain(long seed) {
        byte[][] grid = new byte[SIZE][SIZE];
        Random random = new Random(seed);
        growBlobs(grid, random, TerrainType.WATER, 6, 140);
        growBlobs(grid, random, TerrainType.FOREST, 12, 110);
        growBlobs(grid, random, TerrainType.HILL, 7, 70);
        growBlobs(grid, random, TerrainType.STONE_OUTCROP, 9, 75);
        guaranteeNearbyTerrain(grid, TerrainType.FOREST, START_ROW - GUARANTEE_ANCHOR_DISTANCE, START_COL);
        guaranteeNearbyTerrain(grid, TerrainType.WATER, START_ROW, START_COL + GUARANTEE_ANCHOR_DISTANCE);
        guaranteeNearbyTerrain(grid, TerrainType.STONE_OUTCROP, START_ROW + GUARANTEE_ANCHOR_DISTANCE, START_COL);
        clearProtectedZone(grid);
        repairConnectivity(grid);
        return grid;
    }

    /**
     * Guarantees every walkable tile is reachable from the start, regardless of how the random
     * blobs landed: floods from start over walkable terrain, then for any walkable tile the
     * flood missed, carves the shortest possible connection back to the reached region by
     * converting whatever impassable terrain stands in the way to grass.
     */
    private static void repairConnectivity(byte[][] grid) {
        boolean[][] reached = floodFillWalkable(grid);
        int[] unreached = findUnreachedWalkable(grid, reached);
        while (unreached != null) {
            carvePathToReached(grid, reached, unreached[0], unreached[1]);
            reached = floodFillWalkable(grid);
            unreached = findUnreachedWalkable(grid, reached);
        }
    }

    private static boolean[][] floodFillWalkable(byte[][] grid) {
        boolean[][] reached = new boolean[SIZE][SIZE];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        reached[START_ROW][START_COL] = true;
        queue.add(new int[]{START_ROW, START_COL});
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nr = current[0] + dr[i];
                int nc = current[1] + dc[i];
                if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE || reached[nr][nc]) {
                    continue;
                }
                if (!isRawWalkable(grid, nr, nc)) {
                    continue;
                }
                reached[nr][nc] = true;
                queue.add(new int[]{nr, nc});
            }
        }
        return reached;
    }

    private static int[] findUnreachedWalkable(byte[][] grid, boolean[][] reached) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (!reached[r][c] && isRawWalkable(grid, r, c)) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    private static void carvePathToReached(byte[][] grid, boolean[][] reached, int targetRow, int targetCol) {
        int total = SIZE * SIZE;
        int[] parent = new int[total];
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[total];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = targetRow * SIZE + targetCol;
        visited[start] = true;
        queue.add(start);
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};
        int goal = -1;
        while (!queue.isEmpty()) {
            int current = queue.poll();
            int row = current / SIZE;
            int col = current % SIZE;
            if (reached[row][col]) {
                goal = current;
                break;
            }
            for (int i = 0; i < 4; i++) {
                int nr = row + dr[i];
                int nc = col + dc[i];
                if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE) {
                    continue;
                }
                int next = nr * SIZE + nc;
                if (visited[next]) {
                    continue;
                }
                visited[next] = true;
                parent[next] = current;
                queue.add(next);
            }
        }
        if (goal == -1) {
            return;
        }
        for (int cursor = goal; cursor != -1 && cursor != start; cursor = parent[cursor]) {
            int row = cursor / SIZE;
            int col = cursor % SIZE;
            if (!isRawWalkable(grid, row, col)) {
                grid[row][col] = TerrainType.GRASS;
            }
        }
    }

    private static boolean isRawWalkable(byte[][] grid, int row, int col) {
        int terrainId = grid[row][col];
        return terrainId == TerrainType.GRASS || terrainId == TerrainType.FOREST || terrainId == TerrainType.HILL;
    }

    /**
     * Ensures every resource-gating terrain kind exists within easy walking distance of the
     * kitten's start, regardless of how the random blobs happened to land, by force-filling a
     * small guaranteed patch at a fixed nearby anchor when the seed didn't already provide one.
     */
    private static void guaranteeNearbyTerrain(byte[][] grid, int terrainId, int anchorRow, int anchorCol) {
        if (hasTerrainWithinRadius(grid, terrainId, GUARANTEE_CHECK_RADIUS)) {
            return;
        }
        for (int r = anchorRow - GUARANTEE_PATCH_RADIUS; r <= anchorRow + GUARANTEE_PATCH_RADIUS; r++) {
            for (int c = anchorCol - GUARANTEE_PATCH_RADIUS; c <= anchorCol + GUARANTEE_PATCH_RADIUS; c++) {
                if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) {
                    continue;
                }
                int dr = r - anchorRow;
                int dc = c - anchorCol;
                if (dr * dr + dc * dc <= GUARANTEE_PATCH_RADIUS * GUARANTEE_PATCH_RADIUS) {
                    grid[r][c] = (byte) terrainId;
                }
            }
        }
    }

    private static boolean hasTerrainWithinRadius(byte[][] grid, int terrainId, int radius) {
        for (int r = START_ROW - radius; r <= START_ROW + radius; r++) {
            for (int c = START_COL - radius; c <= START_COL + radius; c++) {
                if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) {
                    continue;
                }
                int dr = r - START_ROW;
                int dc = c - START_COL;
                if (dr * dr + dc * dc <= radius * radius && grid[r][c] == terrainId) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void growBlobs(byte[][] grid, Random random, int terrainId, int blobCount, int steps) {
        for (int blob = 0; blob < blobCount; blob++) {
            int seedRow = random.nextInt(SIZE);
            int seedCol = random.nextInt(SIZE);
            if (isProtected(seedRow, seedCol)) {
                continue;
            }
            List<int[]> frontier = new ArrayList<>();
            frontier.add(new int[]{seedRow, seedCol});
            grid[seedRow][seedCol] = (byte) terrainId;
            int grown = 1;
            int maxAttempts = steps * 20;
            int attempts = 0;
            while (grown < steps && !frontier.isEmpty() && attempts < maxAttempts) {
                attempts++;
                int index = random.nextInt(frontier.size());
                int[] cell = frontier.remove(index);
                int[] dr = {-1, 1, 0, 0};
                int[] dc = {0, 0, -1, 1};
                int direction = random.nextInt(4);
                int nr = cell[0] + dr[direction];
                int nc = cell[1] + dc[direction];
                if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE
                        || isProtected(nr, nc) || grid[nr][nc] != TerrainType.GRASS) {
                    frontier.add(cell);
                    continue;
                }
                grid[nr][nc] = (byte) terrainId;
                frontier.add(cell);
                frontier.add(new int[]{nr, nc});
                grown++;
            }
        }
    }

    private static void clearProtectedZone(byte[][] grid) {
        for (int r = START_ROW - PROTECTED_RADIUS; r <= START_ROW + PROTECTED_RADIUS; r++) {
            for (int c = START_COL - PROTECTED_RADIUS; c <= START_COL + PROTECTED_RADIUS; c++) {
                if (r >= 0 && r < SIZE && c >= 0 && c < SIZE) {
                    grid[r][c] = TerrainType.GRASS;
                }
            }
        }
    }

    private static boolean isProtected(int row, int col) {
        int dr = row - START_ROW;
        int dc = col - START_COL;
        return dr * dr + dc * dc <= PROTECTED_RADIUS * PROTECTED_RADIUS;
    }
}
