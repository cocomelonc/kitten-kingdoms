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
    private static final int SAVE_VERSION = 3;
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
            out.writeInt(building[0]);
            out.writeInt(building[1]);
            out.writeInt(building[2]);
            out.writeInt(building[3]);
        }
        writeExploredBits(out, data.explored);
        writeDiplomacy(out, data);
        out.flush();
    }

    static KingdomSaveData read(InputStream rawIn) throws IOException {
        DataInputStream in = new DataInputStream(rawIn);
        int version = in.readInt();
        if (version != SAVE_VERSION && version != LEGACY_SAVE_VERSION) {
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
            data.buildings.add(new int[]{in.readInt(), in.readInt(), in.readInt(), in.readInt()});
        }
        data.explored = readExploredBits(in);
        if (version >= 3) {
            readDiplomacy(in, data);
        }
        return data;
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

    private static void readDiplomacy(DataInputStream in, KingdomSaveData data) throws IOException {
        int count = in.readInt();
        if (count != Settlement.COUNT) {
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
    }

    private static int valueAt(int[] values, int index) {
        return index < values.length ? values[index] : 0;
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
