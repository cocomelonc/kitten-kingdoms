/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
    private static final int REVEAL_RADIUS = 6;
    private static final int SHORE_BUFFER = 1;

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
        revealFullySurveyedFeatures();
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
        // Older saves may already contain a completely explored shoreline with a fogged-in
        // lake or outcrop core. Apply the current survey rule as part of migration/load.
        revealFullySurveyedFeatures();
    }

    /**
     * Reveals the inaccessible core of a natural feature once its entire reachable boundary has
     * been surveyed. Water and stone cannot be walked on, so a purely radius-based fog rule can
     * otherwise leave permanent dark cells in the middle of a wide lake or outcrop. The feature
     * remains hidden while even one adjacent walkable shore tile is unexplored, preventing a
     * single shoreline glimpse from revealing a distant lake-and-river network.
     */
    private void revealFullySurveyedFeatures() {
        boolean[][] visited = new boolean[SIZE][SIZE];
        int[] cardinalRow = {-1, 1, 0, 0};
        int[] cardinalCol = {0, 0, -1, 1};

        for (int startRow = 0; startRow < SIZE; startRow++) {
            for (int startCol = 0; startCol < SIZE; startCol++) {
                if (visited[startRow][startCol] || isTerrainWalkable(startRow, startCol)) {
                    continue;
                }

                int featureTerrain = terrain[startRow][startCol];
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                ArrayList<Integer> featureCells = new ArrayList<>();
                queue.add(encode(startRow, startCol));
                visited[startRow][startCol] = true;
                boolean hasWalkableBoundary = false;
                boolean boundaryFullyExplored = true;

                while (!queue.isEmpty()) {
                    int encoded = queue.removeFirst();
                    int row = encoded / SIZE;
                    int col = encoded % SIZE;
                    featureCells.add(encoded);

                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            if (dr == 0 && dc == 0) {
                                continue;
                            }
                            int neighborRow = row + dr;
                            int neighborCol = col + dc;
                            if (!inBounds(neighborRow, neighborCol)
                                    || !isTerrainWalkable(neighborRow, neighborCol)) {
                                continue;
                            }
                            hasWalkableBoundary = true;
                            if (!explored[neighborRow][neighborCol]) {
                                boundaryFullyExplored = false;
                            }
                        }
                    }

                    for (int direction = 0; direction < cardinalRow.length; direction++) {
                        int neighborRow = row + cardinalRow[direction];
                        int neighborCol = col + cardinalCol[direction];
                        if (!inBounds(neighborRow, neighborCol)
                                || visited[neighborRow][neighborCol]
                                || terrain[neighborRow][neighborCol] != featureTerrain) {
                            continue;
                        }
                        visited[neighborRow][neighborCol] = true;
                        queue.add(encode(neighborRow, neighborCol));
                    }
                }

                if (hasWalkableBoundary && boundaryFullyExplored) {
                    for (int encoded : featureCells) {
                        explored[encoded / SIZE][encoded % SIZE] = true;
                    }
                }
            }
        }

        // Absolute completion guarantee for unusual generated layouts where one impassable
        // feature is completely nested inside another and therefore has no walkable shoreline.
        if (isEveryWalkableTileExplored()) {
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    explored[row][col] = true;
                }
            }
        }
    }

    private boolean isEveryWalkableTileExplored() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (isTerrainWalkable(row, col) && !explored[row][col]) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isTerrainWalkable(int row, int col) {
        return terrainTypes[terrain[row][col]].walkable;
    }

    private static int encode(int row, int col) {
        return row * SIZE + col;
    }

    private static byte[][] generateTerrain(long seed) {
        byte[][] grid = new byte[SIZE][SIZE];
        Random random = new Random(seed);

        boolean[][] water = new boolean[SIZE][SIZE];
        List<Lake> lakes = createLakes(random);
        for (Lake lake : lakes) {
            paintOrganicLake(water, lake);
        }
        smoothWater(water, 2);
        paintRiver(water, lakes.get(1), lakes.get(2), random);
        paintRiver(water, lakes.get(3), lakes.get(4), random);
        smoothWater(water, 1);
        removeUnsupportedWaterTips(water);
        clearProtectedWater(water);
        copyWaterToGrid(water, grid);

        // Hand-placed, softly shaped resource areas make the opening view immediately readable.
        paintOrganicTerrain(grid, random, TerrainType.FOREST,
                START_ROW - 6, START_COL - 3, 3.2f, 2.7f);
        paintOrganicTerrain(grid, random, TerrainType.STONE_OUTCROP,
                START_ROW + 6, START_COL - 3, 3.0f, 2.3f);
        paintOrganicTerrain(grid, random, TerrainType.HILL,
                START_ROW - 5, START_COL + 5, 3.0f, 2.2f);

        paintRandomTerrain(grid, random, TerrainType.FOREST, 15, 4.0f, 7.0f);
        paintRandomTerrain(grid, random, TerrainType.HILL, 9, 3.2f, 5.4f);
        paintRandomTerrain(grid, random, TerrainType.STONE_OUTCROP, 11, 2.5f, 4.3f);
        clearProtectedZone(grid);
        repairConnectivity(grid);
        return grid;
    }

    /**
     * Builds broad lakes instead of growing single random frontier cells. The first lake is a
     * small welcoming pond beside the starting meadow; the others are seeded but organic.
     */
    private static List<Lake> createLakes(Random random) {
        List<Lake> lakes = new ArrayList<>();
        lakes.add(new Lake(START_ROW + 1f, START_COL + 7f, 3.4f, 2.5f, 0.8f));
        while (lakes.size() < 7) {
            float row = 11f + random.nextFloat() * (SIZE - 22f);
            float col = 11f + random.nextFloat() * (SIZE - 22f);
            if (Math.hypot(row - START_ROW, col - START_COL) < 17f) {
                continue;
            }
            float radiusX = 5.2f + random.nextFloat() * 3.8f;
            float radiusY = 4.0f + random.nextFloat() * 3.0f;
            lakes.add(new Lake(row, col, radiusX, radiusY,
                    random.nextFloat() * (float) Math.PI * 2f));
        }
        return lakes;
    }

    private static void paintOrganicLake(boolean[][] water, Lake lake) {
        int minRow = Math.max(1, (int) Math.floor(lake.row - lake.radiusY * 1.25f));
        int maxRow = Math.min(SIZE - 2, (int) Math.ceil(lake.row + lake.radiusY * 1.25f));
        int minCol = Math.max(1, (int) Math.floor(lake.col - lake.radiusX * 1.25f));
        int maxCol = Math.min(SIZE - 2, (int) Math.ceil(lake.col + lake.radiusX * 1.25f));
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                float dy = (row - lake.row) / lake.radiusY;
                float dx = (col - lake.col) / lake.radiusX;
                float angle = (float) Math.atan2(dy, dx);
                float softEdge = 1f
                        + 0.10f * (float) Math.sin(angle * 3f + lake.phase)
                        + 0.055f * (float) Math.sin(angle * 5f - lake.phase * 0.7f);
                if (Math.hypot(dx, dy) <= softEdge && !isProtected(row, col)) {
                    water[row][col] = true;
                }
            }
        }
    }

    /** Draws a wide, gently meandering connection whose ends disappear naturally into lakes. */
    private static void paintRiver(boolean[][] water, Lake from, Lake to, Random random) {
        float rowDelta = to.row - from.row;
        float colDelta = to.col - from.col;
        float length = Math.max(1f, (float) Math.hypot(rowDelta, colDelta));
        float normalRow = -colDelta / length;
        float normalCol = rowDelta / length;
        float bend = (random.nextFloat() - 0.5f) * 11f;
        float phase = random.nextFloat() * (float) Math.PI * 2f;
        int samples = Math.max(40, Math.round(length * 5f));
        for (int i = 0; i <= samples; i++) {
            float t = i / (float) samples;
            float offset = (float) Math.sin(Math.PI * t) * bend
                    + (float) Math.sin(Math.PI * 4f * t + phase) * 1.25f;
            float row = from.row + rowDelta * t + normalRow * offset;
            float col = from.col + colDelta * t + normalCol * offset;
            float radius = 1.55f + 0.35f * (float) Math.sin(Math.PI * 3f * t + phase);
            stampWaterDisc(water, row, col, radius);
        }
    }

    private static void stampWaterDisc(boolean[][] water, float centerRow, float centerCol, float radius) {
        int minRow = Math.max(1, (int) Math.floor(centerRow - radius));
        int maxRow = Math.min(SIZE - 2, (int) Math.ceil(centerRow + radius));
        int minCol = Math.max(1, (int) Math.floor(centerCol - radius));
        int maxCol = Math.min(SIZE - 2, (int) Math.ceil(centerCol + radius));
        float radiusSquared = radius * radius;
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                float dr = row - centerRow;
                float dc = col - centerCol;
                if (dr * dr + dc * dc <= radiusSquared && !isProtected(row, col)) {
                    water[row][col] = true;
                }
            }
        }
    }

    /** Removes pinholes and one-tile spikes while preserving rivers that are at least two cells wide. */
    private static void smoothWater(boolean[][] water, int passes) {
        for (int pass = 0; pass < passes; pass++) {
            boolean[][] next = new boolean[SIZE][SIZE];
            for (int row = 1; row < SIZE - 1; row++) {
                for (int col = 1; col < SIZE - 1; col++) {
                    int neighbors = 0;
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            if ((dr != 0 || dc != 0) && water[row + dr][col + dc]) {
                                neighbors++;
                            }
                        }
                    }
                    next[row][col] = water[row][col] ? neighbors >= 3 : neighbors >= 6;
                }
            }
            water = copyBooleanGrid(next, water);
        }
    }

    private static boolean[][] copyBooleanGrid(boolean[][] source, boolean[][] destination) {
        for (int row = 0; row < SIZE; row++) {
            System.arraycopy(source[row], 0, destination[row], 0, SIZE);
        }
        return destination;
    }

    /** The Kenney shoreline is a 3x3 blob set; remove one-cell water needles it cannot express. */
    private static void removeUnsupportedWaterTips(boolean[][] water) {
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            boolean[][] next = new boolean[SIZE][SIZE];
            for (int row = 0; row < SIZE; row++) {
                System.arraycopy(water[row], 0, next[row], 0, SIZE);
            }
            for (int row = 1; row < SIZE - 1; row++) {
                for (int col = 1; col < SIZE - 1; col++) {
                    if (!water[row][col]) {
                        continue;
                    }
                    int landSides = 0;
                    landSides += water[row - 1][col] ? 0 : 1;
                    landSides += water[row + 1][col] ? 0 : 1;
                    landSides += water[row][col - 1] ? 0 : 1;
                    landSides += water[row][col + 1] ? 0 : 1;
                    if (landSides >= 3) {
                        next[row][col] = false;
                        changed = true;
                    }
                }
            }
            copyBooleanGrid(next, water);
            if (!changed) {
                return;
            }
        }
    }

    private static void clearProtectedWater(boolean[][] water) {
        for (int row = START_ROW - PROTECTED_RADIUS; row <= START_ROW + PROTECTED_RADIUS; row++) {
            for (int col = START_COL - PROTECTED_RADIUS; col <= START_COL + PROTECTED_RADIUS; col++) {
                if (isProtected(row, col)) {
                    water[row][col] = false;
                }
            }
        }
    }

    private static void copyWaterToGrid(boolean[][] water, byte[][] grid) {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (water[row][col]) {
                    grid[row][col] = TerrainType.WATER;
                }
            }
        }
    }

    private static void paintRandomTerrain(byte[][] grid, Random random, int terrainId,
            int patchCount, float minRadius, float maxRadius) {
        for (int patch = 0; patch < patchCount; patch++) {
            int row = 5 + random.nextInt(SIZE - 10);
            int col = 5 + random.nextInt(SIZE - 10);
            float radiusX = minRadius + random.nextFloat() * (maxRadius - minRadius);
            float radiusY = minRadius + random.nextFloat() * (maxRadius - minRadius);
            paintOrganicTerrain(grid, random, terrainId, row, col, radiusX, radiusY);
        }
    }

    private static void paintOrganicTerrain(byte[][] grid, Random random, int terrainId,
            float centerRow, float centerCol, float radiusX, float radiusY) {
        float phase = random.nextFloat() * (float) Math.PI * 2f;
        int minRow = Math.max(1, (int) Math.floor(centerRow - radiusY * 1.2f));
        int maxRow = Math.min(SIZE - 2, (int) Math.ceil(centerRow + radiusY * 1.2f));
        int minCol = Math.max(1, (int) Math.floor(centerCol - radiusX * 1.2f));
        int maxCol = Math.min(SIZE - 2, (int) Math.ceil(centerCol + radiusX * 1.2f));
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                if (grid[row][col] != TerrainType.GRASS || isProtected(row, col)
                        || hasWaterWithin(grid, row, col, SHORE_BUFFER)) {
                    continue;
                }
                float dy = (row - centerRow) / radiusY;
                float dx = (col - centerCol) / radiusX;
                float angle = (float) Math.atan2(dy, dx);
                float softEdge = 1f + 0.09f * (float) Math.sin(angle * 4f + phase);
                if (Math.hypot(dx, dy) <= softEdge) {
                    grid[row][col] = (byte) terrainId;
                }
            }
        }
    }

    private static boolean hasWaterWithin(byte[][] grid, int row, int col, int radius) {
        for (int dr = -radius; dr <= radius; dr++) {
            for (int dc = -radius; dc <= radius; dc++) {
                if (grid[row + dr][col + dc] == TerrainType.WATER) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Guarantees every walkable tile is reachable without slicing straight grass corridors
     * through a lake. Any tiny land island cut off by water is absorbed into that lake; an area
     * enclosed by rock becomes part of the outcrop instead.
     */
    private static void repairConnectivity(byte[][] grid) {
        boolean[][] reached = floodFillWalkable(grid);
        int[] unreached = findUnreachedWalkable(grid, reached);
        while (unreached != null) {
            absorbUnreachableIsland(grid, reached, unreached[0], unreached[1]);
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

    private static void absorbUnreachableIsland(byte[][] grid, boolean[][] reached,
            int startRow, int startCol) {
        boolean[][] inIsland = new boolean[SIZE][SIZE];
        List<int[]> island = new ArrayList<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        inIsland[startRow][startCol] = true;
        queue.add(new int[]{startRow, startCol});
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};
        int waterBorder = 0;
        int stoneBorder = 0;
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int row = current[0];
            int col = current[1];
            island.add(current);
            for (int i = 0; i < 4; i++) {
                int nr = row + dr[i];
                int nc = col + dc[i];
                if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE) {
                    continue;
                }
                if (grid[nr][nc] == TerrainType.WATER) {
                    waterBorder++;
                } else if (grid[nr][nc] == TerrainType.STONE_OUTCROP) {
                    stoneBorder++;
                }
                if (reached[nr][nc] || inIsland[nr][nc] || !isRawWalkable(grid, nr, nc)) {
                    continue;
                }
                inIsland[nr][nc] = true;
                queue.add(new int[]{nr, nc});
            }
        }
        byte replacement = waterBorder >= stoneBorder
                ? (byte) TerrainType.WATER : (byte) TerrainType.STONE_OUTCROP;
        for (int[] cell : island) {
            grid[cell[0]][cell[1]] = replacement;
        }
    }

    private static boolean isRawWalkable(byte[][] grid, int row, int col) {
        int terrainId = grid[row][col];
        return terrainId == TerrainType.GRASS || terrainId == TerrainType.FOREST || terrainId == TerrainType.HILL;
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

    private static final class Lake {
        final float row;
        final float col;
        final float radiusX;
        final float radiusY;
        final float phase;

        Lake(float row, float col, float radiusX, float radiusY, float phase) {
            this.row = row;
            this.col = col;
            this.radiusX = radiusX;
            this.radiusY = radiusY;
            this.phase = phase;
        }
    }
}
