/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java kingdom state and rules: continuous kitten exploration over a turn-based economy.
 * Fog-of-war reveals as the kitten walks; building placement, production, population, and
 * technology all advance only on an explicit {@link #endTurn()}.
 */
final class KingdomWorld {
    enum State {
        TITLE,
        PLAYING,
        PAUSED
    }

    interface Listener {
        void onTurnResolved(int[] resourceDeltas);

        void onBuildingCompleted(int buildingTypeId, int row, int col);

        void onTechUnlocked(int techId);

        void onPopulationGrew(int newPopulation);
    }

    private static final float MOVE_SPEED = 6.4f;
    private static final int STARTING_WOOD = 35;
    private static final int STARTING_STONE = 25;
    private static final int STARTING_POPULATION = 2;
    private static final int[] ROW_STEP = {-1, 0, 1, 0};
    private static final int[] COL_STEP = {0, 1, 0, -1};

    private final Listener listener;
    private final BuildingType[] buildingTypes;
    private final TechNode[] techNodes;
    private final List<PlacedBuilding> buildings = new ArrayList<>();
    private final int[] resources = new int[ResourceType.COUNT];
    private final boolean[] techUnlocked = new boolean[TechNode.COUNT];
    private final ArrayDeque<Integer> path = new ArrayDeque<>();

    private WorldMap map;
    private State state = State.TITLE;
    private int turn;
    private int population;
    private int techPointPool;
    private int activeTechId = TechNode.NONE;
    private int pendingBuildingTypeId = BuildingType.NONE;
    private int row;
    private int col;
    private float visualRow;
    private float visualCol;
    private float facing = 1f;

    KingdomWorld(Listener listener) {
        this.listener = listener;
        this.buildingTypes = BuildingType.createAll();
        this.techNodes = TechNode.createAll();
        this.map = new WorldMap();
        resetKingdomState();
    }

    void beginNewKingdom() {
        map = new WorldMap();
        resetKingdomState();
        state = State.PLAYING;
    }

    void continueKingdom(KingdomSaveData data) {
        restore(data);
        state = State.PLAYING;
    }

    void showTitle() {
        path.clear();
        pendingBuildingTypeId = BuildingType.NONE;
        state = State.TITLE;
    }

    void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    private void resetKingdomState() {
        buildings.clear();
        Arrays.fill(resources, 0);
        resources[ResourceType.WOOD] = STARTING_WOOD;
        resources[ResourceType.STONE] = STARTING_STONE;
        population = STARTING_POPULATION;
        techPointPool = 0;
        activeTechId = TechNode.NONE;
        Arrays.fill(techUnlocked, false);
        turn = 0;
        row = WorldMap.START_ROW;
        col = WorldMap.START_COL;
        visualRow = row;
        visualCol = col;
        facing = 1f;
        path.clear();
        pendingBuildingTypeId = BuildingType.NONE;
        buildings.add(new PlacedBuilding(BuildingType.TOWN_HALL, row, col, 0));
        map.markOccupied(row, col);
        map.revealAround(row, col);
    }

    void setResourceForTest(int resourceId, int amount) {
        resources[resourceId] = amount;
    }

    void placeBuildingForTest(int typeId, int atRow, int atCol, int turnsRemaining) {
        buildings.add(new PlacedBuilding(typeId, atRow, atCol, turnsRemaining));
        map.markOccupied(atRow, atCol);
    }

    /** Selects a building type to place on the next tap of a discovered, eligible tile. */
    void selectBuildingForPlacement(int buildingTypeId) {
        if (buildingTypeId < 0 || buildingTypeId >= BuildingType.COUNT
                || !buildingTypes[buildingTypeId].playerBuildable) {
            return;
        }
        pendingBuildingTypeId = buildingTypeId;
    }

    void cancelPlacement() {
        pendingBuildingTypeId = BuildingType.NONE;
    }

    boolean canAffordBuilding(int buildingTypeId) {
        BuildingType type = buildingTypes[buildingTypeId];
        for (int r = 0; r < ResourceType.COUNT; r++) {
            if (resources[r] < type.cost[r]) {
                return false;
            }
        }
        return type.requiredTechId == TechNode.NONE || techUnlocked[type.requiredTechId];
    }

    boolean canPlaceBuildingAt(int buildingTypeId, int targetRow, int targetCol) {
        if (buildingTypeId < 0 || buildingTypeId >= BuildingType.COUNT) {
            return false;
        }
        BuildingType type = buildingTypes[buildingTypeId];
        if (!map.isExplored(targetRow, targetCol) || !map.isBuildable(targetRow, targetCol)) {
            return false;
        }
        if (type.requiredTechId != TechNode.NONE && !techUnlocked[type.requiredTechId]) {
            return false;
        }
        if (type.requiredAdjacentTerrain != TerrainType.NONE
                && !map.hasAdjacentTerrain(targetRow, targetCol, type.requiredAdjacentTerrain)) {
            return false;
        }
        return canAffordBuilding(buildingTypeId);
    }

    /** Taps a tile: places the pending building there, or otherwise walks the kitten toward it. */
    boolean tapCell(int targetRow, int targetCol) {
        if (state != State.PLAYING || !map.inBounds(targetRow, targetCol)) {
            return false;
        }
        if (pendingBuildingTypeId != BuildingType.NONE) {
            return tryPlaceBuilding(targetRow, targetCol);
        }
        return tryMoveTo(targetRow, targetCol);
    }

    private boolean tryPlaceBuilding(int targetRow, int targetCol) {
        if (!canPlaceBuildingAt(pendingBuildingTypeId, targetRow, targetCol)) {
            return false;
        }
        BuildingType type = buildingTypes[pendingBuildingTypeId];
        for (int r = 0; r < ResourceType.COUNT; r++) {
            resources[r] -= type.cost[r];
        }
        buildings.add(new PlacedBuilding(type.id, targetRow, targetCol, type.buildTurns));
        map.markOccupied(targetRow, targetCol);
        pendingBuildingTypeId = BuildingType.NONE;
        return true;
    }

    private boolean tryMoveTo(int targetRow, int targetCol) {
        if (!map.isWalkable(targetRow, targetCol)) {
            return false;
        }
        int routeStartRow = row;
        int routeStartCol = col;
        int pendingCell = -1;
        if (!path.isEmpty() && Math.hypot(visualRow - row, visualCol - col) > 0.0001) {
            pendingCell = path.peekFirst();
            routeStartRow = pendingCell / WorldMap.SIZE;
            routeStartCol = pendingCell % WorldMap.SIZE;
        }
        if (targetRow == row && targetCol == col) {
            path.clear();
            if (pendingCell >= 0) {
                path.add(encode(row, col));
            }
            return true;
        }

        int total = WorldMap.SIZE * WorldMap.SIZE;
        int[] parent = new int[total];
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[total];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        int start = encode(routeStartRow, routeStartCol);
        int goal = encode(targetRow, targetCol);
        visited[start] = true;
        open.add(start);

        while (!open.isEmpty() && !visited[goal]) {
            int current = open.removeFirst();
            int currentRow = current / WorldMap.SIZE;
            int currentCol = current % WorldMap.SIZE;
            for (int direction = 0; direction < ROW_STEP.length; direction++) {
                int nextRow = currentRow + ROW_STEP[direction];
                int nextCol = currentCol + COL_STEP[direction];
                if (!map.isWalkable(nextRow, nextCol)) {
                    continue;
                }
                int next = encode(nextRow, nextCol);
                if (visited[next]) {
                    continue;
                }
                visited[next] = true;
                parent[next] = current;
                open.addLast(next);
            }
        }

        if (!visited[goal]) {
            return false;
        }
        ArrayDeque<Integer> reversed = new ArrayDeque<>();
        for (int cursor = goal; cursor != start; cursor = parent[cursor]) {
            reversed.addFirst(cursor);
        }
        path.clear();
        if (pendingCell >= 0) {
            path.add(pendingCell);
        }
        path.addAll(reversed);
        return true;
    }

    void update(float elapsedSeconds) {
        if (state != State.PLAYING || path.isEmpty()) {
            return;
        }
        float remaining = MOVE_SPEED * Math.min(Math.max(elapsedSeconds, 0f), 0.1f);
        while (remaining > 0f && !path.isEmpty() && state == State.PLAYING) {
            int next = path.peekFirst();
            int nextRow = next / WorldMap.SIZE;
            int nextCol = next % WorldMap.SIZE;
            float deltaRow = nextRow - visualRow;
            float deltaCol = nextCol - visualCol;
            float distance = (float) Math.hypot(deltaRow, deltaCol);
            if (deltaCol != 0f) {
                facing = Math.signum(deltaCol);
            }
            if (distance <= remaining + 0.0001f) {
                visualRow = nextRow;
                visualCol = nextCol;
                row = nextRow;
                col = nextCol;
                remaining -= distance;
                path.removeFirst();
                map.revealAround(row, col);
            } else {
                visualRow += deltaRow / distance * remaining;
                visualCol += deltaCol / distance * remaining;
                remaining = 0f;
            }
        }
    }

    boolean selectActiveTech(int techId) {
        if (techId < 0 || techId >= TechNode.COUNT || techUnlocked[techId]) {
            return false;
        }
        for (int prereq : techNodes[techId].prerequisites) {
            if (!techUnlocked[prereq]) {
                return false;
            }
        }
        activeTechId = techId;
        return true;
    }

    void endTurn() {
        if (state != State.PLAYING) {
            return;
        }
        turn++;

        int[] delta = TurnMath.computeTurnDelta(buildings, buildingTypes, techUnlocked, techNodes, resources);
        int cap = TurnMath.computeStorageCap(buildings, buildingTypes);
        for (int r = 0; r < ResourceType.COUNT; r++) {
            resources[r] = TurnMath.clamp(resources[r] + delta[r], 0, cap);
        }

        int foodUpkeep = TurnMath.computeFoodUpkeep(population);
        if (resources[ResourceType.FISH] >= foodUpkeep) {
            resources[ResourceType.FISH] -= foodUpkeep;
            int housingCap = TurnMath.computeHousingCap(buildings, buildingTypes);
            int growthThreshold = TurnMath.computePopulationGrowthThreshold(
                    buildings, buildingTypes, techUnlocked, techNodes);
            if (population < housingCap && resources[ResourceType.FISH] >= growthThreshold) {
                population++;
                if (listener != null) {
                    listener.onPopulationGrew(population);
                }
            }
        }

        techPointPool += TurnMath.computeTechPointsPerTurn(buildings, buildingTypes);
        if (activeTechId != TechNode.NONE) {
            TechNode active = techNodes[activeTechId];
            if (techPointPool >= active.cost) {
                techPointPool -= active.cost;
                techUnlocked[activeTechId] = true;
                int unlockedTech = activeTechId;
                activeTechId = TechNode.NONE;
                if (listener != null) {
                    listener.onTechUnlocked(unlockedTech);
                }
            }
        }

        for (PlacedBuilding building : buildings) {
            if (building.turnsRemaining > 0) {
                building.turnsRemaining--;
                if (building.turnsRemaining == 0 && listener != null) {
                    listener.onBuildingCompleted(building.typeId, building.row, building.col);
                }
            }
        }

        if (listener != null) {
            listener.onTurnResolved(delta);
        }
    }

    KingdomSaveData snapshot() {
        KingdomSaveData data = new KingdomSaveData();
        data.turn = turn;
        data.kittenRow = row;
        data.kittenCol = col;
        data.population = population;
        data.techPointPool = techPointPool;
        data.activeTechId = activeTechId;
        data.techUnlocked = techUnlocked.clone();
        data.resources = resources.clone();
        data.buildings = new ArrayList<>(buildings.size());
        for (PlacedBuilding building : buildings) {
            data.buildings.add(new int[]{building.typeId, building.row, building.col, building.turnsRemaining});
        }
        data.explored = map.exploredSnapshot();
        return data;
    }

    private void restore(KingdomSaveData data) {
        map = new WorldMap();
        map.restoreExplored(data.explored);
        buildings.clear();
        for (int[] entry : data.buildings) {
            int typeId = entry[0];
            int buildingRow = entry[1];
            int buildingCol = entry[2];
            int turnsRemaining = entry[3];
            if (typeId < 0 || typeId >= BuildingType.COUNT || !map.inBounds(buildingRow, buildingCol)) {
                continue;
            }
            buildings.add(new PlacedBuilding(typeId, buildingRow, buildingCol, turnsRemaining));
            map.markOccupied(buildingRow, buildingCol);
        }
        for (int r = 0; r < ResourceType.COUNT && r < data.resources.length; r++) {
            resources[r] = Math.max(0, data.resources[r]);
        }
        population = Math.max(0, data.population);
        techPointPool = Math.max(0, data.techPointPool);
        activeTechId = (data.activeTechId >= 0 && data.activeTechId < TechNode.COUNT)
                ? data.activeTechId : TechNode.NONE;
        for (int i = 0; i < TechNode.COUNT && i < data.techUnlocked.length; i++) {
            techUnlocked[i] = data.techUnlocked[i];
        }
        turn = Math.max(0, data.turn);
        row = clampCoord(data.kittenRow);
        col = clampCoord(data.kittenCol);
        visualRow = row;
        visualCol = col;
        facing = 1f;
        path.clear();
        pendingBuildingTypeId = BuildingType.NONE;
    }

    private static int clampCoord(int value) {
        return Math.max(0, Math.min(WorldMap.SIZE - 1, value));
    }

    private static int encode(int encodedRow, int encodedCol) {
        return encodedRow * WorldMap.SIZE + encodedCol;
    }

    State getState() {
        return state;
    }

    WorldMap getMap() {
        return map;
    }

    BuildingType[] getBuildingTypes() {
        return buildingTypes;
    }

    TechNode[] getTechNodes() {
        return techNodes;
    }

    List<PlacedBuilding> getBuildings() {
        return Collections.unmodifiableList(buildings);
    }

    int getTurn() {
        return turn;
    }

    int getPopulation() {
        return population;
    }

    int getHousingCap() {
        return TurnMath.computeHousingCap(buildings, buildingTypes);
    }

    int getResourceCap() {
        return TurnMath.computeStorageCap(buildings, buildingTypes);
    }

    int getResource(int resourceId) {
        return resources[resourceId];
    }

    int getTechPointPool() {
        return techPointPool;
    }

    int getActiveTechId() {
        return activeTechId;
    }

    boolean isTechUnlocked(int techId) {
        return techUnlocked[techId];
    }

    int getPendingBuildingTypeId() {
        return pendingBuildingTypeId;
    }

    int getKittenRow() {
        return row;
    }

    int getKittenCol() {
        return col;
    }

    float getVisualRow() {
        return visualRow;
    }

    float getVisualCol() {
        return visualCol;
    }

    float getFacing() {
        return facing;
    }

    int getPathLength() {
        return path.size();
    }
}
