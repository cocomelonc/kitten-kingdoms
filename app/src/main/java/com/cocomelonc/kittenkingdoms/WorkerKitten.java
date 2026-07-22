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
    static final int DIPLOMACY = 7;

    // Champion (super-kitten) abilities. A normal worker is NOT_CHAMPION; a crafted champion rolls
    // exactly one of these. Every effect is deliberately small and bounded so a few champions help
    // without breaking the calm, scarcity-driven economy. Champions are also exempt from wages.
    static final int NOT_CHAMPION = -1;
    static final int ABILITY_SWIFT_PAWS = 0;      // moves and collects twice as fast
    static final int ABILITY_BOUNTIFUL = 1;       // +1 to the batch of the workshop it staffs
    static final int ABILITY_PROSPECTOR = 2;      // passively finds a little Crystal now and then
    static final int ABILITY_NURTURER = 3;        // lowers the Fish growth threshold by one
    static final int ABILITY_STURDY_BUILDER = 4;  // finishes construction twice as fast
    static final int ABILITY_COUNT = 5;

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
    int cargoSourceBuildingId = BuildingType.NONE;
    boolean releaseAfterDelivery;
    float actionTimer;
    int championAbility = NOT_CHAMPION;

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

    boolean isChampion() {
        return championAbility != NOT_CHAMPION;
    }

    boolean isIdleAndUnassigned() {
        return state == IDLE && assignedBuildingId == BuildingType.NONE && carriedAmount == 0;
    }
}
