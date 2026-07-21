/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KingdomWorldTest {
    @Test
    public void newKingdomStartsWithExpectedDefaults() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();

        assertEquals(2, world.getPopulation());
        assertEquals(35, world.getResource(ResourceType.WOOD));
        assertEquals(25, world.getResource(ResourceType.STONE));
        assertEquals(0, world.getTurn());
        assertEquals(1, world.getBuildings().size());
        assertEquals(BuildingType.TOWN_HALL, world.getBuildings().get(0).typeId);
    }

    @Test
    public void placingBuildingDeductsCostAndOccupiesTile() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        int row = WorldMap.START_ROW;
        int col = WorldMap.START_COL + 1;

        world.selectBuildingForPlacement(BuildingType.CATNIP_FARM);
        assertTrue(world.tapCell(row, col));

        assertEquals(25, world.getResource(ResourceType.WOOD));
        assertEquals(2, world.getBuildings().size());
        assertFalse(world.getMap().isBuildable(row, col));
    }

    @Test
    public void unaffordableBuildingIsRejectedOnceResourcesRunOut() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        int row = WorldMap.START_ROW;
        int col = WorldMap.START_COL;

        world.selectBuildingForPlacement(BuildingType.CATNIP_FARM);
        assertTrue(world.tapCell(row, col + 1));
        world.selectBuildingForPlacement(BuildingType.CATNIP_FARM);
        assertTrue(world.tapCell(row, col - 1));
        world.selectBuildingForPlacement(BuildingType.CATNIP_FARM);
        assertTrue(world.tapCell(row + 1, col));

        // 30 of the starting 35 Wood is spent; a fourth 10-Wood farm cannot be afforded.
        world.selectBuildingForPlacement(BuildingType.CATNIP_FARM);
        assertFalse(world.tapCell(row - 1, col));
        assertEquals(4, world.getBuildings().size());
    }

    @Test
    public void cannotPlaceOnUnexploredTile() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();

        world.selectBuildingForPlacement(BuildingType.CATNIP_FARM);
        assertFalse(world.tapCell(5, 5));
    }

    @Test
    public void cannotPlaceWithoutRequiredAdjacentTerrain() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();

        world.selectBuildingForPlacement(BuildingType.LUMBER_CAMP);
        assertFalse(world.tapCell(WorldMap.START_ROW, WorldMap.START_COL + 1));
    }

    @Test
    public void cannotPlaceWithoutRequiredTech() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        world.setResourceForTest(ResourceType.WOOD, 100);
        world.setResourceForTest(ResourceType.STONE, 100);

        world.selectBuildingForPlacement(BuildingType.WEAVERS_COTTAGE);
        assertFalse(world.tapCell(WorldMap.START_ROW, WorldMap.START_COL + 1));

        world.selectActiveTech(TechNode.BASIC_TOOLS);
        advanceTurns(world, 5);
        world.selectActiveTech(TechNode.TEXTILE_CRAFT);
        advanceTurns(world, 10);
        assertTrue(world.isTechUnlocked(TechNode.TEXTILE_CRAFT));

        world.selectBuildingForPlacement(BuildingType.WEAVERS_COTTAGE);
        assertTrue(world.tapCell(WorldMap.START_ROW, WorldMap.START_COL + 1));
    }

    @Test
    public void techUnlockedBitsMatchesTheUnlockedTechAfterResearch() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        assertEquals(0, world.getTechUnlockedBits());

        world.selectActiveTech(TechNode.BASIC_TOOLS);
        advanceTurns(world, 5);

        assertTrue(world.isTechUnlocked(TechNode.BASIC_TOOLS));
        assertEquals(1 << TechNode.BASIC_TOOLS, world.getTechUnlockedBits());
    }

    @Test
    public void completedBuildingProducesResourcesOnEndTurn() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        int row = WorldMap.START_ROW;
        int col = WorldMap.START_COL + 1;
        world.selectBuildingForPlacement(BuildingType.CATNIP_FARM);
        world.tapCell(row, col);
        int buildTurns = BuildingType.createAll()[BuildingType.CATNIP_FARM].buildTurns;

        advanceTurns(world, buildTurns);
        int catnipAfterConstruction = world.getResource(ResourceType.CATNIP);
        world.endTurn();

        assertEquals(catnipAfterConstruction + 4, world.getResource(ResourceType.CATNIP));
    }

    @Test
    public void resourceStockpileNeverExceedsCap() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        world.selectBuildingForPlacement(BuildingType.CATNIP_FARM);
        world.tapCell(WorldMap.START_ROW, WorldMap.START_COL + 1);

        for (int i = 0; i < 60; i++) {
            world.endTurn();
            assertTrue(world.getResource(ResourceType.CATNIP) <= world.getResourceCap());
        }
    }

    @Test
    public void populationNeverShrinksEvenWithoutFish() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        int startingPopulation = world.getPopulation();

        for (int i = 0; i < 30; i++) {
            world.endTurn();
            assertTrue(world.getPopulation() >= startingPopulation);
        }
        assertEquals(startingPopulation, world.getPopulation());
    }

    @Test
    public void upkeepSoftFailIdlesBuildingWithoutDestroyingIt() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        int row = WorldMap.START_ROW;
        int col = WorldMap.START_COL + 1;
        world.placeBuildingForTest(BuildingType.WEAVERS_COTTAGE, row, col, 0);
        world.setResourceForTest(ResourceType.WOOD, 0);
        world.setResourceForTest(ResourceType.YARN, 0);

        world.endTurn();

        assertEquals(0, world.getResource(ResourceType.YARN));
        assertEquals(0, world.getResource(ResourceType.WOOD));
        assertEquals(2, world.getBuildings().size());
    }

    private static void advanceTurns(KingdomWorld world, int turns) {
        for (int i = 0; i < turns; i++) {
            world.endTurn();
        }
    }
}
