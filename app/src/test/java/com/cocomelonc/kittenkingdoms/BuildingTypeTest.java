/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BuildingTypeTest {
    @Test
    public void registryContainsExactlyThirtyTwoOrderedEntries() {
        BuildingType[] all = BuildingType.createAll();
        assertEquals(32, BuildingType.COUNT);
        assertEquals(BuildingType.COUNT, all.length);
        for (int i = 0; i < all.length; i++) {
            assertEquals(i, all[i].id);
        }
    }

    @Test
    public void everyCatalogueBuildingIsPlayerBuildable() {
        for (BuildingType type : BuildingType.createAll()) {
            assertTrue(type.playerBuildable);
        }
    }

    @Test
    public void everyRequiredTechReferencesAnExistingTechNode() {
        TechNode[] techs = TechNode.createAll();
        for (BuildingType type : BuildingType.createAll()) {
            if (type.requiredTechId != TechNode.NONE) {
                assertTrue("Unknown tech id " + type.requiredTechId + " on building " + type.id,
                        type.requiredTechId >= 0 && type.requiredTechId < techs.length);
            }
        }
    }

    @Test
    public void everyRequiredAdjacentTerrainReferencesAnExistingTerrain() {
        TerrainType[] terrains = TerrainType.createAll();
        for (BuildingType type : BuildingType.createAll()) {
            if (type.requiredAdjacentTerrain != TerrainType.NONE) {
                assertTrue(type.requiredAdjacentTerrain >= 0
                        && type.requiredAdjacentTerrain < terrains.length);
            }
        }
    }

    @Test
    public void noBuildingCostIsNegative() {
        for (BuildingType type : BuildingType.createAll()) {
            for (int amount : type.cost) {
                assertTrue(amount >= 0);
            }
        }
    }
}
