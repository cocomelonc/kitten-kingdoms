/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/** A building instance placed on the world map; mutable construction/placement state only. */
final class PlacedBuilding {
    final int typeId;
    final int row;
    final int col;
    int turnsRemaining;

    PlacedBuilding(int typeId, int row, int col, int turnsRemaining) {
        this.typeId = typeId;
        this.row = row;
        this.col = col;
        this.turnsRemaining = turnsRemaining;
    }

    boolean isComplete() {
        return turnsRemaining <= 0;
    }
}
