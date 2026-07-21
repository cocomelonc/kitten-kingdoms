/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/** Data-driven technology tree node: a DAG gating buildings and yield bonuses. */
final class TechNode {
    static final int BASIC_TOOLS = 0;
    static final int FISHING_NETS = 1;
    static final int TEXTILE_CRAFT = 2;
    static final int STONE_MASONRY = 3;
    static final int EFFICIENT_FARMING = 4;
    static final int CURIOUS_MINDS = 5;
    static final int WARM_BEDDING = 6;
    static final int COMMUNITY_SPIRIT = 7;
    static final int KINGDOM_CHARTER = 8;
    static final int COUNT = 9;

    static final int NONE = -1;

    final int id;
    final int nameRes;
    final int cost;
    final int[] prerequisites;
    final int unlocksBuildingId;
    final int yieldBonusResourceId;
    final int yieldBonusPercent;
    final int populationGrowthThresholdReduction;

    private TechNode(
            int id,
            int nameRes,
            int cost,
            int[] prerequisites,
            int unlocksBuildingId,
            int yieldBonusResourceId,
            int yieldBonusPercent,
            int populationGrowthThresholdReduction
    ) {
        if (id < 0 || id >= COUNT) {
            throw new IllegalArgumentException("Tech id out of range: " + id);
        }
        if (cost <= 0) {
            throw new IllegalArgumentException("Tech cost must be positive");
        }
        for (int prereq : prerequisites) {
            if (prereq == id) {
                throw new IllegalArgumentException("Tech cannot be its own prerequisite: " + id);
            }
        }
        this.id = id;
        this.nameRes = nameRes;
        this.cost = cost;
        this.prerequisites = prerequisites.clone();
        this.unlocksBuildingId = unlocksBuildingId;
        this.yieldBonusResourceId = yieldBonusResourceId;
        this.yieldBonusPercent = yieldBonusPercent;
        this.populationGrowthThresholdReduction = populationGrowthThresholdReduction;
    }

    static TechNode[] createAll() {
        TechNode[] all = new TechNode[]{
                basicTools(), fishingNets(), textileCraft(), stoneMasonry(), efficientFarming(),
                curiousMinds(), warmBedding(), communitySpirit(), kingdomCharter()
        };
        if (all.length != COUNT) {
            throw new IllegalStateException("Tech registry must contain exactly " + COUNT + " entries");
        }
        for (int i = 0; i < all.length; i++) {
            if (all[i].id != i) {
                throw new IllegalStateException("Tech registry order must match id: " + i);
            }
            for (int prereq : all[i].prerequisites) {
                if (prereq < 0 || prereq >= COUNT) {
                    throw new IllegalStateException("Unknown prerequisite id " + prereq
                            + " referenced by tech " + i);
                }
            }
        }
        return all;
    }

    private static TechNode basicTools() {
        return new TechNode(BASIC_TOOLS, R.string.tech_basic_tools, 5, new int[]{},
                BuildingType.NONE, ResourceType.NONE, 0, 0);
    }

    private static TechNode fishingNets() {
        return new TechNode(FISHING_NETS, R.string.tech_fishing_nets, 8, new int[]{BASIC_TOOLS},
                BuildingType.NONE, ResourceType.FISH, 25, 0);
    }

    private static TechNode textileCraft() {
        return new TechNode(TEXTILE_CRAFT, R.string.tech_textile_craft, 10, new int[]{BASIC_TOOLS},
                BuildingType.WEAVERS_COTTAGE, ResourceType.NONE, 0, 0);
    }

    private static TechNode stoneMasonry() {
        return new TechNode(STONE_MASONRY, R.string.tech_stone_masonry, 10, new int[]{BASIC_TOOLS},
                BuildingType.STORAGE_BARN, ResourceType.STONE, 20, 0);
    }

    private static TechNode efficientFarming() {
        return new TechNode(EFFICIENT_FARMING, R.string.tech_efficient_farming, 10, new int[]{BASIC_TOOLS},
                BuildingType.NONE, ResourceType.CATNIP, 25, 0);
    }

    private static TechNode curiousMinds() {
        return new TechNode(CURIOUS_MINDS, R.string.tech_curious_minds, 15,
                new int[]{FISHING_NETS, TEXTILE_CRAFT},
                BuildingType.SCHOLARS_DEN, ResourceType.NONE, 0, 0);
    }

    private static TechNode warmBedding() {
        return new TechNode(WARM_BEDDING, R.string.tech_warm_bedding, 12, new int[]{TEXTILE_CRAFT},
                BuildingType.NONE, ResourceType.NONE, 0, 1);
    }

    private static TechNode communitySpirit() {
        return new TechNode(COMMUNITY_SPIRIT, R.string.tech_community_spirit, 18,
                new int[]{CURIOUS_MINDS},
                BuildingType.COZY_PLAZA, ResourceType.NONE, 0, 0);
    }

    private static TechNode kingdomCharter() {
        return new TechNode(KINGDOM_CHARTER, R.string.tech_kingdom_charter, 25,
                new int[]{COMMUNITY_SPIRIT, EFFICIENT_FARMING},
                BuildingType.NONE, ResourceType.NONE, 0, 0);
    }
}
