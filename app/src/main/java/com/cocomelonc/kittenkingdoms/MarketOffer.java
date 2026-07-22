/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/** One exact, deterministic exchange offered by a neighbouring kingdom. */
final class MarketOffer {
    final int giveResourceId;
    final int giveAmount;
    final int receiveResourceId;
    final int receiveAmount;

    MarketOffer(int giveResourceId, int giveAmount, int receiveResourceId, int receiveAmount) {
        if (giveResourceId < 0 || giveResourceId >= ResourceType.COUNT
                || receiveResourceId < 0 || receiveResourceId >= ResourceType.COUNT
                || giveResourceId == receiveResourceId || giveAmount <= 0 || receiveAmount <= 0) {
            throw new IllegalArgumentException("Invalid market offer");
        }
        this.giveResourceId = giveResourceId;
        this.giveAmount = giveAmount;
        this.receiveResourceId = receiveResourceId;
        this.receiveAmount = receiveAmount;
    }

    boolean isPlayerSelling() {
        return receiveResourceId == ResourceType.CRYSTALS;
    }
}
