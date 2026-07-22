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
    public void fullStorageLeavesGoodsAtWorkshopAndWorkerCanBeReleased() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        world.placeBuildingForTest(BuildingType.CATNIP_FARM,
                WorldMap.START_ROW, WorldMap.START_COL + 1, 0);
        PlacedBuilding farm = world.getBuildings().get(1);
        assertEquals(KingdomWorld.WORKFORCE_OK, world.assignWorker(farm.id));
        WorkerKitten worker = world.getAssignedWorker(farm.id);
        advanceTime(world, 1f);
        world.setResourceForTest(ResourceType.CATNIP, world.getResourceCap());

        world.endTurn();
        advanceTime(world, 5f);

        assertEquals(4, farm.pendingAmount);
        assertEquals(0, worker.carriedAmount);
        assertEquals(WorkerKitten.WORKING, worker.state);
        assertEquals(KingdomWorld.WORKFORCE_OK, world.unassignWorker(farm.id));
        assertEquals(WorkerKitten.IDLE, worker.state);
    }

    @Test
    public void releasedWorkerReturnsOverflowAndCanBeAssignedAgain() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        world.placeBuildingForTest(BuildingType.CATNIP_FARM,
                WorldMap.START_ROW, WorldMap.START_COL + 1, 0);
        PlacedBuilding farm = world.getBuildings().get(1);
        assertEquals(KingdomWorld.WORKFORCE_OK, world.assignWorker(farm.id));
        WorkerKitten worker = world.getAssignedWorker(farm.id);
        assertEquals(DiplomacySystem.ACTION_OK, world.sendEnvoy(Settlement.RIVERWHISKER));
        world.setResourceForTest(ResourceType.CATNIP, world.getResourceCap() - 2);
        advanceTime(world, 1f);
        world.endTurn();
        advanceUntilCarrying(world, worker);

        assertEquals(KingdomWorld.WORKFORCE_OK, world.unassignWorker(farm.id));
        advanceTime(world, 8f);

        assertEquals(world.getResourceCap(), world.getResource(ResourceType.CATNIP));
        assertEquals(2, farm.pendingAmount);
        assertEquals(0, worker.carriedAmount);
        assertEquals(WorkerKitten.IDLE, worker.state);
        assertEquals(KingdomWorld.WORKFORCE_OK, world.assignWorker(farm.id));
        assertEquals(worker.id, world.getAssignedWorker(farm.id).id);
    }

    @Test
    public void missingDepotNeverRecursesOrTrapsWorker() {
        KingdomWorld original = new KingdomWorld(null);
        original.beginNewKingdom();
        KingdomSaveData save = original.snapshot();
        save.buildings.clear();
        save.workers.clear();
        save.workers.add(new int[]{4, WorldMap.START_ROW - 1, WorldMap.START_COL,
                BuildingType.NONE, ResourceType.CATNIP, 3, BuildingType.NONE, 1});
        save.resources[ResourceType.CATNIP] = 0;

        KingdomWorld restored = new KingdomWorld(null);
        restored.continueKingdom(save);

        assertEquals(1, restored.getIdleWorkerCount());
        assertEquals(3, restored.getResource(ResourceType.CATNIP));
        assertEquals(WorkerKitten.IDLE, restored.getWorkers().get(0).state);
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

    @Test
    public void envoyUsesARealWorkerAndReturnsItAfterTheRoundTrip() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        assertEquals(2, world.getIdleWorkerCount());

        assertEquals(DiplomacySystem.ACTION_OK,
                world.sendEnvoy(Settlement.RIVERWHISKER));
        int envoyId = world.getEnvoyWorkerId(Settlement.RIVERWHISKER);
        assertTrue(envoyId >= 0);
        assertEquals(1, world.getIdleWorkerCount());
        assertEquals(WorkerKitten.DIPLOMACY, workerWithId(world, envoyId).state);

        advanceTurns(world, 5);

        assertEquals(BuildingType.NONE, world.getEnvoyWorkerId(Settlement.RIVERWHISKER));
        assertEquals(2, world.getIdleWorkerCount());
        assertEquals(WorkerKitten.IDLE, workerWithId(world, envoyId).state);
    }

    @Test
    public void diplomacyCannotInventAWorkerWhenEveryKittenIsAway() {
        KingdomWorld world = new KingdomWorld(null);
        world.beginNewKingdom();
        assertEquals(DiplomacySystem.ACTION_OK, world.sendEnvoy(Settlement.RIVERWHISKER));
        assertEquals(DiplomacySystem.ACTION_OK, world.sendEnvoy(Settlement.MOSSBELL));

        assertEquals(DiplomacySystem.ACTION_NEEDS_WORKER,
                world.sendEnvoy(Settlement.CLOVERDOWN));
        assertEquals(0, world.getEnvoyTurns(Settlement.CLOVERDOWN));
    }

    private static WorkerKitten workerWithId(KingdomWorld world, int workerId) {
        for (WorkerKitten worker : world.getWorkers()) {
            if (worker.id == workerId) {
                return worker;
            }
        }
        throw new AssertionError("Missing worker " + workerId);
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

    private static void advanceUntilCarrying(KingdomWorld world, WorkerKitten worker) {
        for (int step = 0; step < 100 && worker.carriedAmount == 0; step++) {
            world.update(0.1f);
        }
        assertTrue("Worker never collected the ready batch", worker.carriedAmount > 0);
    }
}
