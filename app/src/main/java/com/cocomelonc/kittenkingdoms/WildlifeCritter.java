/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/**
 * A purely decorative background creature: no AI, no interaction with the player or the
 * economy, just a slow random walk between nearby tiles so the world feels a little less empty.
 * Not persisted in save files, the same way terrain regenerates from a fixed seed instead.
 */
final class WildlifeCritter {
    static final int HEDGEHOG = 0;
    static final int RABBIT = 1;
    static final int DUCKLING = 2;
    static final int BEE = 3;

    final int species;
    float worldX;
    float worldY;
    float targetX;
    float targetY;
    float speed;
    float facing = 1f;
    float retargetIn;

    WildlifeCritter(int species) {
        this.species = species;
    }
}
