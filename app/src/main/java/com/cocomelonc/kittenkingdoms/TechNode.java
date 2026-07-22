/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/** Data-driven research card with prerequisite, time, economy, and settlement gates. */
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
    static final int CRYSTAL_VEINS = 9;
    static final int PRESERVATION = 10;
    static final int FORESTRY = 11;
    static final int STONECRAFT = 12;
    static final int HERBALISM = 13;
    static final int MERCHANT_CUSTOMS = 14;
    static final int ADVANCED_TEXTILES = 15;
    static final int ARCHITECTURE = 16;
    static final int NAVIGATION = 17;
    static final int HORTICULTURE = 18;
    static final int ASTRONOMY = 19;
    static final int FESTIVALS = 20;
    static final int CRYSTAL_CRAFT = 21;
    static final int GRAND_COUNCIL = 22;
    static final int ROYAL_LEGACY = 23;
    static final int COUNT = 24;

    static final int NONE = -1;

    final int id;
    final int nameRes;
    final int cost;
    final int[] prerequisites;
    final int unlocksBuildingId;
    final int yieldBonusResourceId;
    final int yieldBonusPercent;
    final int populationGrowthThresholdReduction;
    final int minTurn;
    final int minPopulation;
    final int requiredBuildingTypeId;
    final int requiredBuildingCount;
    final int requiredResourceId;
    final int requiredResourceAmount;
    final int iconBuildingId;

    private TechNode(int id, int nameRes, int cost, int[] prerequisites,
            int unlocksBuildingId, int yieldResource, int yieldPercent, int growthReduction,
            int minTurn, int minPopulation, int requiredBuilding, int requiredCount,
            int requiredResource, int requiredAmount, int iconBuildingId) {
        if (id < 0 || id >= COUNT || cost <= 0) {
            throw new IllegalArgumentException("Invalid technology: " + id);
        }
        this.id = id;
        this.nameRes = nameRes;
        this.cost = cost;
        this.prerequisites = prerequisites.clone();
        this.unlocksBuildingId = unlocksBuildingId;
        this.yieldBonusResourceId = yieldResource;
        this.yieldBonusPercent = yieldPercent;
        this.populationGrowthThresholdReduction = growthReduction;
        this.minTurn = minTurn;
        this.minPopulation = minPopulation;
        this.requiredBuildingTypeId = requiredBuilding;
        this.requiredBuildingCount = requiredCount;
        this.requiredResourceId = requiredResource;
        this.requiredResourceAmount = requiredAmount;
        this.iconBuildingId = iconBuildingId;
    }

    static TechNode[] createAll() {
        TechNode[] all = new TechNode[]{
                n(BASIC_TOOLS, R.string.tech_basic_tools, 5, p(), BuildingType.NONE,
                        ResourceType.WOOD, 10, 0, 0, 0, BuildingType.NONE, 0,
                        ResourceType.NONE, 0, BuildingType.LUMBER_CAMP),
                n(FISHING_NETS, R.string.tech_fishing_nets, 8, p(BASIC_TOOLS), BuildingType.NONE,
                        ResourceType.FISH, 25, 0, 0, 0, BuildingType.NONE, 0,
                        ResourceType.NONE, 0, BuildingType.FISHING_DOCK),
                n(TEXTILE_CRAFT, R.string.tech_textile_craft, 10, p(BASIC_TOOLS),
                        BuildingType.WEAVERS_COTTAGE, ResourceType.NONE, 0, 0, 0, 0,
                        BuildingType.NONE, 0, ResourceType.NONE, 0, BuildingType.WEAVERS_COTTAGE),
                n(STONE_MASONRY, R.string.tech_stone_masonry, 10, p(BASIC_TOOLS),
                        BuildingType.STORAGE_BARN, ResourceType.STONE, 20, 0, 0, 0,
                        BuildingType.NONE, 0, ResourceType.NONE, 0, BuildingType.QUARRY),
                n(EFFICIENT_FARMING, R.string.tech_efficient_farming, 10, p(BASIC_TOOLS),
                        BuildingType.NONE, ResourceType.CATNIP, 25, 0, 0, 0,
                        BuildingType.NONE, 0, ResourceType.NONE, 0, BuildingType.CATNIP_FARM),
                n(CURIOUS_MINDS, R.string.tech_curious_minds, 15, p(FISHING_NETS, TEXTILE_CRAFT),
                        BuildingType.SCHOLARS_DEN, ResourceType.NONE, 0, 0, 0, 0,
                        BuildingType.NONE, 0, ResourceType.NONE, 0, BuildingType.SCHOLARS_DEN),
                n(WARM_BEDDING, R.string.tech_warm_bedding, 12, p(TEXTILE_CRAFT), BuildingType.NONE,
                        ResourceType.NONE, 0, 1, 0, 0, BuildingType.NONE, 0,
                        ResourceType.NONE, 0, BuildingType.KITTEN_COTTAGE),
                n(COMMUNITY_SPIRIT, R.string.tech_community_spirit, 18, p(CURIOUS_MINDS),
                        BuildingType.COZY_PLAZA, ResourceType.NONE, 0, 0, 0, 0,
                        BuildingType.NONE, 0, ResourceType.NONE, 0, BuildingType.COZY_PLAZA),
                n(KINGDOM_CHARTER, R.string.tech_kingdom_charter, 25,
                        p(COMMUNITY_SPIRIT, EFFICIENT_FARMING), BuildingType.NONE,
                        ResourceType.NONE, 0, 1, 0, 0, BuildingType.NONE, 0,
                        ResourceType.NONE, 0, BuildingType.TOWN_HALL),
                n(CRYSTAL_VEINS, R.string.tech_crystal_veins, 30, p(KINGDOM_CHARTER),
                        BuildingType.CRYSTAL_MINE, ResourceType.NONE, 0, 0, 0, 0,
                        BuildingType.NONE, 0, ResourceType.NONE, 0, BuildingType.CRYSTAL_MINE),

                n(PRESERVATION, R.string.tech_preservation, 14, p(FISHING_NETS),
                        BuildingType.SMOKEHOUSE, ResourceType.FISH, 15, 0, 6, 3,
                        BuildingType.FISHING_DOCK, 1, ResourceType.FISH, 10, BuildingType.SMOKEHOUSE),
                n(FORESTRY, R.string.tech_forestry, 14, p(BASIC_TOOLS), BuildingType.SAWMILL,
                        ResourceType.WOOD, 20, 0, 7, 3, BuildingType.LUMBER_CAMP, 1,
                        ResourceType.WOOD, 12, BuildingType.SAWMILL),
                n(STONECRAFT, R.string.tech_stonecraft, 16, p(STONE_MASONRY),
                        BuildingType.STONEMASONS_YARD, ResourceType.STONE, 20, 0, 8, 3,
                        BuildingType.QUARRY, 1, ResourceType.STONE, 12, BuildingType.STONEMASONS_YARD),
                n(HERBALISM, R.string.tech_herbalism, 16, p(EFFICIENT_FARMING),
                        BuildingType.HERBARIUM, ResourceType.CATNIP, 20, 0, 8, 3,
                        BuildingType.CATNIP_FARM, 1, ResourceType.CATNIP, 8, BuildingType.HERBARIUM),
                n(MERCHANT_CUSTOMS, R.string.tech_merchant_customs, 22, p(COMMUNITY_SPIRIT),
                        BuildingType.MARKET_SQUARE, ResourceType.NONE, 0, 0, 12, 4,
                        BuildingType.STORAGE_BARN, 1, ResourceType.WOOD, 20, BuildingType.MARKET_SQUARE),
                n(ADVANCED_TEXTILES, R.string.tech_advanced_textiles, 24,
                        p(TEXTILE_CRAFT, WARM_BEDDING), BuildingType.DYE_HOUSE,
                        ResourceType.YARN, 25, 0, 14, 5, BuildingType.WEAVERS_COTTAGE, 1,
                        ResourceType.YARN, 8, BuildingType.LOOM_HALL),
                n(ARCHITECTURE, R.string.tech_architecture, 24, p(STONE_MASONRY, WARM_BEDDING),
                        BuildingType.LONGHOUSE, ResourceType.NONE, 0, 0, 14, 5,
                        BuildingType.KITTEN_COTTAGE, 2, ResourceType.STONE, 20, BuildingType.LONGHOUSE),
                n(NAVIGATION, R.string.tech_navigation, 28, p(FISHING_NETS, PRESERVATION),
                        BuildingType.BOATYARD, ResourceType.FISH, 20, 0, 16, 5,
                        BuildingType.FISHING_DOCK, 2, ResourceType.FISH, 18, BuildingType.BOATYARD),
                n(HORTICULTURE, R.string.tech_horticulture, 28, p(HERBALISM, EFFICIENT_FARMING),
                        BuildingType.GLASS_GARDEN, ResourceType.CATNIP, 25, 0, 17, 6,
                        BuildingType.CATNIP_FARM, 2, ResourceType.CATNIP, 16, BuildingType.GLASS_GARDEN),
                n(ASTRONOMY, R.string.tech_astronomy, 36, p(CURIOUS_MINDS, CRYSTAL_VEINS),
                        BuildingType.OBSERVATORY, ResourceType.NONE, 0, 0, 22, 6,
                        BuildingType.SCHOLARS_DEN, 1, ResourceType.CRYSTALS, 8, BuildingType.OBSERVATORY),
                n(FESTIVALS, R.string.tech_festivals, 30, p(COMMUNITY_SPIRIT, WARM_BEDDING),
                        BuildingType.FESTIVAL_GARDEN, ResourceType.NONE, 0, 2, 18, 6,
                        BuildingType.COZY_PLAZA, 1, ResourceType.YARN, 10, BuildingType.FESTIVAL_GARDEN),
                n(CRYSTAL_CRAFT, R.string.tech_crystal_craft, 38, p(CRYSTAL_VEINS, STONECRAFT),
                        BuildingType.CRYSTAL_WORKSHOP, ResourceType.CRYSTALS, 25, 0, 22, 7,
                        BuildingType.CRYSTAL_MINE, 1, ResourceType.CRYSTALS, 12,
                        BuildingType.GEMCUTTERS_HALL),
                n(GRAND_COUNCIL, R.string.tech_grand_council, 45,
                        p(KINGDOM_CHARTER, ARCHITECTURE, MERCHANT_CUSTOMS), BuildingType.GREAT_HALL,
                        ResourceType.NONE, 0, 1, 25, 8, BuildingType.TOWN_HALL, 1,
                        ResourceType.WOOD, 30, BuildingType.GREAT_HALL),
                n(ROYAL_LEGACY, R.string.tech_royal_legacy, 60,
                        p(GRAND_COUNCIL, ASTRONOMY, CRYSTAL_CRAFT), BuildingType.ROYAL_ARCHIVE,
                        ResourceType.NONE, 0, 2, 30, 10, BuildingType.GREAT_HALL, 1,
                        ResourceType.CRYSTALS, 20, BuildingType.ROYAL_ARCHIVE),
        };
        if (all.length != COUNT) {
            throw new IllegalStateException("Technology registry must contain exactly " + COUNT + " entries");
        }
        for (int i = 0; i < all.length; i++) {
            if (all[i].id != i) {
                throw new IllegalStateException("Technology registry order must match id: " + i);
            }
            for (int prerequisite : all[i].prerequisites) {
                if (prerequisite < 0 || prerequisite >= COUNT || prerequisite == i) {
                    throw new IllegalStateException("Invalid prerequisite on technology " + i);
                }
            }
        }
        return all;
    }

    static boolean prerequisitesMet(TechNode node, boolean[] unlocked) {
        for (int prerequisite : node.prerequisites) {
            if (prerequisite >= unlocked.length || !unlocked[prerequisite]) {
                return false;
            }
        }
        return true;
    }

    static boolean conditionsMet(TechNode node, boolean[] unlocked, int turn, int population,
            int[] resources, int[] buildingCounts) {
        if (!prerequisitesMet(node, unlocked) || turn < node.minTurn
                || population < node.minPopulation) {
            return false;
        }
        if (node.requiredResourceId != ResourceType.NONE
                && resources[node.requiredResourceId] < node.requiredResourceAmount) {
            return false;
        }
        return node.requiredBuildingTypeId == BuildingType.NONE
                || buildingCounts[node.requiredBuildingTypeId] >= node.requiredBuildingCount;
    }

    private static TechNode n(int id, int name, int cost, int[] prerequisites, int unlock,
            int yieldResource, int yieldPercent, int growth, int minTurn, int minPopulation,
            int requiredBuilding, int requiredCount, int requiredResource, int requiredAmount,
            int iconBuilding) {
        return new TechNode(id, name, cost, prerequisites, unlock, yieldResource, yieldPercent,
                growth, minTurn, minPopulation, requiredBuilding, requiredCount,
                requiredResource, requiredAmount, iconBuilding);
    }

    private static int[] p(int... prerequisites) {
        return prerequisites;
    }
}
