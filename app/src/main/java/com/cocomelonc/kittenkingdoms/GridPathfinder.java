/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Deterministic four-direction breadth-first routes for kittens on the occupied tile map. */
final class GridPathfinder {
    private static final int[] ROW_STEP = {-1, 0, 1, 0};
    private static final int[] COL_STEP = {0, 1, 0, -1};

    private GridPathfinder() {
    }

    static ArrayDeque<Integer> toCell(WorldMap map, int startRow, int startCol,
                                      int targetRow, int targetCol) {
        return search(map, startRow, startCol, targetRow, targetCol, false);
    }

    static ArrayDeque<Integer> besideCell(WorldMap map, int startRow, int startCol,
                                          int targetRow, int targetCol) {
        return search(map, startRow, startCol, targetRow, targetCol, true);
    }

    private static ArrayDeque<Integer> search(WorldMap map, int startRow, int startCol,
                                              int targetRow, int targetCol, boolean stopBeside) {
        ArrayDeque<Integer> empty = new ArrayDeque<>();
        if (!map.inBounds(startRow, startCol) || !map.inBounds(targetRow, targetCol)) {
            return empty;
        }
        if (isGoal(startRow, startCol, targetRow, targetCol, stopBeside)) {
            return empty;
        }
        if (!stopBeside && !map.isWalkable(targetRow, targetCol)) {
            return empty;
        }

        int total = WorldMap.SIZE * WorldMap.SIZE;
        int[] parent = new int[total];
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[total];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        int start = encode(startRow, startCol);
        int goal = -1;
        visited[start] = true;
        open.add(start);

        while (!open.isEmpty() && goal < 0) {
            int current = open.removeFirst();
            int currentRow = current / WorldMap.SIZE;
            int currentCol = current % WorldMap.SIZE;
            for (int direction = 0; direction < ROW_STEP.length; direction++) {
                int nextRow = currentRow + ROW_STEP[direction];
                int nextCol = currentCol + COL_STEP[direction];
                if (!map.isWalkable(nextRow, nextCol)) {
                    continue;
                }
                int next = encode(nextRow, nextCol);
                if (visited[next]) {
                    continue;
                }
                visited[next] = true;
                parent[next] = current;
                if (isGoal(nextRow, nextCol, targetRow, targetCol, stopBeside)) {
                    goal = next;
                    break;
                }
                open.addLast(next);
            }
        }

        if (goal < 0) {
            return empty;
        }
        ArrayDeque<Integer> route = new ArrayDeque<>();
        for (int cursor = goal; cursor != start; cursor = parent[cursor]) {
            route.addFirst(cursor);
        }
        return route;
    }

    private static boolean isGoal(int row, int col, int targetRow, int targetCol, boolean beside) {
        return beside
                ? Math.abs(row - targetRow) + Math.abs(col - targetCol) == 1
                : row == targetRow && col == targetCol;
    }

    private static int encode(int row, int col) {
        return row * WorldMap.SIZE + col;
    }
}
