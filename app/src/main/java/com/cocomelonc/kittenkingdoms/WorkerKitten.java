/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import java.util.ArrayDeque;

/** One visible worker and its current logistics job. Paths are rebuilt after loading a save. */
final class WorkerKitten {
    static final int IDLE = 0;
    static final int TO_CONSTRUCTION = 1;
    static final int CONSTRUCTING = 2;
    static final int TO_WORK = 3;
    static final int WORKING = 4;
    static final int COLLECTING = 5;
    static final int TO_STORAGE = 6;

    final int id;
    final ArrayDeque<Integer> path = new ArrayDeque<>();
    int row;
    int col;
    float visualRow;
    float visualCol;
    int facingDirection = KingdomWorld.DIRECTION_DOWN;
    int state = IDLE;
    int assignedBuildingId = BuildingType.NONE;
    int taskBuildingId = BuildingType.NONE;
    int carriedResourceId = ResourceType.NONE;
    int carriedAmount;
    float actionTimer;

    WorkerKitten(int id, int row, int col) {
        this.id = id;
        this.row = row;
        this.col = col;
        this.visualRow = row;
        this.visualCol = col;
    }

    boolean isMoving() {
        return !path.isEmpty();
    }

    boolean isIdleAndUnassigned() {
        return state == IDLE && assignedBuildingId == BuildingType.NONE && carriedAmount == 0;
    }
}
