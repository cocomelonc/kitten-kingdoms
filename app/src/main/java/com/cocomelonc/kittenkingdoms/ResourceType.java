/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/** Data-driven stockpiled resource kinds. */
final class ResourceType {
    static final int FISH = 0;
    static final int WOOD = 1;
    static final int STONE = 2;
    static final int CATNIP = 3;
    static final int YARN = 4;
    static final int CRYSTALS = 5;
    static final int COUNT = 6;
    static final int NONE = -1;

    final int id;
    final int nameRes;
    final int color;

    private ResourceType(int id, int nameRes, int color) {
        if (id < 0 || id >= COUNT) {
            throw new IllegalArgumentException("Resource id out of range: " + id);
        }
        this.id = id;
        this.nameRes = nameRes;
        this.color = color;
    }

    static ResourceType[] createAll() {
        ResourceType[] all = new ResourceType[]{
                fish(), wood(), stone(), catnip(), yarn(), crystals()
        };
        if (all.length != COUNT) {
            throw new IllegalStateException("Resource registry must contain exactly " + COUNT + " entries");
        }
        for (int i = 0; i < all.length; i++) {
            if (all[i].id != i) {
                throw new IllegalStateException("Resource registry order must match id: " + i);
            }
        }
        return all;
    }

    private static ResourceType fish() {
        return new ResourceType(FISH, R.string.resource_fish, 0xFF6FB0D8);
    }

    private static ResourceType wood() {
        return new ResourceType(WOOD, R.string.resource_wood, 0xFF9B7550);
    }

    private static ResourceType stone() {
        return new ResourceType(STONE, R.string.resource_stone, 0xFFA8A2AC);
    }

    private static ResourceType catnip() {
        return new ResourceType(CATNIP, R.string.resource_catnip, 0xFF8FC15C);
    }

    private static ResourceType yarn() {
        return new ResourceType(YARN, R.string.resource_yarn, 0xFFD88FB0);
    }

    private static ResourceType crystals() {
        return new ResourceType(CRYSTALS, R.string.resource_crystals, 0xFF8E7CD8);
    }
}
