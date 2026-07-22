/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import java.util.Arrays;

/** Pure turn-based diplomacy rules, including outbound, visiting, and return phases. */
final class DiplomacySystem {
    static final int ACTION_OK = 0;
    static final int ACTION_INVALID = 1;
    static final int ACTION_ALREADY_TRAVELLING = 2;
    static final int ACTION_NEEDS_RELATION = 3;
    static final int ACTION_NEEDS_RESOURCES = 4;
    static final int ACTION_ROUTE_EXISTS = 5;
    static final int ACTION_NEEDS_WORKER = 6;
    static final int ACTION_STORAGE_FULL = 7;
    static final int ACTION_ROUTE_REQUIRED = 8;

    static final int EVENT_ENVOY_ARRIVED = 1;
    static final int EVENT_COURIER_ARRIVED = 1 << 1;
    static final int EVENT_ENVOY_RETURNED = 1 << 3;
    static final int EVENT_COURIER_RETURNED = 1 << 4;

    static final int MISSION_NONE = 0;
    static final int MISSION_OUTBOUND = 1;
    static final int MISSION_VISITING = 2;
    static final int MISSION_RETURNING = 3;

    static final int GIFT_AMOUNT = 5;
    static final int TRADE_RELATION_REQUIRED = 35;
    static final int TRADE_WOOD_COST = 8;
    static final int TRADE_STONE_COST = 5;
    private static final int ENVOY_TOTAL_TURNS = 5;
    private static final int ENVOY_ARRIVAL_REMAINING = 3;
    private static final int COURIER_TOTAL_TURNS = 3;
    private static final int COURIER_ARRIVAL_REMAINING = 2;
    private static final int COURIER_RELATION_REQUIRED = 15;
    private static final int[] STARTING_RELATIONS = {18, 12, 22, 8, 16, 10, 20, 14};

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
        envoyTurns[settlementId] = ENVOY_TOTAL_TURNS;
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
        courierTurns[settlementId] = COURIER_TOTAL_TURNS;
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

    int checkTrade(int settlementId, int offerIndex, int[] resources, int resourceCap) {
        if (!isValid(settlementId)) {
            return ACTION_INVALID;
        }
        if (!tradeRoutes[settlementId]) {
            return ACTION_ROUTE_REQUIRED;
        }
        MarketOffer[] offers = settlements[settlementId].marketOffers;
        if (offerIndex < 0 || offerIndex >= offers.length) {
            return ACTION_INVALID;
        }
        MarketOffer offer = offers[offerIndex];
        if (resources[offer.giveResourceId] < offer.giveAmount) {
            return ACTION_NEEDS_RESOURCES;
        }
        if (resources[offer.receiveResourceId] > resourceCap - offer.receiveAmount) {
            return ACTION_STORAGE_FULL;
        }
        return ACTION_OK;
    }

    int trade(int settlementId, int offerIndex, int[] resources, int resourceCap) {
        int result = checkTrade(settlementId, offerIndex, resources, resourceCap);
        if (result != ACTION_OK) {
            return result;
        }
        MarketOffer offer = settlements[settlementId].marketOffers[offerIndex];
        resources[offer.giveResourceId] -= offer.giveAmount;
        resources[offer.receiveResourceId] += offer.receiveAmount;
        relations[settlementId] = clampRelation(relations[settlementId] + 1);
        return ACTION_OK;
    }

    TurnReport advanceTurn() {
        TurnReport report = new TurnReport();
        for (Settlement settlement : settlements) {
            int id = settlement.id;
            if (envoyTurns[id] > 0) {
                envoyTurns[id]--;
                if (envoyTurns[id] == ENVOY_ARRIVAL_REMAINING) {
                    relations[id] = clampRelation(relations[id] + 12);
                    report.events[id] |= EVENT_ENVOY_ARRIVED;
                } else if (envoyTurns[id] == 0) {
                    report.events[id] |= EVENT_ENVOY_RETURNED;
                }
            }
            if (courierTurns[id] > 0) {
                courierTurns[id]--;
                if (courierTurns[id] == COURIER_ARRIVAL_REMAINING) {
                    relations[id] = clampRelation(relations[id] + 6);
                    report.events[id] |= EVENT_COURIER_ARRIVED;
                } else if (courierTurns[id] == 0) {
                    report.events[id] |= EVENT_COURIER_RETURNED;
                }
            }
        }
        return report;
    }

    int getEnvoyPhase(int settlementId) {
        return phase(envoyTurns[settlementId], ENVOY_ARRIVAL_REMAINING);
    }

    int getCourierPhase(int settlementId) {
        return phase(courierTurns[settlementId], COURIER_ARRIVAL_REMAINING);
    }

    float getEnvoyRouteProgress(int settlementId) {
        return routeProgress(envoyTurns[settlementId], ENVOY_TOTAL_TURNS,
                ENVOY_ARRIVAL_REMAINING);
    }

    float getCourierRouteProgress(int settlementId) {
        return routeProgress(courierTurns[settlementId], COURIER_TOTAL_TURNS,
                COURIER_ARRIVAL_REMAINING);
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

    int getTradeRouteCount() {
        int count = 0;
        for (boolean route : tradeRoutes) {
            if (route) {
                count++;
            }
        }
        return count;
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

    void cancelEnvoy(int settlementId) {
        if (isValid(settlementId)) {
            envoyTurns[settlementId] = 0;
        }
    }

    void cancelCourier(int settlementId) {
        if (isValid(settlementId)) {
            courierTurns[settlementId] = 0;
        }
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
                envoyTurns[id] = Math.max(0, Math.min(ENVOY_TOTAL_TURNS, restoredEnvoys[id]));
            }
            if (id < restoredCouriers.length) {
                courierTurns[id] = Math.max(0, Math.min(COURIER_TOTAL_TURNS, restoredCouriers[id]));
            }
            if (id < restoredRoutes.length) {
                tradeRoutes[id] = restoredRoutes[id];
            }
        }
    }

    private static int phase(int remaining, int arrivalRemaining) {
        if (remaining <= 0) {
            return MISSION_NONE;
        }
        if (remaining > arrivalRemaining) {
            return MISSION_OUTBOUND;
        }
        if (remaining == arrivalRemaining) {
            return MISSION_VISITING;
        }
        return MISSION_RETURNING;
    }

    private static float routeProgress(int remaining, int total, int arrivalRemaining) {
        int phase = phase(remaining, arrivalRemaining);
        if (phase == MISSION_NONE) {
            return 0f;
        }
        if (phase == MISSION_VISITING) {
            return 1f;
        }
        if (phase == MISSION_OUTBOUND) {
            return Math.max(0.08f, (total - remaining) / (float) (total - arrivalRemaining));
        }
        return remaining / (float) arrivalRemaining;
    }

    private static int clampRelation(int relation) {
        return Math.max(0, Math.min(100, relation));
    }

    private static boolean isValid(int settlementId) {
        return settlementId >= 0 && settlementId < Settlement.COUNT;
    }

    static final class TurnReport {
        final int[] events = new int[Settlement.COUNT];
    }
}
