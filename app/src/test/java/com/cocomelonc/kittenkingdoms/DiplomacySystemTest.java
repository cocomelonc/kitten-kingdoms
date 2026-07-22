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

public final class DiplomacySystemTest {
    @Test
    public void envoyVisitsAndReturnsAfterAVisibleRoundTrip() {
        DiplomacySystem diplomacy = new DiplomacySystem();
        int[] resources = new int[ResourceType.COUNT];

        assertEquals(DiplomacySystem.ACTION_OK,
                diplomacy.sendEnvoy(Settlement.RIVERWHISKER));
        diplomacy.advanceTurn(resources, 100);
        assertEquals(4, diplomacy.getEnvoyTurns(Settlement.RIVERWHISKER));
        assertEquals(DiplomacySystem.MISSION_OUTBOUND,
                diplomacy.getEnvoyPhase(Settlement.RIVERWHISKER));
        assertEquals(18, diplomacy.getRelation(Settlement.RIVERWHISKER));

        DiplomacySystem.TurnReport report = diplomacy.advanceTurn(resources, 100);
        assertEquals(3, diplomacy.getEnvoyTurns(Settlement.RIVERWHISKER));
        assertEquals(30, diplomacy.getRelation(Settlement.RIVERWHISKER));
        assertTrue((report.events[Settlement.RIVERWHISKER]
                & DiplomacySystem.EVENT_ENVOY_ARRIVED) != 0);

        diplomacy.advanceTurn(resources, 100);
        diplomacy.advanceTurn(resources, 100);
        report = diplomacy.advanceTurn(resources, 100);
        assertEquals(0, diplomacy.getEnvoyTurns(Settlement.RIVERWHISKER));
        assertTrue((report.events[Settlement.RIVERWHISKER]
                & DiplomacySystem.EVENT_ENVOY_RETURNED) != 0);
    }

    @Test
    public void giftConsumesTheRequestedGoodAndImprovesRelations() {
        DiplomacySystem diplomacy = new DiplomacySystem();
        int[] resources = new int[ResourceType.COUNT];
        resources[ResourceType.STONE] = 9;

        assertEquals(DiplomacySystem.ACTION_OK, diplomacy.giveGift(Settlement.MOSSBELL, resources));

        assertEquals(4, resources[ResourceType.STONE]);
        assertEquals(22, diplomacy.getRelation(Settlement.MOSSBELL));
    }

    @Test
    public void friendlySettlementCanRunARealResourceExchange() {
        DiplomacySystem diplomacy = new DiplomacySystem();
        int[] resources = new int[ResourceType.COUNT];
        resources[ResourceType.WOOD] = 50;
        resources[ResourceType.STONE] = 50;

        diplomacy.sendEnvoy(Settlement.RIVERWHISKER);
        diplomacy.advanceTurn(resources, 100);
        diplomacy.advanceTurn(resources, 100);
        diplomacy.sendCourier(Settlement.RIVERWHISKER);
        diplomacy.advanceTurn(resources, 100);
        assertTrue(diplomacy.getRelation(Settlement.RIVERWHISKER)
                >= DiplomacySystem.TRADE_RELATION_REQUIRED);

        assertEquals(DiplomacySystem.ACTION_OK,
                diplomacy.establishTradeRoute(Settlement.RIVERWHISKER, resources));
        assertTrue(diplomacy.hasTradeRoute(Settlement.RIVERWHISKER));
        int woodBeforeCaravan = resources[ResourceType.WOOD];

        DiplomacySystem.TurnReport report = diplomacy.advanceTurn(resources, 100);

        assertEquals(woodBeforeCaravan - DiplomacySystem.TRADE_EXPORT_AMOUNT,
                resources[ResourceType.WOOD]);
        assertEquals(DiplomacySystem.TRADE_IMPORT_AMOUNT, resources[ResourceType.FISH]);
        assertTrue((report.events[Settlement.RIVERWHISKER]
                & DiplomacySystem.EVENT_TRADE_COMPLETED) != 0);
    }

    @Test
    public void tradeRouteCannotOpenBeforeTrustThreshold() {
        DiplomacySystem diplomacy = new DiplomacySystem();
        int[] resources = new int[ResourceType.COUNT];
        resources[ResourceType.WOOD] = 50;
        resources[ResourceType.STONE] = 50;

        assertEquals(DiplomacySystem.ACTION_NEEDS_RELATION,
                diplomacy.establishTradeRoute(Settlement.STARFALL, resources));
        assertFalse(diplomacy.hasTradeRoute(Settlement.STARFALL));
    }
}
