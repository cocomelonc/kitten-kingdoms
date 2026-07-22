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
        assertEquals(2, world.getWorkerCount());
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
    public void workerBuildsCollectsAndDeliversProduction() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        int row = WorldMap.START_ROW;
        int col = WorldMap.START_COL + 1;
        world.selectBuildingForPlacement(BuildingType.CATNIP_FARM);
        world.tapCell(row, col);
        PlacedBuilding farm = world.getBuildings().get(1);

        world.endTurn();
        assertFalse(farm.isComplete());
        advanceTime(world, 5f);
        assertTrue(farm.isComplete());
        assertEquals(0, world.getResource(ResourceType.CATNIP));
        world.endTurn();
        assertEquals(4, farm.pendingAmount);
        assertEquals(0, world.getResource(ResourceType.CATNIP));
        advanceTime(world, 5f);

        assertEquals(4, world.getResource(ResourceType.CATNIP));
        assertEquals(0, farm.pendingAmount);
    }

    @Test
    public void resourceStockpileNeverExceedsCap() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        world.placeBuildingForTest(BuildingType.CATNIP_FARM,
                WorldMap.START_ROW, WorldMap.START_COL + 1, 0);
        PlacedBuilding farm = world.getBuildings().get(1);
        assertEquals(KingdomWorld.WORKFORCE_OK, world.assignWorker(farm.id));
        advanceTime(world, 2f);

        for (int i = 0; i < 60; i++) {
            world.endTurn();
            advanceTime(world, 2f);
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
        PlacedBuilding weaver = world.getBuildings().get(1);
        assertEquals(KingdomWorld.WORKFORCE_OK, world.assignWorker(weaver.id));
        advanceTime(world, 2f);

        world.endTurn();

        assertEquals(0, world.getResource(ResourceType.YARN));
        assertEquals(0, world.getResource(ResourceType.WOOD));
        assertEquals(2, world.getBuildings().size());
    }

    @Test
    public void waitingConstructionStartsWhenAWorkerIsReleased() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        world.setResourceForTest(ResourceType.WOOD, 100);
        int row = WorldMap.START_ROW;
        int col = WorldMap.START_COL;
        int[][] sites = {{row, col + 1}, {row, col - 1}, {row + 1, col}};
        for (int[] site : sites) {
            world.selectBuildingForPlacement(BuildingType.CATNIP_FARM);
            assertTrue(world.tapCell(site[0], site[1]));
        }

        advanceTime(world, 6f);
        PlacedBuilding first = world.getBuildings().get(1);
        PlacedBuilding third = world.getBuildings().get(3);
        assertTrue(first.isComplete());
        assertFalse(third.isComplete());

        assertEquals(KingdomWorld.WORKFORCE_OK, world.unassignWorker(first.id));
        advanceTime(world, 6f);
        assertTrue(third.isComplete());
    }

    @Test
    public void hiringUsesResidentsAndPaysTheAdvertisedCost() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        world.setPopulationForTest(3);
        world.setResourceForTest(ResourceType.FISH, 9);
        world.setResourceForTest(ResourceType.CATNIP, 5);

        assertEquals(KingdomWorld.WORKFORCE_OK, world.hireWorker());

        assertEquals(3, world.getWorkerCount());
        assertEquals(4, world.getResource(ResourceType.FISH));
        assertEquals(3, world.getResource(ResourceType.CATNIP));
        assertEquals(KingdomWorld.WORKFORCE_POPULATION_LIMIT, world.hireWorker());
    }

    private static void advanceTurns(KingdomWorld world, int turns) {
        for (int i = 0; i < turns; i++) {
            world.endTurn();
        }
    }

    private static void advanceTime(KingdomWorld world, float seconds) {
        int steps = (int) Math.ceil(seconds / 0.1f);
        for (int i = 0; i < steps; i++) {
            world.update(0.1f);
        }
    }
}
