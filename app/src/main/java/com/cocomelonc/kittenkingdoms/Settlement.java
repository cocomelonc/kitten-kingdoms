/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/** Immutable data describing one non-player settlement on the regional map. */
final class Settlement {
    static final int RIVERWHISKER = 0;
    static final int MOSSBELL = 1;
    static final int CLOVERDOWN = 2;
    static final int STARFALL = 3;
    static final int PEBBLEBROOK = 4;
    static final int REEDHAVEN = 5;
    static final int AMBERPINE = 6;
    static final int MOONLOOM = 7;
    static final int COUNT = 8;

    final int id;
    final int nameRes;
    final int descriptionRes;
    final int color;
    final float mapX;
    final float mapY;
    final int requestedResource;
    final MarketOffer[] marketOffers;

    private Settlement(int id, int nameRes, int descriptionRes, int color,
            float mapX, float mapY, int requestedResource, MarketOffer... marketOffers) {
        this.id = id;
        this.nameRes = nameRes;
        this.descriptionRes = descriptionRes;
        this.color = color;
        this.mapX = mapX;
        this.mapY = mapY;
        this.requestedResource = requestedResource;
        this.marketOffers = marketOffers.clone();
    }

    static Settlement[] createAll() {
        Settlement[] all = new Settlement[]{
                new Settlement(RIVERWHISKER, R.string.settlement_riverwhisker,
                        R.string.settlement_riverwhisker_description, 0xFF74A9C2,
                        145f, 175f, ResourceType.WOOD,
                        sell(ResourceType.WOOD, 5, 2), sell(ResourceType.STONE, 6, 2),
                        buy(ResourceType.FISH, 6, 2), buy(ResourceType.CATNIP, 5, 2)),
                new Settlement(MOSSBELL, R.string.settlement_mossbell,
                        R.string.settlement_mossbell_description, 0xFF7FA66B,
                        410f, 180f, ResourceType.STONE,
                        sell(ResourceType.STONE, 5, 2), sell(ResourceType.FISH, 7, 2),
                        buy(ResourceType.WOOD, 6, 2), buy(ResourceType.YARN, 4, 3)),
                new Settlement(CLOVERDOWN, R.string.settlement_cloverdown,
                        R.string.settlement_cloverdown_description, 0xFFD69A78,
                        145f, 350f, ResourceType.CATNIP,
                        sell(ResourceType.CATNIP, 6, 2), sell(ResourceType.FISH, 6, 2),
                        buy(ResourceType.YARN, 4, 3), buy(ResourceType.WOOD, 5, 2)),
                new Settlement(STARFALL, R.string.settlement_starfall,
                        R.string.settlement_starfall_description, 0xFF8E83BC,
                        720f, 175f, ResourceType.YARN,
                        sell(ResourceType.YARN, 4, 3), sell(ResourceType.CATNIP, 7, 2),
                        buy(ResourceType.STONE, 5, 2), buy(ResourceType.FISH, 6, 2)),
                new Settlement(PEBBLEBROOK, R.string.settlement_pebblebrook,
                        R.string.settlement_pebblebrook_description, 0xFF9B98A1,
                        720f, 350f, ResourceType.FISH,
                        sell(ResourceType.FISH, 7, 2), sell(ResourceType.WOOD, 6, 2),
                        buy(ResourceType.STONE, 6, 2), buy(ResourceType.CATNIP, 5, 2)),
                new Settlement(REEDHAVEN, R.string.settlement_reedhaven,
                        R.string.settlement_reedhaven_description, 0xFF73A995,
                        145f, 525f, ResourceType.STONE,
                        sell(ResourceType.STONE, 6, 2), sell(ResourceType.YARN, 5, 3),
                        buy(ResourceType.FISH, 7, 2), buy(ResourceType.WOOD, 6, 2)),
                new Settlement(AMBERPINE, R.string.settlement_amberpine,
                        R.string.settlement_amberpine_description, 0xFFC99455,
                        410f, 535f, ResourceType.CATNIP,
                        sell(ResourceType.CATNIP, 6, 2), sell(ResourceType.STONE, 6, 2),
                        buy(ResourceType.WOOD, 7, 2), buy(ResourceType.YARN, 4, 3)),
                new Settlement(MOONLOOM, R.string.settlement_moonloom,
                        R.string.settlement_moonloom_description, 0xFFB47DA5,
                        720f, 525f, ResourceType.WOOD,
                        sell(ResourceType.WOOD, 6, 2), sell(ResourceType.YARN, 4, 3),
                        buy(ResourceType.CATNIP, 6, 2), buy(ResourceType.STONE, 5, 2)),
        };
        if (all.length != COUNT) {
            throw new IllegalStateException("Settlement registry must contain " + COUNT + " entries");
        }
        for (int id = 0; id < all.length; id++) {
            if (all[id].id != id || all[id].marketOffers.length != 4) {
                throw new IllegalStateException("Invalid settlement market: " + id);
            }
        }
        return all;
    }

    private static MarketOffer sell(int resourceId, int amount, int crystals) {
        return new MarketOffer(resourceId, amount, ResourceType.CRYSTALS, crystals);
    }

    private static MarketOffer buy(int resourceId, int amount, int crystals) {
        return new MarketOffer(ResourceType.CRYSTALS, crystals, resourceId, amount);
    }
}
