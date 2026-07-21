/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

/** Data-driven building kinds: cost, per-turn output/upkeep, and placement gates. */
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
    static final int COUNT = 10;

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

    private BuildingType(
            int id,
            int nameRes,
            int[] cost,
            int[] outputPerTurn,
            int[] upkeepPerTurn,
            int requiredTechId,
            int requiredAdjacentTerrain,
            int buildTurns,
            boolean playerBuildable,
            int housing,
            int techPointsPerTurn,
            int storageCapBonus,
            int populationGrowthThresholdReduction
    ) {
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
        if (buildTurns < 0) {
            throw new IllegalArgumentException("Building construction time must not be negative");
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
        this.populationGrowthThresholdReduction = populationGrowthThresholdReduction;
    }

    static BuildingType[] createAll() {
        BuildingType[] all = new BuildingType[]{
                townHall(), fishingDock(), lumberCamp(), quarry(), catnipFarm(),
                weaversCottage(), kittenCottage(), storageBarn(), scholarsDen(), cozyPlaza()
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

    private static int[] costOf(int wood, int stone, int yarn) {
        int[] cost = new int[ResourceType.COUNT];
        cost[ResourceType.WOOD] = wood;
        cost[ResourceType.STONE] = stone;
        cost[ResourceType.YARN] = yarn;
        return cost;
    }

    private static int[] outputOf(int resourceId, int amount) {
        int[] output = new int[ResourceType.COUNT];
        if (amount != 0) {
            output[resourceId] = amount;
        }
        return output;
    }

    private static int[] noAmounts() {
        return new int[ResourceType.COUNT];
    }

    private static BuildingType townHall() {
        return new BuildingType(TOWN_HALL, R.string.building_town_hall,
                noAmounts(), noAmounts(), noAmounts(),
                TechNode.NONE, TerrainType.NONE, 0, false,
                4, 1, 80, 0);
    }

    private static BuildingType fishingDock() {
        return new BuildingType(FISHING_DOCK, R.string.building_fishing_dock,
                costOf(12, 0, 0), outputOf(ResourceType.FISH, 4), noAmounts(),
                TechNode.NONE, TerrainType.WATER, 2, true,
                0, 0, 0, 0);
    }

    private static BuildingType lumberCamp() {
        return new BuildingType(LUMBER_CAMP, R.string.building_lumber_camp,
                costOf(0, 8, 0), outputOf(ResourceType.WOOD, 4), noAmounts(),
                TechNode.NONE, TerrainType.FOREST, 2, true,
                0, 0, 0, 0);
    }

    private static BuildingType quarry() {
        return new BuildingType(QUARRY, R.string.building_quarry,
                costOf(12, 0, 0), outputOf(ResourceType.STONE, 3), noAmounts(),
                TechNode.NONE, TerrainType.STONE_OUTCROP, 2, true,
                0, 0, 0, 0);
    }

    private static BuildingType catnipFarm() {
        return new BuildingType(CATNIP_FARM, R.string.building_catnip_farm,
                costOf(10, 0, 0), outputOf(ResourceType.CATNIP, 4), noAmounts(),
                TechNode.NONE, TerrainType.NONE, 2, true,
                0, 0, 0, 0);
    }

    private static BuildingType weaversCottage() {
        int[] upkeep = new int[ResourceType.COUNT];
        upkeep[ResourceType.WOOD] = 1;
        return new BuildingType(WEAVERS_COTTAGE, R.string.building_weavers_cottage,
                costOf(16, 8, 0), outputOf(ResourceType.YARN, 3), upkeep,
                TechNode.TEXTILE_CRAFT, TerrainType.NONE, 2, true,
                0, 0, 0, 0);
    }

    private static BuildingType kittenCottage() {
        return new BuildingType(KITTEN_COTTAGE, R.string.building_kitten_cottage,
                costOf(12, 8, 0), noAmounts(), noAmounts(),
                TechNode.NONE, TerrainType.NONE, 2, true,
                4, 0, 0, 0);
    }

    private static BuildingType storageBarn() {
        return new BuildingType(STORAGE_BARN, R.string.building_storage_barn,
                costOf(18, 14, 0), noAmounts(), noAmounts(),
                TechNode.STONE_MASONRY, TerrainType.NONE, 2, true,
                0, 0, 60, 0);
    }

    private static BuildingType scholarsDen() {
        return new BuildingType(SCHOLARS_DEN, R.string.building_scholars_den,
                costOf(16, 10, 8), noAmounts(), noAmounts(),
                TechNode.CURIOUS_MINDS, TerrainType.NONE, 2, true,
                0, 1, 0, 0);
    }

    private static BuildingType cozyPlaza() {
        return new BuildingType(COZY_PLAZA, R.string.building_cozy_plaza,
                costOf(0, 14, 8), noAmounts(), noAmounts(),
                TechNode.COMMUNITY_SPIRIT, TerrainType.NONE, 2, true,
                0, 0, 0, 3);
    }
}
