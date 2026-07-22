/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import java.util.List;

/**
 * Plain mutable transfer object for persistence. Terrain is never included here - it always
 * regenerates deterministically from {@link WorldMap#TERRAIN_SEED}.
 */
final class KingdomSaveData {
    int turn;
    int kittenRow;
    int kittenCol;
    int population;
    int techPointPool;
    int activeTechId;
    boolean[] techUnlocked;
    int[] resources;
    List<int[]> buildings;
    List<int[]> workers;
    boolean[][] explored;
    int[] diplomaticRelations;
    int[] envoyTurns;
    int[] courierTurns;
    boolean[] tradeRoutes;
}
