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
    static final int COUNT = 4;

    final int id;
    final int nameRes;
    final int descriptionRes;
    final int color;
    final float mapX;
    final float mapY;
    final int requestedResource;
    final int offeredResource;

    private Settlement(int id, int nameRes, int descriptionRes, int color,
            float mapX, float mapY, int requestedResource, int offeredResource) {
        this.id = id;
        this.nameRes = nameRes;
        this.descriptionRes = descriptionRes;
        this.color = color;
        this.mapX = mapX;
        this.mapY = mapY;
        this.requestedResource = requestedResource;
        this.offeredResource = offeredResource;
    }

    static Settlement[] createAll() {
        return new Settlement[]{
                new Settlement(RIVERWHISKER, R.string.settlement_riverwhisker,
                        R.string.settlement_riverwhisker_description, 0xFF74A9C2,
                        205f, 220f, ResourceType.WOOD, ResourceType.FISH),
                new Settlement(MOSSBELL, R.string.settlement_mossbell,
                        R.string.settlement_mossbell_description, 0xFF7FA66B,
                        690f, 205f, ResourceType.STONE, ResourceType.WOOD),
                new Settlement(CLOVERDOWN, R.string.settlement_cloverdown,
                        R.string.settlement_cloverdown_description, 0xFFD69A78,
                        200f, 515f, ResourceType.CATNIP, ResourceType.YARN),
                new Settlement(STARFALL, R.string.settlement_starfall,
                        R.string.settlement_starfall_description, 0xFF8E83BC,
                        690f, 505f, ResourceType.YARN, ResourceType.CRYSTALS),
        };
    }
}
