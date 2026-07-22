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

        assertEquals(DiplomacySystem.ACTION_OK,
                diplomacy.sendEnvoy(Settlement.RIVERWHISKER));
        diplomacy.advanceTurn();
        assertEquals(4, diplomacy.getEnvoyTurns(Settlement.RIVERWHISKER));
        assertEquals(DiplomacySystem.MISSION_OUTBOUND,
                diplomacy.getEnvoyPhase(Settlement.RIVERWHISKER));
        assertEquals(18, diplomacy.getRelation(Settlement.RIVERWHISKER));

        DiplomacySystem.TurnReport report = diplomacy.advanceTurn();
        assertEquals(3, diplomacy.getEnvoyTurns(Settlement.RIVERWHISKER));
        assertEquals(30, diplomacy.getRelation(Settlement.RIVERWHISKER));
        assertTrue((report.events[Settlement.RIVERWHISKER]
                & DiplomacySystem.EVENT_ENVOY_ARRIVED) != 0);

        diplomacy.advanceTurn();
        diplomacy.advanceTurn();
        report = diplomacy.advanceTurn();
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
    public void friendlySettlementUnlocksManualResourceExchange() {
        DiplomacySystem diplomacy = new DiplomacySystem();
        int[] resources = new int[ResourceType.COUNT];
        resources[ResourceType.WOOD] = 50;
        resources[ResourceType.STONE] = 50;

        diplomacy.sendEnvoy(Settlement.RIVERWHISKER);
        diplomacy.advanceTurn();
        diplomacy.advanceTurn();
        diplomacy.sendCourier(Settlement.RIVERWHISKER);
        diplomacy.advanceTurn();
        assertTrue(diplomacy.getRelation(Settlement.RIVERWHISKER)
                >= DiplomacySystem.TRADE_RELATION_REQUIRED);

        assertEquals(DiplomacySystem.ACTION_OK,
                diplomacy.establishTradeRoute(Settlement.RIVERWHISKER, resources));
        assertTrue(diplomacy.hasTradeRoute(Settlement.RIVERWHISKER));
        MarketOffer offer = diplomacy.getSettlements()[Settlement.RIVERWHISKER].marketOffers[0];
        int woodBeforeTrade = resources[ResourceType.WOOD];
        int crystalsBeforeTrade = resources[ResourceType.CRYSTALS];

        diplomacy.advanceTurn();
        assertEquals(woodBeforeTrade, resources[ResourceType.WOOD]);
        assertEquals(DiplomacySystem.ACTION_OK,
                diplomacy.trade(Settlement.RIVERWHISKER, 0, resources, 100));

        assertEquals(woodBeforeTrade - offer.giveAmount,
                resources[ResourceType.WOOD]);
        assertEquals(crystalsBeforeTrade + offer.receiveAmount,
                resources[ResourceType.CRYSTALS]);
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

    @Test
    public void marketRequiresRouteResourcesAndIncomingStorageRoom() {
        DiplomacySystem diplomacy = new DiplomacySystem();
        int[] resources = new int[ResourceType.COUNT];
        resources[ResourceType.WOOD] = 50;
        resources[ResourceType.STONE] = 50;

        assertEquals(DiplomacySystem.ACTION_ROUTE_REQUIRED,
                diplomacy.trade(Settlement.RIVERWHISKER, 0, resources, 100));
        diplomacy.restore(new int[]{100}, new int[]{0}, new int[]{0}, new boolean[]{true});
        resources[ResourceType.WOOD] = 0;
        assertEquals(DiplomacySystem.ACTION_NEEDS_RESOURCES,
                diplomacy.trade(Settlement.RIVERWHISKER, 0, resources, 100));
        resources[ResourceType.WOOD] = 50;
        resources[ResourceType.CRYSTALS] = 100;
        assertEquals(DiplomacySystem.ACTION_STORAGE_FULL,
                diplomacy.trade(Settlement.RIVERWHISKER, 0, resources, 100));
    }

    @Test
    public void regionalMapHasEightDistinctFourOfferMarkets() {
        Settlement[] settlements = new DiplomacySystem().getSettlements();
        assertEquals(8, settlements.length);
        for (int id = 0; id < settlements.length; id++) {
            assertEquals(id, settlements[id].id);
            assertEquals(4, settlements[id].marketOffers.length);
            int sellOffers = 0;
            for (MarketOffer offer : settlements[id].marketOffers) {
                if (offer.isPlayerSelling()) {
                    sellOffers++;
                }
            }
            assertEquals(2, sellOffers);
        }
    }
}
