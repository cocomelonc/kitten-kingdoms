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

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public final class KingdomSerializerTest {
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
        original.buildings.add(new int[]{BuildingType.TOWN_HALL, 48, 48, 0});
        original.buildings.add(new int[]{BuildingType.CATNIP_FARM, 48, 49, 1});
        original.explored = new boolean[WorldMap.SIZE][WorldMap.SIZE];
        original.explored[48][48] = true;
        original.explored[10][20] = true;
        original.diplomaticRelations = new int[]{31, 42, 53, 64};
        original.envoyTurns = new int[]{0, 1, 2, 0};
        original.courierTurns = new int[]{1, 0, 0, 1};
        original.tradeRoutes = new boolean[]{true, false, true, false};

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
        for (int row = 0; row < WorldMap.SIZE; row++) {
            assertArrayEquals(original.explored[row], restored.explored[row]);
        }
        assertArrayEquals(original.diplomaticRelations, restored.diplomaticRelations);
        assertArrayEquals(original.envoyTurns, restored.envoyTurns);
        assertArrayEquals(original.courierTurns, restored.courierTurns);
        assertArrayEquals(original.tradeRoutes, restored.tradeRoutes);
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
}
