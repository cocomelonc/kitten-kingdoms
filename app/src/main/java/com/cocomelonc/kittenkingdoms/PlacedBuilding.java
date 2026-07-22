/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/** A stable building instance, including its construction and undelivered production state. */
final class PlacedBuilding {
    final int id;
    final int typeId;
    final int row;
    final int col;
    int turnsRemaining;
    int pendingResourceId = ResourceType.NONE;
    int pendingAmount;
    float constructionTimer;
    // Turns spent gathering toward the next batch. Transient timing state: it is not saved, so a
    // reloaded workshop simply starts its next cycle fresh (at most one interval of delay).
    int productionProgress;

    PlacedBuilding(int id, int typeId, int row, int col, int turnsRemaining) {
        this.id = id;
        this.typeId = typeId;
        this.row = row;
        this.col = col;
        this.turnsRemaining = turnsRemaining;
    }

    boolean isComplete() {
        return turnsRemaining <= 0;
    }

    boolean hasReadyGoods() {
        return pendingResourceId != ResourceType.NONE && pendingAmount > 0;
    }
}
