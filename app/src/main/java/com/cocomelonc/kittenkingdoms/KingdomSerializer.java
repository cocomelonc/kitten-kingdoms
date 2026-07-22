/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Zero-dependency versioned binary save format via plain {@code java.io} streams.
 * Any read failure (bad version, truncated stream, implausible counts) throws {@link IOException}
 * so the caller can fall back to a fresh kingdom rather than crash.
 */
final class KingdomSerializer {
    private static final int SAVE_VERSION = 7;
    private static final int LOGISTICS_SAVE_VERSION = 6;
    private static final int DIPLOMATIC_WORKER_SAVE_VERSION = 5;
    private static final int WORKER_SAVE_VERSION = 4;
    private static final int DIPLOMACY_SAVE_VERSION = 3;
    private static final int LEGACY_SAVE_VERSION = 2;
    private static final int MAX_PLAUSIBLE_BUILDINGS = 100_000;

    private KingdomSerializer() {
    }

    static void write(KingdomSaveData data, OutputStream rawOut) throws IOException {
        DataOutputStream out = new DataOutputStream(rawOut);
        out.writeInt(SAVE_VERSION);
        out.writeInt(data.turn);
        out.writeInt(data.kittenRow);
        out.writeInt(data.kittenCol);
        out.writeInt(data.population);
        out.writeInt(data.techPointPool);
        out.writeInt(data.activeTechId);
        out.writeInt(packTechBits(data.techUnlocked));
        for (int amount : data.resources) {
            out.writeInt(amount);
        }
        out.writeInt(data.buildings.size());
        for (int[] building : data.buildings) {
            for (int field = 0; field < 7; field++) {
                out.writeInt(valueAt(building, field));
            }
        }
        writeExploredBits(out, data.explored);
        writeDiplomacy(out, data);
        writeWorkers(out, data);
        writeDiplomaticWorkers(out, data);
        out.writeInt(Math.max(0, data.totalTrades));
        out.flush();
    }

    static KingdomSaveData read(InputStream rawIn) throws IOException {
        DataInputStream in = new DataInputStream(rawIn);
        int version = in.readInt();
        if (version != SAVE_VERSION && version != LOGISTICS_SAVE_VERSION
                && version != DIPLOMATIC_WORKER_SAVE_VERSION
                && version != WORKER_SAVE_VERSION
                && version != DIPLOMACY_SAVE_VERSION && version != LEGACY_SAVE_VERSION) {
            throw new IOException("Unsupported save version: " + version);
        }
        KingdomSaveData data = new KingdomSaveData();
        data.turn = in.readInt();
        data.kittenRow = in.readInt();
        data.kittenCol = in.readInt();
        data.population = in.readInt();
        data.techPointPool = in.readInt();
        data.activeTechId = in.readInt();
        data.techUnlocked = unpackTechBits(in.readInt());
        data.resources = new int[ResourceType.COUNT];
        for (int i = 0; i < ResourceType.COUNT; i++) {
            data.resources[i] = in.readInt();
        }
        int buildingCount = in.readInt();
        if (buildingCount < 0 || buildingCount > MAX_PLAUSIBLE_BUILDINGS) {
            throw new IOException("Implausible building count: " + buildingCount);
        }
        data.buildings = new ArrayList<>(buildingCount);
        for (int i = 0; i < buildingCount; i++) {
            if (version >= WORKER_SAVE_VERSION) {
                data.buildings.add(new int[]{in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                        in.readInt(), in.readInt(), in.readInt()});
            } else {
                int typeId = in.readInt();
                int row = in.readInt();
                int col = in.readInt();
                int turnsRemaining = in.readInt();
                data.buildings.add(new int[]{i, typeId, row, col, turnsRemaining,
                        ResourceType.NONE, 0});
            }
        }
        data.explored = readExploredBits(in);
        int savedSettlementCount = 0;
        if (version >= DIPLOMACY_SAVE_VERSION) {
            savedSettlementCount = readDiplomacy(in, data);
            if (version < DIPLOMATIC_WORKER_SAVE_VERSION) {
                migrateLegacyDiplomaticTrips(data);
            }
        }
        if (version >= WORKER_SAVE_VERSION) {
            readWorkers(in, data, version);
        }
        if (version >= DIPLOMATIC_WORKER_SAVE_VERSION) {
            readDiplomaticWorkers(in, data, savedSettlementCount);
        }
        if (version >= SAVE_VERSION) {
            data.totalTrades = Math.max(0, in.readInt());
        }
        return data;
    }

    /** Old saves tracked one-way arrival only; preserve that ETA and append the new return leg. */
    private static void migrateLegacyDiplomaticTrips(KingdomSaveData data) {
        int count = Math.min(data.envoyTurns.length, data.courierTurns.length);
        for (int settlement = 0; settlement < count; settlement++) {
            if (data.envoyTurns[settlement] > 0) {
                data.envoyTurns[settlement] += 3;
            }
            if (data.courierTurns[settlement] > 0) {
                data.courierTurns[settlement] += 2;
            }
        }
    }

    private static void writeDiplomaticWorkers(DataOutputStream out, KingdomSaveData data)
            throws IOException {
        for (int settlement = 0; settlement < Settlement.COUNT; settlement++) {
            out.writeInt(valueAtOr(data.envoyWorkerIds, settlement, BuildingType.NONE));
            out.writeInt(valueAtOr(data.courierWorkerIds, settlement, BuildingType.NONE));
        }
    }

    private static void readDiplomaticWorkers(DataInputStream in, KingdomSaveData data,
            int settlementCount)
            throws IOException {
        data.envoyWorkerIds = new int[settlementCount];
        data.courierWorkerIds = new int[settlementCount];
        for (int settlement = 0; settlement < settlementCount; settlement++) {
            data.envoyWorkerIds[settlement] = in.readInt();
            data.courierWorkerIds[settlement] = in.readInt();
        }
    }

    private static void writeWorkers(DataOutputStream out, KingdomSaveData data) throws IOException {
        int count = data.workers == null ? 0 : data.workers.size();
        out.writeInt(count);
        if (data.workers == null) {
            return;
        }
        for (int[] worker : data.workers) {
            for (int field = 0; field < 6; field++) {
                out.writeInt(valueAt(worker, field));
            }
            out.writeInt(valueAtOr(worker, 6, BuildingType.NONE));
            out.writeInt(valueAt(worker, 7));
        }
    }

    private static void readWorkers(DataInputStream in, KingdomSaveData data, int version)
            throws IOException {
        int count = in.readInt();
        if (count < 0 || count > WorldMap.SIZE * WorldMap.SIZE) {
            throw new IOException("Implausible worker count: " + count);
        }
        data.workers = new ArrayList<>(count);
        int fields = version >= LOGISTICS_SAVE_VERSION ? 8 : 6;
        for (int i = 0; i < count; i++) {
            int[] worker = new int[fields];
            for (int field = 0; field < fields; field++) {
                worker[field] = in.readInt();
            }
            data.workers.add(worker);
        }
    }

    private static void writeDiplomacy(DataOutputStream out, KingdomSaveData data) throws IOException {
        DiplomacySystem defaults = new DiplomacySystem();
        int[] relations = data.diplomaticRelations == null
                ? defaults.relationsSnapshot() : data.diplomaticRelations;
        int[] envoys = data.envoyTurns == null
                ? defaults.envoyTurnsSnapshot() : data.envoyTurns;
        int[] couriers = data.courierTurns == null
                ? defaults.courierTurnsSnapshot() : data.courierTurns;
        boolean[] routes = data.tradeRoutes == null
                ? defaults.tradeRoutesSnapshot() : data.tradeRoutes;
        out.writeInt(Settlement.COUNT);
        for (int id = 0; id < Settlement.COUNT; id++) {
            out.writeInt(valueAt(relations, id));
            out.writeInt(valueAt(envoys, id));
            out.writeInt(valueAt(couriers, id));
            out.writeBoolean(id < routes.length && routes[id]);
        }
    }

    private static int readDiplomacy(DataInputStream in, KingdomSaveData data) throws IOException {
        int count = in.readInt();
        if (count <= 0 || count > Settlement.COUNT) {
            throw new IOException("Unsupported settlement count: " + count);
        }
        data.diplomaticRelations = new int[count];
        data.envoyTurns = new int[count];
        data.courierTurns = new int[count];
        data.tradeRoutes = new boolean[count];
        for (int id = 0; id < count; id++) {
            data.diplomaticRelations[id] = in.readInt();
            data.envoyTurns[id] = in.readInt();
            data.courierTurns[id] = in.readInt();
            data.tradeRoutes[id] = in.readBoolean();
        }
        return count;
    }

    private static int valueAt(int[] values, int index) {
        return index < values.length ? values[index] : 0;
    }

    private static int valueAtOr(int[] values, int index, int fallback) {
        return values != null && index < values.length ? values[index] : fallback;
    }

    static int packTechBits(boolean[] techUnlocked) {
        int bits = 0;
        for (int i = 0; i < techUnlocked.length; i++) {
            if (techUnlocked[i]) {
                bits |= (1 << i);
            }
        }
        return bits;
    }

    static boolean[] unpackTechBits(int bits) {
        boolean[] techUnlocked = new boolean[TechNode.COUNT];
        for (int i = 0; i < TechNode.COUNT; i++) {
            techUnlocked[i] = (bits & (1 << i)) != 0;
        }
        return techUnlocked;
    }

    private static void writeExploredBits(DataOutputStream out, boolean[][] explored) throws IOException {
        byte[] packed = new byte[packedExploredByteCount()];
        int bitIndex = 0;
        for (int row = 0; row < WorldMap.SIZE; row++) {
            for (int col = 0; col < WorldMap.SIZE; col++) {
                if (explored[row][col]) {
                    packed[bitIndex / 8] |= (byte) (1 << (bitIndex % 8));
                }
                bitIndex++;
            }
        }
        out.write(packed);
    }

    private static boolean[][] readExploredBits(DataInputStream in) throws IOException {
        byte[] packed = new byte[packedExploredByteCount()];
        in.readFully(packed);
        boolean[][] explored = new boolean[WorldMap.SIZE][WorldMap.SIZE];
        int bitIndex = 0;
        for (int row = 0; row < WorldMap.SIZE; row++) {
            for (int col = 0; col < WorldMap.SIZE; col++) {
                explored[row][col] = (packed[bitIndex / 8] & (1 << (bitIndex % 8))) != 0;
                bitIndex++;
            }
        }
        return explored;
    }

    private static int packedExploredByteCount() {
        int totalBits = WorldMap.SIZE * WorldMap.SIZE;
        return (totalBits + 7) / 8;
    }
}
