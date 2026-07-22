/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/** Data-driven building catalogue: economy, placement, progression, and presentation gates. */
final class BuildingType {
    static final int TOWN_HALL = 0;
    static final int FISHING_DOCK = 1;
    static final int LUMBER_CAMP = 2;
    static final int QUARRY = 3;
    static final int CATNIP_FARM = 4;
    static final int WEAVERS_COTTAGE = 5;
    static final int KITTEN_COTTAGE = 6;
    static final int STORAGE_BARN = 7;
    static final int SCHOLARS_DEN = 8;
    static final int COZY_PLAZA = 9;
    static final int CRYSTAL_MINE = 10;
    static final int SMOKEHOUSE = 11;
    static final int SAWMILL = 12;
    static final int STONEMASONS_YARD = 13;
    static final int HERBARIUM = 14;
    static final int DYE_HOUSE = 15;
    static final int CRYSTAL_WORKSHOP = 16;
    static final int LONGHOUSE = 17;
    static final int GRANARY = 18;
    static final int LIBRARY = 19;
    static final int MARKET_SQUARE = 20;
    static final int BOATYARD = 21;
    static final int FORESTERS_LODGE = 22;
    static final int MASON_HALL = 23;
    static final int GLASS_GARDEN = 24;
    static final int LOOM_HALL = 25;
    static final int OBSERVATORY = 26;
    static final int GREAT_HALL = 27;
    static final int TRADE_DEPOT = 28;
    static final int FESTIVAL_GARDEN = 29;
    static final int GEMCUTTERS_HALL = 30;
    static final int ROYAL_ARCHIVE = 31;
    static final int COUNT = 32;

    static final int NONE = -1;

    final int id;
    final int nameRes;
    final int[] cost;
    final int[] outputPerTurn;
    final int[] upkeepPerTurn;
    final int requiredTechId;
    final int requiredAdjacentTerrain;
    final int buildTurns;
    final boolean playerBuildable;
    final int housing;
    final int techPointsPerTurn;
    final int storageCapBonus;
    final int populationGrowthThresholdReduction;
    final int minTurn;
    final int minPopulation;
    final int requiredBuildingTypeId;
    final int requiredBuildingCount;

    private BuildingType(int id, int nameRes, int[] cost, int[] outputPerTurn,
            int[] upkeepPerTurn, int requiredTechId, int requiredAdjacentTerrain,
            int buildTurns, boolean playerBuildable, int housing, int techPointsPerTurn,
            int storageCapBonus, int growthReduction, int minTurn, int minPopulation,
            int requiredBuildingTypeId, int requiredBuildingCount) {
        if (id < 0 || id >= COUNT) {
            throw new IllegalArgumentException("Building id out of range: " + id);
        }
        if (cost.length != ResourceType.COUNT || outputPerTurn.length != ResourceType.COUNT
                || upkeepPerTurn.length != ResourceType.COUNT) {
            throw new IllegalArgumentException("Building resource arrays must have length "
                    + ResourceType.COUNT);
        }
        for (int amount : cost) {
            if (amount < 0) {
                throw new IllegalArgumentException("Building cost must not be negative");
            }
        }
        if (buildTurns < 0 || housing < 0 || techPointsPerTurn < 0
                || storageCapBonus < 0 || growthReduction < 0 || minTurn < 0
                || minPopulation < 0 || requiredBuildingCount < 0) {
            throw new IllegalArgumentException("Building progression values must not be negative");
        }
        this.id = id;
        this.nameRes = nameRes;
        this.cost = cost.clone();
        this.outputPerTurn = outputPerTurn.clone();
        this.upkeepPerTurn = upkeepPerTurn.clone();
        this.requiredTechId = requiredTechId;
        this.requiredAdjacentTerrain = requiredAdjacentTerrain;
        this.buildTurns = buildTurns;
        this.playerBuildable = playerBuildable;
        this.housing = housing;
        this.techPointsPerTurn = techPointsPerTurn;
        this.storageCapBonus = storageCapBonus;
        this.populationGrowthThresholdReduction = growthReduction;
        this.minTurn = minTurn;
        this.minPopulation = minPopulation;
        this.requiredBuildingTypeId = requiredBuildingTypeId;
        this.requiredBuildingCount = requiredBuildingCount;
    }

    static BuildingType[] createAll() {
        BuildingType[] all = new BuildingType[]{
                b(TOWN_HALL, R.string.building_town_hall, c(0, 60, 45, 0, 20, 5), o(), o(),
                        TechNode.GRAND_COUNCIL, TerrainType.NONE, 5, true,
                        4, 1, 80, 0, 28, 8, GREAT_HALL, 1),
                b(FISHING_DOCK, R.string.building_fishing_dock, c(0, 12, 0, 0, 0, 0),
                        o(ResourceType.FISH, 4), o(), TechNode.NONE, TerrainType.WATER,
                        2, true, 0, 0, 0, 0, 0, 0, NONE, 0),
                b(LUMBER_CAMP, R.string.building_lumber_camp, c(0, 0, 8, 0, 0, 0),
                        o(ResourceType.WOOD, 4), o(), TechNode.NONE, TerrainType.FOREST,
                        2, true, 0, 0, 0, 0, 0, 0, NONE, 0),
                b(QUARRY, R.string.building_quarry, c(0, 12, 0, 0, 0, 0),
                        o(ResourceType.STONE, 3), o(), TechNode.NONE, TerrainType.STONE_OUTCROP,
                        2, true, 0, 0, 0, 0, 0, 0, NONE, 0),
                b(CATNIP_FARM, R.string.building_catnip_farm, c(0, 10, 0, 0, 0, 0),
                        o(ResourceType.CATNIP, 4), o(), TechNode.NONE, TerrainType.NONE,
                        2, true, 0, 0, 0, 0, 0, 0, NONE, 0),
                b(WEAVERS_COTTAGE, R.string.building_weavers_cottage, c(0, 16, 8, 0, 0, 0),
                        o(ResourceType.YARN, 3), o(ResourceType.WOOD, 1), TechNode.TEXTILE_CRAFT,
                        TerrainType.NONE, 2, true, 0, 0, 0, 0, 0, 0, NONE, 0),
                b(KITTEN_COTTAGE, R.string.building_kitten_cottage, c(0, 12, 8, 0, 0, 0),
                        o(), o(), TechNode.NONE, TerrainType.NONE, 2, true,
                        4, 0, 0, 0, 0, 0, NONE, 0),
                b(STORAGE_BARN, R.string.building_storage_barn, c(0, 18, 14, 0, 0, 0),
                        o(), o(), TechNode.STONE_MASONRY, TerrainType.NONE, 2, true,
                        0, 0, 60, 0, 0, 0, NONE, 0),
                b(SCHOLARS_DEN, R.string.building_scholars_den, c(0, 16, 10, 0, 8, 0),
                        o(), o(), TechNode.CURIOUS_MINDS, TerrainType.NONE, 2, true,
                        0, 1, 0, 0, 0, 0, NONE, 0),
                b(COZY_PLAZA, R.string.building_cozy_plaza, c(0, 0, 14, 0, 8, 0),
                        o(), o(), TechNode.COMMUNITY_SPIRIT, TerrainType.NONE, 2, true,
                        0, 0, 0, 3, 0, 0, NONE, 0),
                b(CRYSTAL_MINE, R.string.building_crystal_mine, c(0, 20, 15, 0, 0, 0),
                        o(ResourceType.CRYSTALS, 2), o(ResourceType.WOOD, 1),
                        TechNode.CRYSTAL_VEINS, TerrainType.STONE_OUTCROP, 3, true,
                        0, 0, 0, 0, 0, 0, NONE, 0),

                b(SMOKEHOUSE, R.string.building_smokehouse, c(4, 18, 8, 0, 0, 0),
                        o(ResourceType.FISH, 5), o(ResourceType.WOOD, 1), TechNode.PRESERVATION,
                        TerrainType.NONE, 3, true, 0, 0, 0, 0, 6, 3, FISHING_DOCK, 1),
                b(SAWMILL, R.string.building_sawmill, c(0, 10, 16, 0, 0, 0),
                        o(ResourceType.WOOD, 6), o(), TechNode.FORESTRY, TerrainType.FOREST,
                        3, true, 0, 0, 0, 0, 7, 3, LUMBER_CAMP, 1),
                b(STONEMASONS_YARD, R.string.building_stonemasons_yard, c(0, 20, 8, 0, 0, 0),
                        o(ResourceType.STONE, 5), o(), TechNode.STONECRAFT,
                        TerrainType.STONE_OUTCROP, 3, true, 0, 0, 0, 0, 8, 3, QUARRY, 1),
                b(HERBARIUM, R.string.building_herbarium, c(0, 16, 6, 4, 0, 0),
                        o(ResourceType.CATNIP, 5), o(), TechNode.HERBALISM, TerrainType.NONE,
                        3, true, 0, 0, 0, 0, 8, 3, CATNIP_FARM, 1),
                b(DYE_HOUSE, R.string.building_dye_house, c(0, 22, 10, 6, 4, 0),
                        o(ResourceType.YARN, 4), o(ResourceType.CATNIP, 1),
                        TechNode.ADVANCED_TEXTILES, TerrainType.NONE, 3, true,
                        0, 0, 0, 0, 10, 4, WEAVERS_COTTAGE, 1),
                b(CRYSTAL_WORKSHOP, R.string.building_crystal_workshop, c(0, 26, 18, 0, 8, 4),
                        o(ResourceType.CRYSTALS, 3), o(ResourceType.YARN, 1),
                        TechNode.CRYSTAL_CRAFT, TerrainType.NONE, 4, true,
                        0, 0, 0, 0, 22, 6, CRYSTAL_MINE, 1),
                b(LONGHOUSE, R.string.building_longhouse, c(0, 28, 20, 0, 8, 0),
                        o(), o(), TechNode.ARCHITECTURE, TerrainType.NONE, 3, true,
                        8, 0, 0, 0, 12, 4, KITTEN_COTTAGE, 2),
                b(GRANARY, R.string.building_granary, c(0, 24, 18, 0, 0, 0),
                        o(), o(), TechNode.PRESERVATION, TerrainType.NONE, 3, true,
                        0, 0, 100, 0, 10, 4, STORAGE_BARN, 1),
                b(LIBRARY, R.string.building_library, c(0, 24, 20, 0, 12, 0),
                        o(), o(), TechNode.CURIOUS_MINDS, TerrainType.NONE, 3, true,
                        0, 2, 0, 0, 14, 4, SCHOLARS_DEN, 1),
                b(MARKET_SQUARE, R.string.building_market_square, c(0, 18, 18, 0, 10, 0),
                        o(), o(), TechNode.MERCHANT_CUSTOMS, TerrainType.NONE, 3, true,
                        0, 0, 0, 2, 15, 5, TOWN_HALL, 1),
                b(BOATYARD, R.string.building_boatyard, c(0, 34, 18, 0, 6, 0),
                        o(ResourceType.FISH, 7), o(ResourceType.WOOD, 1), TechNode.NAVIGATION,
                        TerrainType.WATER, 4, true, 0, 0, 0, 0, 16, 5, FISHING_DOCK, 2),
                b(FORESTERS_LODGE, R.string.building_foresters_lodge, c(0, 20, 24, 0, 0, 0),
                        o(ResourceType.WOOD, 8), o(), TechNode.FORESTRY, TerrainType.FOREST,
                        4, true, 0, 0, 0, 0, 17, 5, LUMBER_CAMP, 2),
                b(MASON_HALL, R.string.building_mason_hall, c(0, 30, 18, 0, 0, 0),
                        o(ResourceType.STONE, 7), o(), TechNode.STONECRAFT,
                        TerrainType.STONE_OUTCROP, 4, true, 0, 0, 0, 0, 18, 5, QUARRY, 2),
                b(GLASS_GARDEN, R.string.building_glass_garden, c(0, 28, 16, 8, 6, 2),
                        o(ResourceType.CATNIP, 8), o(ResourceType.CRYSTALS, 1),
                        TechNode.HORTICULTURE, TerrainType.NONE, 4, true,
                        0, 0, 0, 0, 19, 6, CATNIP_FARM, 2),
                b(LOOM_HALL, R.string.building_loom_hall, c(0, 34, 20, 4, 10, 0),
                        o(ResourceType.YARN, 7), o(ResourceType.WOOD, 2),
                        TechNode.ADVANCED_TEXTILES, TerrainType.NONE, 4, true,
                        0, 0, 0, 0, 20, 6, WEAVERS_COTTAGE, 2),
                b(OBSERVATORY, R.string.building_observatory, c(0, 24, 32, 0, 10, 8),
                        o(), o(), TechNode.ASTRONOMY, TerrainType.NONE, 4, true,
                        0, 4, 0, 0, 22, 6, SCHOLARS_DEN, 2),
                b(GREAT_HALL, R.string.building_great_hall, c(0, 44, 36, 0, 18, 4),
                        o(), o(), TechNode.GRAND_COUNCIL, TerrainType.NONE, 5, true,
                        14, 1, 0, 0, 24, 7, KITTEN_COTTAGE, 3),
                b(TRADE_DEPOT, R.string.building_trade_depot, c(0, 36, 30, 0, 12, 2),
                        o(), o(), TechNode.MERCHANT_CUSTOMS, TerrainType.NONE, 4, true,
                        0, 0, 160, 0, 25, 7, STORAGE_BARN, 2),
                b(FESTIVAL_GARDEN, R.string.building_festival_garden, c(0, 22, 24, 8, 18, 2),
                        o(), o(), TechNode.FESTIVALS, TerrainType.NONE, 4, true,
                        0, 0, 0, 4, 26, 8, COZY_PLAZA, 2),
                b(GEMCUTTERS_HALL, R.string.building_gemcutters_hall, c(0, 38, 34, 0, 18, 8),
                        o(ResourceType.CRYSTALS, 6), o(ResourceType.YARN, 2),
                        TechNode.CRYSTAL_CRAFT, TerrainType.NONE, 5, true,
                        0, 0, 0, 0, 28, 8, CRYSTAL_MINE, 2),
                b(ROYAL_ARCHIVE, R.string.building_royal_archive, c(0, 48, 42, 0, 20, 12),
                        o(), o(), TechNode.ROYAL_LEGACY, TerrainType.NONE, 5, true,
                        0, 6, 0, 0, 30, 10, SCHOLARS_DEN, 3),
        };
        if (all.length != COUNT) {
            throw new IllegalStateException("Building registry must contain exactly " + COUNT + " entries");
        }
        for (int i = 0; i < all.length; i++) {
            if (all[i].id != i) {
                throw new IllegalStateException("Building registry order must match id: " + i);
            }
        }
        return all;
    }

    private static BuildingType b(int id, int nameRes, int[] cost, int[] output, int[] upkeep,
            int tech, int terrain, int turns, boolean buildable, int housing, int techPoints,
            int storage, int growth, int minTurn, int minPopulation,
            int requiredBuilding, int requiredCount) {
        return new BuildingType(id, nameRes, cost, output, upkeep, tech, terrain, turns,
                buildable, housing, techPoints, storage, growth, minTurn, minPopulation,
                requiredBuilding, requiredCount);
    }

    private static int[] c(int... values) {
        int[] result = new int[ResourceType.COUNT];
        System.arraycopy(values, 0, result, 0, Math.min(values.length, result.length));
        return result;
    }

    private static int[] o() {
        return new int[ResourceType.COUNT];
    }

    private static int[] o(int resourceId, int amount) {
        int[] result = o();
        result[resourceId] = amount;
        return result;
    }
}
