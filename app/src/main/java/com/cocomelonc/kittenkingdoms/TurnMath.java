/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import java.util.List;

/**
 * Stateless per-turn economy formulas, factored out of {@link KingdomWorld} so each rule is a
 * small, directly testable pure function. No randomness affects any outcome here.
 */
final class TurnMath {
    static final int BASE_POPULATION_GROWTH_THRESHOLD = 5;
    static final int MIN_POPULATION_GROWTH_THRESHOLD = 1;
    static final int STORAGE_PER_HOUSING_SPACE = 5;

    private TurnMath() {
    }

    static boolean canAffordUpkeep(BuildingType type, int[] currentResources) {
        for (int resource = 0; resource < ResourceType.COUNT; resource++) {
            if (type.upkeepPerTurn[resource] > 0 && currentResources[resource] < type.upkeepPerTurn[resource]) {
                return false;
            }
        }
        return true;
    }

    static int computeYield(BuildingType type, boolean[] techUnlocked, TechNode[] techs,
                            int resourceId) {
        int output = type.outputPerTurn[resourceId];
        int bonusPercent = totalYieldBonusPercent(techUnlocked, techs, resourceId);
        return output + (output * bonusPercent) / 100;
    }

    private static int totalYieldBonusPercent(boolean[] techUnlocked, TechNode[] techs, int resourceId) {
        int total = 0;
        for (TechNode tech : techs) {
            if (techUnlocked[tech.id] && tech.yieldBonusResourceId == resourceId) {
                total += tech.yieldBonusPercent;
            }
        }
        return total;
    }

    static int computeStorageCap(List<PlacedBuilding> buildings, BuildingType[] types) {
        int cap = 0;
        for (PlacedBuilding building : buildings) {
            if (building.isComplete()) {
                BuildingType type = types[building.typeId];
                cap += type.storageCapBonus + type.housing * STORAGE_PER_HOUSING_SPACE;
            }
        }
        return cap;
    }

    static int computeHousingCap(List<PlacedBuilding> buildings, BuildingType[] types) {
        int cap = 0;
        for (PlacedBuilding building : buildings) {
            if (building.isComplete()) {
                cap += types[building.typeId].housing;
            }
        }
        return cap;
    }

    static int computeTechPointsPerTurn(List<PlacedBuilding> buildings, BuildingType[] types) {
        int total = 0;
        for (PlacedBuilding building : buildings) {
            if (building.isComplete()) {
                total += types[building.typeId].techPointsPerTurn;
            }
        }
        return total;
    }

    static int computePopulationGrowthThreshold(List<PlacedBuilding> buildings, BuildingType[] types,
                                                boolean[] techUnlocked, TechNode[] techs) {
        int reduction = 0;
        for (PlacedBuilding building : buildings) {
            if (building.isComplete()) {
                reduction += types[building.typeId].populationGrowthThresholdReduction;
            }
        }
        for (TechNode tech : techs) {
            if (techUnlocked[tech.id]) {
                reduction += tech.populationGrowthThresholdReduction;
            }
        }
        return Math.max(MIN_POPULATION_GROWTH_THRESHOLD, BASE_POPULATION_GROWTH_THRESHOLD - reduction);
    }

    static int computeFoodUpkeep(int population) {
        return (population + 3) / 4;
    }

    /**
     * How many turns a staffed workshop spends gathering before it readies one batch of the given
     * resource. Staggering the rhythms keeps deliveries and on-screen events from all landing on the
     * same turn once a kingdom has many workshops, so the world grows slowly and calmly. Fish stays
     * every turn because it feeds food upkeep and wages; heavier raw materials come in more slowly.
     */
    static int productionIntervalForResource(int resourceId) {
        switch (resourceId) {
            case ResourceType.FISH:
                return 1;
            case ResourceType.CATNIP:
            case ResourceType.YARN:
                return 2;
            case ResourceType.WOOD:
            case ResourceType.STONE:
                return 3;
            case ResourceType.CRYSTALS:
                return 4;
            default:
                return 1;
        }
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
