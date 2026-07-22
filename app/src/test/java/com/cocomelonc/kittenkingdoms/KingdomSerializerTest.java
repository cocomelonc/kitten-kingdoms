/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public final class KingdomSerializerTest {
    private static final int LEGACY_SETTLEMENT_COUNT = 4;

    @Test
    public void roundTripPreservesAllFields() throws IOException {
        KingdomSaveData original = new KingdomSaveData();
        original.turn = 42;
        original.kittenRow = 10;
        original.kittenCol = 20;
        original.population = 7;
        original.techPointPool = 3;
        original.activeTechId = TechNode.STONE_MASONRY;
        original.techUnlocked = new boolean[TechNode.COUNT];
        original.techUnlocked[TechNode.BASIC_TOOLS] = true;
        original.techUnlocked[TechNode.FISHING_NETS] = true;
        original.resources = new int[]{5, 6, 7, 8, 9, 3};
        original.buildings = new ArrayList<>();
        original.buildings.add(new int[]{0, BuildingType.TOWN_HALL, 48, 48, 0,
                ResourceType.NONE, 0});
        original.buildings.add(new int[]{1, BuildingType.CATNIP_FARM, 48, 49, 1,
                ResourceType.CATNIP, 4});
        original.workers = new ArrayList<>();
        original.workers.add(new int[]{0, 47, 48, 1, ResourceType.NONE, 0,
                BuildingType.NONE, 0});
        original.explored = new boolean[WorldMap.SIZE][WorldMap.SIZE];
        original.explored[48][48] = true;
        original.explored[10][20] = true;
        original.diplomaticRelations = new int[]{31, 42, 53, 64, 25, 35, 45, 55};
        original.envoyTurns = new int[]{0, 1, 2, 0, 0, 0, 1, 0};
        original.courierTurns = new int[]{1, 0, 0, 1, 0, 2, 0, 0};
        original.tradeRoutes = new boolean[]{true, false, true, false,
                false, true, false, true};
        original.envoyWorkerIds = new int[]{-1, 0, 2, -1, -1, -1, 4, -1};
        original.courierWorkerIds = new int[]{1, -1, -1, 3, -1, 5, -1, -1};
        original.totalTrades = 17;
        original.wageDebt = 9;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        KingdomSerializer.write(original, out);
        KingdomSaveData restored = KingdomSerializer.read(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(original.turn, restored.turn);
        assertEquals(original.kittenRow, restored.kittenRow);
        assertEquals(original.kittenCol, restored.kittenCol);
        assertEquals(original.population, restored.population);
        assertEquals(original.techPointPool, restored.techPointPool);
        assertEquals(original.activeTechId, restored.activeTechId);
        assertArrayEquals(original.techUnlocked, restored.techUnlocked);
        assertArrayEquals(original.resources, restored.resources);
        assertEquals(original.buildings.size(), restored.buildings.size());
        for (int i = 0; i < original.buildings.size(); i++) {
            assertArrayEquals(original.buildings.get(i), restored.buildings.get(i));
        }
        assertEquals(original.workers.size(), restored.workers.size());
        assertArrayEquals(original.workers.get(0), restored.workers.get(0));
        for (int row = 0; row < WorldMap.SIZE; row++) {
            assertArrayEquals(original.explored[row], restored.explored[row]);
        }
        assertArrayEquals(original.diplomaticRelations, restored.diplomaticRelations);
        assertArrayEquals(original.envoyTurns, restored.envoyTurns);
        assertArrayEquals(original.courierTurns, restored.courierTurns);
        assertArrayEquals(original.tradeRoutes, restored.tradeRoutes);
        assertArrayEquals(original.envoyWorkerIds, restored.envoyWorkerIds);
        assertArrayEquals(original.courierWorkerIds, restored.courierWorkerIds);
        assertEquals(original.totalTrades, restored.totalTrades);
        assertEquals(original.wageDebt, restored.wageDebt);
    }

    @Test
    public void wrongVersionFailsCleanly() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new DataOutputStream(out).writeInt(999);
        byte[] bytes = out.toByteArray();
        assertThrows(IOException.class, () -> KingdomSerializer.read(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void truncatedStreamFailsCleanlyRatherThanCrashing() {
        byte[] truncated = new byte[]{0, 0, 0, 2};
        assertThrows(IOException.class, () -> KingdomSerializer.read(new ByteArrayInputStream(truncated)));
    }

    @Test
    public void versionTwoSaveMigratesWithFreshDiplomacy() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(2);
        out.writeInt(7);
        out.writeInt(WorldMap.START_ROW);
        out.writeInt(WorldMap.START_COL);
        out.writeInt(2);
        out.writeInt(0);
        out.writeInt(TechNode.NONE);
        out.writeInt(0);
        for (int resource = 0; resource < ResourceType.COUNT; resource++) {
            out.writeInt(0);
        }
        out.writeInt(0);
        out.write(new byte[(WorldMap.SIZE * WorldMap.SIZE + 7) / 8]);

        KingdomSaveData migrated = KingdomSerializer.read(
                new ByteArrayInputStream(bytes.toByteArray()));
        KingdomWorld world = new KingdomWorld(null);
        world.continueKingdom(migrated);

        assertEquals(7, world.getTurn());
        assertEquals(18, world.getRelation(Settlement.RIVERWHISKER));
    }

    @Test
    public void versionThreeSaveMigratesBuildingsAndCreatesWorkforce() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(3);
        out.writeInt(11);
        out.writeInt(WorldMap.START_ROW);
        out.writeInt(WorldMap.START_COL);
        out.writeInt(2);
        out.writeInt(0);
        out.writeInt(TechNode.NONE);
        out.writeInt(0);
        for (int resource = 0; resource < ResourceType.COUNT; resource++) {
            out.writeInt(resource + 1);
        }
        out.writeInt(1);
        out.writeInt(BuildingType.TOWN_HALL);
        out.writeInt(WorldMap.START_ROW);
        out.writeInt(WorldMap.START_COL);
        out.writeInt(0);
        out.write(new byte[(WorldMap.SIZE * WorldMap.SIZE + 7) / 8]);
        out.writeInt(LEGACY_SETTLEMENT_COUNT);
        for (int settlement = 0; settlement < LEGACY_SETTLEMENT_COUNT; settlement++) {
            out.writeInt(20 + settlement);
            out.writeInt(0);
            out.writeInt(0);
            out.writeBoolean(false);
        }

        KingdomSaveData migrated = KingdomSerializer.read(new ByteArrayInputStream(bytes.toByteArray()));
        KingdomWorld world = new KingdomWorld(null);
        world.continueKingdom(migrated);

        assertEquals(11, world.getTurn());
        assertEquals(1, world.getBuildings().size());
        assertEquals(BuildingType.TOWN_HALL, world.getBuildings().get(0).typeId);
        assertEquals(2, world.getWorkerCount());
        assertEquals(20, world.getRelation(Settlement.RIVERWHISKER));
    }

    @Test
    public void versionFourOneWayEnvoyMigratesToAWorkerRoundTrip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(4);
        out.writeInt(9);
        out.writeInt(WorldMap.START_ROW);
        out.writeInt(WorldMap.START_COL);
        out.writeInt(2);
        out.writeInt(0);
        out.writeInt(TechNode.NONE);
        out.writeInt(0);
        for (int resource = 0; resource < ResourceType.COUNT; resource++) {
            out.writeInt(0);
        }
        out.writeInt(1);
        out.writeInt(0);
        out.writeInt(BuildingType.TOWN_HALL);
        out.writeInt(WorldMap.START_ROW);
        out.writeInt(WorldMap.START_COL);
        out.writeInt(0);
        out.writeInt(ResourceType.NONE);
        out.writeInt(0);
        out.write(new byte[(WorldMap.SIZE * WorldMap.SIZE + 7) / 8]);
        out.writeInt(LEGACY_SETTLEMENT_COUNT);
        for (int settlement = 0; settlement < LEGACY_SETTLEMENT_COUNT; settlement++) {
            out.writeInt(settlement == Settlement.RIVERWHISKER ? 18 : 10);
            out.writeInt(settlement == Settlement.RIVERWHISKER ? 2 : 0);
            out.writeInt(0);
            out.writeBoolean(false);
        }
        out.writeInt(2);
        for (int worker = 0; worker < 2; worker++) {
            out.writeInt(worker);
            out.writeInt(WorldMap.START_ROW - 1);
            out.writeInt(WorldMap.START_COL + worker);
            out.writeInt(BuildingType.NONE);
            out.writeInt(ResourceType.NONE);
            out.writeInt(0);
        }

        KingdomSaveData migrated = KingdomSerializer.read(
                new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(5, migrated.envoyTurns[Settlement.RIVERWHISKER]);
        KingdomWorld world = new KingdomWorld(null);
        world.continueKingdom(migrated);
        assertEquals(1, world.getIdleWorkerCount());

        for (int turn = 0; turn < 5; turn++) {
            world.endTurn();
        }
        assertEquals(30, world.getRelation(Settlement.RIVERWHISKER));
        assertEquals(2, world.getIdleWorkerCount());
    }

    @Test
    public void versionFiveStuckCargoIsRecoveredAndWorkerBecomesReusable() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(5);
        out.writeInt(12);
        out.writeInt(WorldMap.START_ROW);
        out.writeInt(WorldMap.START_COL);
        out.writeInt(2);
        out.writeInt(0);
        out.writeInt(TechNode.NONE);
        out.writeInt(0);
        for (int resource = 0; resource < ResourceType.COUNT; resource++) {
            out.writeInt(resource == ResourceType.CATNIP ? 100 : 0);
        }
        out.writeInt(2);
        writeVersionFiveBuilding(out, 0, BuildingType.TOWN_HALL,
                WorldMap.START_ROW, WorldMap.START_COL);
        writeVersionFiveBuilding(out, 1, BuildingType.CATNIP_FARM,
                WorldMap.START_ROW, WorldMap.START_COL + 1);
        out.write(new byte[(WorldMap.SIZE * WorldMap.SIZE + 7) / 8]);
        out.writeInt(LEGACY_SETTLEMENT_COUNT);
        for (int settlement = 0; settlement < LEGACY_SETTLEMENT_COUNT; settlement++) {
            out.writeInt(10);
            out.writeInt(0);
            out.writeInt(0);
            out.writeBoolean(false);
        }
        out.writeInt(1);
        out.writeInt(7);
        out.writeInt(WorldMap.START_ROW - 1);
        out.writeInt(WorldMap.START_COL);
        out.writeInt(BuildingType.NONE);
        out.writeInt(ResourceType.CATNIP);
        out.writeInt(4);
        for (int settlement = 0; settlement < LEGACY_SETTLEMENT_COUNT; settlement++) {
            out.writeInt(BuildingType.NONE);
            out.writeInt(BuildingType.NONE);
        }

        KingdomSaveData migrated = KingdomSerializer.read(
                new ByteArrayInputStream(bytes.toByteArray()));
        KingdomWorld world = new KingdomWorld(null);
        world.continueKingdom(migrated);
        for (int step = 0; step < 20; step++) {
            world.update(0.1f);
        }

        PlacedBuilding farm = world.getBuilding(1);
        assertEquals(4, farm.pendingAmount);
        assertEquals(1, world.getIdleWorkerCount());
        assertEquals(KingdomWorld.WORKFORCE_OK, world.assignWorker(farm.id));
        assertEquals(7, world.getAssignedWorker(farm.id).id);
    }

    @Test
    public void versionSixSaveKeepsFourOldKingdomsAndAddsFourNewDefaults() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(6);
        out.writeInt(20);
        out.writeInt(WorldMap.START_ROW);
        out.writeInt(WorldMap.START_COL);
        out.writeInt(2);
        out.writeInt(0);
        out.writeInt(TechNode.NONE);
        out.writeInt(0);
        for (int resource = 0; resource < ResourceType.COUNT; resource++) {
            out.writeInt(0);
        }
        out.writeInt(1);
        writeVersionFiveBuilding(out, 0, BuildingType.TOWN_HALL,
                WorldMap.START_ROW, WorldMap.START_COL);
        out.write(new byte[(WorldMap.SIZE * WorldMap.SIZE + 7) / 8]);
        out.writeInt(LEGACY_SETTLEMENT_COUNT);
        for (int settlement = 0; settlement < LEGACY_SETTLEMENT_COUNT; settlement++) {
            out.writeInt(40 + settlement);
            out.writeInt(0);
            out.writeInt(0);
            out.writeBoolean(settlement == Settlement.RIVERWHISKER);
        }
        out.writeInt(1);
        out.writeInt(3);
        out.writeInt(WorldMap.START_ROW - 1);
        out.writeInt(WorldMap.START_COL);
        out.writeInt(BuildingType.NONE);
        out.writeInt(ResourceType.NONE);
        out.writeInt(0);
        out.writeInt(BuildingType.NONE);
        out.writeInt(0);
        for (int settlement = 0; settlement < LEGACY_SETTLEMENT_COUNT; settlement++) {
            out.writeInt(BuildingType.NONE);
            out.writeInt(BuildingType.NONE);
        }

        KingdomSaveData migrated = KingdomSerializer.read(
                new ByteArrayInputStream(bytes.toByteArray()));
        KingdomWorld world = new KingdomWorld(null);
        world.continueKingdom(migrated);

        assertEquals(40, world.getRelation(Settlement.RIVERWHISKER));
        assertEquals(16, world.getRelation(Settlement.PEBBLEBROOK));
        assertTrue(world.hasTradeRoute(Settlement.RIVERWHISKER));
        assertEquals(0, world.getTotalTrades());
        assertEquals(1, world.getIdleWorkerCount());
    }

    private static void writeVersionFiveBuilding(DataOutputStream out, int id, int type,
            int row, int col) throws IOException {
        out.writeInt(id);
        out.writeInt(type);
        out.writeInt(row);
        out.writeInt(col);
        out.writeInt(0);
        out.writeInt(ResourceType.NONE);
        out.writeInt(0);
    }
}
