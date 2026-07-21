/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import java.util.Arrays;

/**
 * Pure turn-based diplomacy rules. The view only presents actions; costs, travel times,
 * relationship thresholds, and trade exchanges live here and are independently testable.
 */
final class DiplomacySystem {
    static final int ACTION_OK = 0;
    static final int ACTION_INVALID = 1;
    static final int ACTION_ALREADY_TRAVELLING = 2;
    static final int ACTION_NEEDS_RELATION = 3;
    static final int ACTION_NEEDS_RESOURCES = 4;
    static final int ACTION_ROUTE_EXISTS = 5;

    static final int EVENT_ENVOY_ARRIVED = 1;
    static final int EVENT_COURIER_ARRIVED = 1 << 1;
    static final int EVENT_TRADE_COMPLETED = 1 << 2;

    static final int GIFT_AMOUNT = 5;
    static final int TRADE_RELATION_REQUIRED = 35;
    static final int TRADE_WOOD_COST = 8;
    static final int TRADE_STONE_COST = 5;
    static final int TRADE_EXPORT_AMOUNT = 2;
    static final int TRADE_IMPORT_AMOUNT = 3;

    private static final int ENVOY_TRAVEL_TURNS = 2;
    private static final int COURIER_TRAVEL_TURNS = 1;
    private static final int COURIER_RELATION_REQUIRED = 15;
    private static final int[] STARTING_RELATIONS = {18, 12, 22, 8};

    private final Settlement[] settlements = Settlement.createAll();
    private final int[] relations = new int[Settlement.COUNT];
    private final int[] envoyTurns = new int[Settlement.COUNT];
    private final int[] courierTurns = new int[Settlement.COUNT];
    private final boolean[] tradeRoutes = new boolean[Settlement.COUNT];

    DiplomacySystem() {
        reset();
    }

    void reset() {
        System.arraycopy(STARTING_RELATIONS, 0, relations, 0, Settlement.COUNT);
        Arrays.fill(envoyTurns, 0);
        Arrays.fill(courierTurns, 0);
        Arrays.fill(tradeRoutes, false);
    }

    int sendEnvoy(int settlementId) {
        if (!isValid(settlementId)) {
            return ACTION_INVALID;
        }
        if (envoyTurns[settlementId] > 0) {
            return ACTION_ALREADY_TRAVELLING;
        }
        envoyTurns[settlementId] = ENVOY_TRAVEL_TURNS;
        return ACTION_OK;
    }

    int sendCourier(int settlementId) {
        if (!isValid(settlementId)) {
            return ACTION_INVALID;
        }
        if (courierTurns[settlementId] > 0) {
            return ACTION_ALREADY_TRAVELLING;
        }
        if (relations[settlementId] < COURIER_RELATION_REQUIRED) {
            return ACTION_NEEDS_RELATION;
        }
        courierTurns[settlementId] = COURIER_TRAVEL_TURNS;
        return ACTION_OK;
    }

    int giveGift(int settlementId, int[] resources) {
        if (!isValid(settlementId)) {
            return ACTION_INVALID;
        }
        int requested = settlements[settlementId].requestedResource;
        if (resources[requested] < GIFT_AMOUNT) {
            return ACTION_NEEDS_RESOURCES;
        }
        resources[requested] -= GIFT_AMOUNT;
        relations[settlementId] = clampRelation(relations[settlementId] + 10);
        return ACTION_OK;
    }

    int establishTradeRoute(int settlementId, int[] resources) {
        if (!isValid(settlementId)) {
            return ACTION_INVALID;
        }
        if (tradeRoutes[settlementId]) {
            return ACTION_ROUTE_EXISTS;
        }
        if (relations[settlementId] < TRADE_RELATION_REQUIRED) {
            return ACTION_NEEDS_RELATION;
        }
        if (resources[ResourceType.WOOD] < TRADE_WOOD_COST
                || resources[ResourceType.STONE] < TRADE_STONE_COST) {
            return ACTION_NEEDS_RESOURCES;
        }
        resources[ResourceType.WOOD] -= TRADE_WOOD_COST;
        resources[ResourceType.STONE] -= TRADE_STONE_COST;
        tradeRoutes[settlementId] = true;
        return ACTION_OK;
    }

    TurnReport advanceTurn(int[] resources, int resourceCap) {
        TurnReport report = new TurnReport();
        for (Settlement settlement : settlements) {
            int id = settlement.id;
            if (envoyTurns[id] > 0 && --envoyTurns[id] == 0) {
                relations[id] = clampRelation(relations[id] + 12);
                report.events[id] |= EVENT_ENVOY_ARRIVED;
            }
            if (courierTurns[id] > 0 && --courierTurns[id] == 0) {
                relations[id] = clampRelation(relations[id] + 6);
                report.events[id] |= EVENT_COURIER_ARRIVED;
            }
            if (!tradeRoutes[id]) {
                continue;
            }
            int exported = settlement.requestedResource;
            int imported = settlement.offeredResource;
            if (resources[exported] < TRADE_EXPORT_AMOUNT || resources[imported] >= resourceCap) {
                continue;
            }
            int received = Math.min(TRADE_IMPORT_AMOUNT, resourceCap - resources[imported]);
            resources[exported] -= TRADE_EXPORT_AMOUNT;
            resources[imported] += received;
            report.resourceDelta[exported] -= TRADE_EXPORT_AMOUNT;
            report.resourceDelta[imported] += received;
            report.events[id] |= EVENT_TRADE_COMPLETED;
            relations[id] = clampRelation(relations[id] + 1);
        }
        return report;
    }

    Settlement[] getSettlements() {
        return settlements.clone();
    }

    int getRelation(int settlementId) {
        return relations[settlementId];
    }

    int getEnvoyTurns(int settlementId) {
        return envoyTurns[settlementId];
    }

    int getCourierTurns(int settlementId) {
        return courierTurns[settlementId];
    }

    boolean hasTradeRoute(int settlementId) {
        return tradeRoutes[settlementId];
    }

    int[] relationsSnapshot() {
        return relations.clone();
    }

    int[] envoyTurnsSnapshot() {
        return envoyTurns.clone();
    }

    int[] courierTurnsSnapshot() {
        return courierTurns.clone();
    }

    boolean[] tradeRoutesSnapshot() {
        return tradeRoutes.clone();
    }

    void restore(int[] restoredRelations, int[] restoredEnvoys,
            int[] restoredCouriers, boolean[] restoredRoutes) {
        reset();
        if (restoredRelations == null || restoredEnvoys == null
                || restoredCouriers == null || restoredRoutes == null) {
            return;
        }
        for (int id = 0; id < Settlement.COUNT; id++) {
            if (id < restoredRelations.length) {
                relations[id] = clampRelation(restoredRelations[id]);
            }
            if (id < restoredEnvoys.length) {
                envoyTurns[id] = Math.max(0, restoredEnvoys[id]);
            }
            if (id < restoredCouriers.length) {
                courierTurns[id] = Math.max(0, restoredCouriers[id]);
            }
            if (id < restoredRoutes.length) {
                tradeRoutes[id] = restoredRoutes[id];
            }
        }
    }

    private static int clampRelation(int relation) {
        return Math.max(0, Math.min(100, relation));
    }

    private static boolean isValid(int settlementId) {
        return settlementId >= 0 && settlementId < Settlement.COUNT;
    }

    static final class TurnReport {
        final int[] events = new int[Settlement.COUNT];
        final int[] resourceDelta = new int[ResourceType.COUNT];
    }
}
