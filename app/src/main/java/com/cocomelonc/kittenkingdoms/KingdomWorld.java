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
 * Pure-Java kingdom state and rules: continuous exploration and worker logistics over a
 * turn-based economy. Production becomes stockpiled only after a visible kitten delivers it.
 */
final class KingdomWorld {
    enum State {
        TITLE,
        PLAYING,
        PAUSED
    }

    static final int PLACEMENT_OK = 0;
    static final int PLACEMENT_REJECTED_UNBUILDABLE = 1;
    static final int PLACEMENT_REJECTED_TECH = 2;
    static final int PLACEMENT_REJECTED_TERRAIN = 3;
    static final int PLACEMENT_REJECTED_COST = 4;
    static final int PLACEMENT_REJECTED_PROGRESSION = 5;
    static final int WORKFORCE_OK = 0;
    static final int WORKFORCE_NO_KITTEN = 1;
    static final int WORKFORCE_NEEDS_RESOURCES = 2;
    static final int WORKFORCE_POPULATION_LIMIT = 3;
    static final int WORKFORCE_INVALID = 4;
    static final int DIRECTION_DOWN = 0;
    static final int DIRECTION_LEFT = 1;
    static final int DIRECTION_RIGHT = 2;
    static final int DIRECTION_UP = 3;

    interface Listener {
        void onTurnResolved(int[] resourceDeltas);

        void onBuildingCompleted(int buildingTypeId, int row, int col);

        void onTechUnlocked(int techId);

        void onPopulationGrew(int newPopulation);

        void onDiplomacyEvent(int settlementId, int eventMask);

        void onGoodsReady(int buildingId, int resourceId, int amount);

        void onGoodsDelivered(int workerId, int resourceId, int amount);

        void onWorkerHired(int workerId);
    }

    private static final float MOVE_SPEED = 4.2f;
    private static final float WORKER_MOVE_SPEED = 3.35f;
    private static final float CONSTRUCTION_STEP_SECONDS = 1.35f;
    private static final float COLLECTION_SECONDS = 0.8f;
    static final int HIRE_FISH_COST = 5;
    static final int HIRE_CATNIP_COST = 2;
    private static final int STARTING_WOOD = 35;
    private static final int STARTING_STONE = 25;
    private static final int STARTING_POPULATION = 2;
    private static final int STARTING_WORKERS = 2;
    private static final int[] ROW_STEP = {-1, 0, 1, 0};
    private static final int[] COL_STEP = {0, 1, 0, -1};

    private final Listener listener;
    private final BuildingType[] buildingTypes;
    private final TechNode[] techNodes;
    private final DiplomacySystem diplomacy = new DiplomacySystem();
    private final List<PlacedBuilding> buildings = new ArrayList<>();
    private final List<WorkerKitten> workers = new ArrayList<>();
    private final int[] resources = new int[ResourceType.COUNT];
    private final boolean[] techUnlocked = new boolean[TechNode.COUNT];
    private final int[] envoyWorkerIds = new int[Settlement.COUNT];
    private final int[] courierWorkerIds = new int[Settlement.COUNT];
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
    private int facingDirection = DIRECTION_DOWN;
    private int nextBuildingId;
    private int nextWorkerId;

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
        workers.clear();
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
        facingDirection = DIRECTION_DOWN;
        path.clear();
        pendingBuildingTypeId = BuildingType.NONE;
        diplomacy.reset();
        Arrays.fill(envoyWorkerIds, BuildingType.NONE);
        Arrays.fill(courierWorkerIds, BuildingType.NONE);
        nextBuildingId = 0;
        nextWorkerId = 0;
        buildings.add(new PlacedBuilding(nextBuildingId++, BuildingType.TOWN_HALL, row, col, 0));
        map.markOccupied(row, col);
        map.revealAround(row, col);
        for (int workerIndex = 0; workerIndex < STARTING_WORKERS; workerIndex++) {
            int[] workerHome = findOpenCellBeside(row, col);
            workers.add(new WorkerKitten(nextWorkerId++, workerHome[0], workerHome[1]));
        }
    }

    void setResourceForTest(int resourceId, int amount) {
        resources[resourceId] = amount;
    }

    void setPopulationForTest(int amount) {
        population = Math.max(0, amount);
    }

    void placeBuildingForTest(int typeId, int atRow, int atCol, int turnsRemaining) {
        buildings.add(new PlacedBuilding(nextBuildingId++, typeId, atRow, atCol, turnsRemaining));
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
        if (buildingTypeId < 0 || buildingTypeId >= BuildingType.COUNT
                || !isBuildingUnlocked(buildingTypeId)) {
            return false;
        }
        BuildingType type = buildingTypes[buildingTypeId];
        for (int r = 0; r < ResourceType.COUNT; r++) {
            if (resources[r] < type.cost[r]) {
                return false;
            }
        }
        return true;
    }

    boolean isBuildingUnlocked(int buildingTypeId) {
        if (buildingTypeId < 0 || buildingTypeId >= BuildingType.COUNT) {
            return false;
        }
        BuildingType type = buildingTypes[buildingTypeId];
        if (type.requiredTechId != TechNode.NONE && !techUnlocked[type.requiredTechId]) {
            return false;
        }
        if (turn < type.minTurn || population < type.minPopulation) {
            return false;
        }
        return type.requiredBuildingTypeId == BuildingType.NONE
                || countCompletedBuildings(type.requiredBuildingTypeId) >= type.requiredBuildingCount;
    }

    boolean canPlaceBuildingAt(int buildingTypeId, int targetRow, int targetCol) {
        return checkPlacement(buildingTypeId, targetRow, targetCol) == PLACEMENT_OK;
    }

    /** Reason a placement would fail, so the UI can explain a rejected tap instead of ignoring it. */
    int checkPlacement(int buildingTypeId, int targetRow, int targetCol) {
        if (buildingTypeId < 0 || buildingTypeId >= BuildingType.COUNT) {
            return PLACEMENT_REJECTED_UNBUILDABLE;
        }
        BuildingType type = buildingTypes[buildingTypeId];
        if (!map.isExplored(targetRow, targetCol) || !map.isBuildable(targetRow, targetCol)) {
            return PLACEMENT_REJECTED_UNBUILDABLE;
        }
        if (targetRow == row && targetCol == col) {
            return PLACEMENT_REJECTED_UNBUILDABLE;
        }
        if (!map.canOccupyWithoutBlockingRoutes(targetRow, targetCol)) {
            return PLACEMENT_REJECTED_UNBUILDABLE;
        }
        if (!hasAccessibleApproach(targetRow, targetCol)) {
            return PLACEMENT_REJECTED_UNBUILDABLE;
        }
        if (type.requiredTechId != TechNode.NONE && !techUnlocked[type.requiredTechId]) {
            return PLACEMENT_REJECTED_TECH;
        }
        if (!isBuildingUnlocked(buildingTypeId)) {
            return PLACEMENT_REJECTED_PROGRESSION;
        }
        if (type.requiredAdjacentTerrain != TerrainType.NONE
                && !map.hasAdjacentTerrain(targetRow, targetCol, type.requiredAdjacentTerrain)) {
            return PLACEMENT_REJECTED_TERRAIN;
        }
        if (!canAffordBuilding(buildingTypeId)) {
            return PLACEMENT_REJECTED_COST;
        }
        return PLACEMENT_OK;
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
        PlacedBuilding building = new PlacedBuilding(
                nextBuildingId++, type.id, targetRow, targetCol, type.buildTurns);
        buildings.add(building);
        map.markOccupied(targetRow, targetCol);
        moveWorkersOffNewSite(targetRow, targetCol);
        pendingBuildingTypeId = BuildingType.NONE;
        dispatchWaitingConstruction();
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
        if (state != State.PLAYING) {
            return;
        }
        float clampedElapsed = Math.min(Math.max(elapsedSeconds, 0f), 0.1f);
        updateExplorer(clampedElapsed);
        updateWorkers(clampedElapsed);
    }

    private void updateExplorer(float elapsedSeconds) {
        if (path.isEmpty()) {
            return;
        }
        float remaining = MOVE_SPEED * elapsedSeconds;
        while (remaining > 0f && !path.isEmpty() && state == State.PLAYING) {
            int next = path.peekFirst();
            int nextRow = next / WorldMap.SIZE;
            int nextCol = next % WorldMap.SIZE;
            if (!map.isWalkable(nextRow, nextCol)) {
                path.clear();
                return;
            }
            float deltaRow = nextRow - visualRow;
            float deltaCol = nextCol - visualCol;
            float distance = (float) Math.hypot(deltaRow, deltaCol);
            if (Math.abs(deltaCol) > Math.abs(deltaRow)) {
                facingDirection = deltaCol < 0f ? DIRECTION_LEFT : DIRECTION_RIGHT;
            } else if (deltaRow != 0f) {
                facingDirection = deltaRow < 0f ? DIRECTION_UP : DIRECTION_DOWN;
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

    private void updateWorkers(float elapsedSeconds) {
        dispatchWaitingConstruction();
        for (WorkerKitten worker : workers) {
            if (worker.state == WorkerKitten.DIPLOMACY) {
                continue;
            }
            if (worker.isMoving()) {
                advanceWorker(worker, elapsedSeconds);
            }
            if (!worker.isMoving()) {
                updateWorkerAtDestination(worker, elapsedSeconds);
            }
        }
    }

    private void advanceWorker(WorkerKitten worker, float elapsedSeconds) {
        float remaining = WORKER_MOVE_SPEED * elapsedSeconds;
        while (remaining > 0f && !worker.path.isEmpty()) {
            int next = worker.path.peekFirst();
            int nextRow = next / WorldMap.SIZE;
            int nextCol = next % WorldMap.SIZE;
            if (!map.isWalkable(nextRow, nextCol)) {
                worker.path.clear();
                rerouteWorker(worker);
                return;
            }
            float deltaRow = nextRow - worker.visualRow;
            float deltaCol = nextCol - worker.visualCol;
            float distance = (float) Math.hypot(deltaRow, deltaCol);
            if (Math.abs(deltaCol) > Math.abs(deltaRow)) {
                worker.facingDirection = deltaCol < 0f ? DIRECTION_LEFT : DIRECTION_RIGHT;
            } else if (deltaRow != 0f) {
                worker.facingDirection = deltaRow < 0f ? DIRECTION_UP : DIRECTION_DOWN;
            }
            if (distance <= remaining + 0.0001f) {
                worker.visualRow = nextRow;
                worker.visualCol = nextCol;
                worker.row = nextRow;
                worker.col = nextCol;
                remaining -= distance;
                worker.path.removeFirst();
            } else {
                worker.visualRow += deltaRow / distance * remaining;
                worker.visualCol += deltaCol / distance * remaining;
                remaining = 0f;
            }
        }
    }

    private void moveWorkersOffNewSite(int siteRow, int siteCol) {
        for (WorkerKitten worker : workers) {
            if (worker.row != siteRow || worker.col != siteCol) {
                continue;
            }
            int[] open = findOpenCellBeside(siteRow, siteCol);
            worker.row = open[0];
            worker.col = open[1];
            worker.visualRow = open[0];
            worker.visualCol = open[1];
            worker.path.clear();
            rerouteWorker(worker);
        }
    }

    private void rerouteWorker(WorkerKitten worker) {
        PlacedBuilding target;
        switch (worker.state) {
            case WorkerKitten.TO_CONSTRUCTION:
                target = findBuilding(worker.taskBuildingId);
                if (target != null) {
                    worker.path.addAll(GridPathfinder.besideCell(
                            map, worker.row, worker.col, target.row, target.col));
                }
                break;
            case WorkerKitten.TO_WORK:
                target = findBuilding(worker.assignedBuildingId);
                if (target != null) {
                    worker.path.addAll(GridPathfinder.besideCell(
                            map, worker.row, worker.col, target.row, target.col));
                }
                break;
            case WorkerKitten.TO_STORAGE:
                routeWorkerToStorage(worker);
                break;
            default:
                break;
        }
    }

    private void updateWorkerAtDestination(WorkerKitten worker, float elapsedSeconds) {
        switch (worker.state) {
            case WorkerKitten.TO_CONSTRUCTION:
                worker.state = WorkerKitten.CONSTRUCTING;
                worker.actionTimer = 0f;
                break;
            case WorkerKitten.CONSTRUCTING:
                workOnConstruction(worker, elapsedSeconds);
                break;
            case WorkerKitten.TO_WORK:
                worker.state = WorkerKitten.WORKING;
                worker.actionTimer = 0f;
                break;
            case WorkerKitten.WORKING:
                beginCollectionIfReady(worker);
                break;
            case WorkerKitten.COLLECTING:
                collectGoods(worker, elapsedSeconds);
                break;
            case WorkerKitten.TO_STORAGE:
                depositGoods(worker);
                break;
            default:
                break;
        }
    }

    private void workOnConstruction(WorkerKitten worker, float elapsedSeconds) {
        PlacedBuilding building = findBuilding(worker.taskBuildingId);
        if (building == null || building.isComplete()) {
            finishConstructionJob(worker, building);
            return;
        }
        worker.actionTimer += elapsedSeconds;
        building.constructionTimer += elapsedSeconds;
        while (building.constructionTimer >= CONSTRUCTION_STEP_SECONDS && building.turnsRemaining > 0) {
            building.constructionTimer -= CONSTRUCTION_STEP_SECONDS;
            building.turnsRemaining--;
        }
        if (building.isComplete()) {
            if (listener != null) {
                listener.onBuildingCompleted(building.typeId, building.row, building.col);
            }
            finishConstructionJob(worker, building);
        }
    }

    private void finishConstructionJob(WorkerKitten worker, PlacedBuilding building) {
        worker.taskBuildingId = BuildingType.NONE;
        worker.actionTimer = 0f;
        if (building != null && outputResourceId(building.typeId) != ResourceType.NONE) {
            worker.assignedBuildingId = building.id;
            worker.state = WorkerKitten.WORKING;
        } else {
            worker.state = WorkerKitten.IDLE;
        }
    }

    private void beginCollectionIfReady(WorkerKitten worker) {
        PlacedBuilding building = findBuilding(worker.assignedBuildingId);
        if (building == null || !building.isComplete()) {
            releaseWorker(worker);
            return;
        }
        if (building.hasReadyGoods()
                && availableStorageRoom(building.pendingResourceId) > 0) {
            worker.state = WorkerKitten.COLLECTING;
            worker.actionTimer = 0f;
        }
    }

    private void collectGoods(WorkerKitten worker, float elapsedSeconds) {
        PlacedBuilding building = findBuilding(worker.assignedBuildingId);
        if (building == null) {
            releaseWorker(worker);
            return;
        }
        worker.actionTimer += elapsedSeconds;
        if (worker.actionTimer < COLLECTION_SECONDS || !building.hasReadyGoods()) {
            return;
        }
        int amount = Math.min(building.pendingAmount,
                availableStorageRoom(building.pendingResourceId));
        if (amount <= 0) {
            worker.state = WorkerKitten.WORKING;
            worker.actionTimer = 0f;
            return;
        }
        worker.carriedResourceId = building.pendingResourceId;
        worker.carriedAmount = amount;
        worker.cargoSourceBuildingId = building.id;
        worker.releaseAfterDelivery = false;
        building.pendingAmount -= amount;
        if (building.pendingAmount == 0) {
            building.pendingResourceId = ResourceType.NONE;
        }
        worker.actionTimer = 0f;
        routeWorkerToStorage(worker);
    }

    private void depositGoods(WorkerKitten worker) {
        if (worker.carriedResourceId == ResourceType.NONE || worker.carriedAmount <= 0) {
            finishCargoJob(worker);
            return;
        }
        int room = availableStorageRoom(worker.carriedResourceId);
        int delivered = Math.min(room, worker.carriedAmount);
        if (delivered > 0) {
            resources[worker.carriedResourceId] += delivered;
            worker.carriedAmount -= delivered;
            if (listener != null) {
                listener.onGoodsDelivered(worker.id, worker.carriedResourceId, delivered);
            }
        }
        if (worker.carriedAmount > 0) {
            returnCargoToSource(worker);
        }
        finishCargoJob(worker);
    }

    private void returnWorkerToAssignment(WorkerKitten worker) {
        if (worker.releaseAfterDelivery || worker.assignedBuildingId == BuildingType.NONE) {
            makeWorkerIdle(worker);
            return;
        }
        PlacedBuilding assigned = findBuilding(worker.assignedBuildingId);
        if (assigned == null || !assigned.isComplete()) {
            makeWorkerIdle(worker);
            return;
        }
        worker.state = WorkerKitten.TO_WORK;
        worker.path.clear();
        worker.path.addAll(GridPathfinder.besideCell(map, worker.row, worker.col,
                assigned.row, assigned.col));
        if (worker.path.isEmpty() && !isBeside(worker.row, worker.col, assigned.row, assigned.col)) {
            makeWorkerIdle(worker);
        }
    }

    private void routeWorkerToStorage(WorkerKitten worker) {
        PlacedBuilding depot = findNearestDepot(worker.row, worker.col);
        if (depot == null) {
            returnCargoToSource(worker);
            finishCargoJob(worker);
            return;
        }
        worker.state = WorkerKitten.TO_STORAGE;
        worker.path.clear();
        worker.path.addAll(GridPathfinder.besideCell(map, worker.row, worker.col, depot.row, depot.col));
    }

    private int availableStorageRoom(int resourceId) {
        if (resourceId < 0 || resourceId >= ResourceType.COUNT) {
            return 0;
        }
        return Math.max(0, getResourceCap() - resources[resourceId]);
    }

    private void returnCargoToSource(WorkerKitten worker) {
        if (worker.carriedResourceId < 0 || worker.carriedResourceId >= ResourceType.COUNT
                || worker.carriedAmount <= 0) {
            return;
        }
        PlacedBuilding source = findCompatibleCargoSource(
                worker.cargoSourceBuildingId, worker.carriedResourceId);
        if (source == null) {
            source = findCompatibleCargoSource(
                    worker.assignedBuildingId, worker.carriedResourceId);
        }
        if (source == null) {
            for (PlacedBuilding building : buildings) {
                if (building.isComplete()
                        && outputResourceId(building.typeId) == worker.carriedResourceId
                        && (building.pendingResourceId == ResourceType.NONE
                        || building.pendingResourceId == worker.carriedResourceId)) {
                    source = building;
                    break;
                }
            }
        }
        if (source != null) {
            source.pendingResourceId = worker.carriedResourceId;
            source.pendingAmount += worker.carriedAmount;
        } else {
            // Recovery for an old/corrupt save without the original workshop: never lose cargo
            // and never leave a worker permanently stuck looking for a depot.
            resources[worker.carriedResourceId] += worker.carriedAmount;
        }
        worker.carriedAmount = 0;
    }

    private PlacedBuilding findCompatibleCargoSource(int buildingId, int resourceId) {
        PlacedBuilding building = findBuilding(buildingId);
        if (building == null || !building.isComplete()
                || outputResourceId(building.typeId) != resourceId) {
            return null;
        }
        return building.pendingResourceId == ResourceType.NONE
                || building.pendingResourceId == resourceId ? building : null;
    }

    private void finishCargoJob(WorkerKitten worker) {
        worker.carriedResourceId = ResourceType.NONE;
        worker.carriedAmount = 0;
        worker.cargoSourceBuildingId = BuildingType.NONE;
        if (worker.releaseAfterDelivery || worker.assignedBuildingId == BuildingType.NONE) {
            makeWorkerIdle(worker);
        } else {
            worker.releaseAfterDelivery = false;
            returnWorkerToAssignment(worker);
        }
    }

    private void makeWorkerIdle(WorkerKitten worker) {
        worker.assignedBuildingId = BuildingType.NONE;
        worker.taskBuildingId = BuildingType.NONE;
        worker.carriedResourceId = ResourceType.NONE;
        worker.carriedAmount = 0;
        worker.cargoSourceBuildingId = BuildingType.NONE;
        worker.releaseAfterDelivery = false;
        worker.actionTimer = 0f;
        worker.path.clear();
        worker.state = WorkerKitten.IDLE;
    }

    private PlacedBuilding findNearestDepot(int fromRow, int fromCol) {
        PlacedBuilding best = null;
        int bestLength = Integer.MAX_VALUE;
        for (PlacedBuilding building : buildings) {
            if (!building.isComplete()
                    || buildingTypes[building.typeId].storageCapBonus <= 0) {
                continue;
            }
            ArrayDeque<Integer> route = GridPathfinder.besideCell(
                    map, fromRow, fromCol, building.row, building.col);
            if (route.isEmpty() && !isBeside(fromRow, fromCol, building.row, building.col)) {
                continue;
            }
            if (route.size() < bestLength) {
                best = building;
                bestLength = route.size();
            }
        }
        return best;
    }

    private void dispatchWaitingConstruction() {
        for (PlacedBuilding building : buildings) {
            if (building.isComplete() || constructionWorker(building.id) != null) {
                continue;
            }
            WorkerKitten worker = firstIdleWorker();
            if (worker == null) {
                return;
            }
            ArrayDeque<Integer> route = GridPathfinder.besideCell(
                    map, worker.row, worker.col, building.row, building.col);
            if (route.isEmpty() && !isBeside(worker.row, worker.col, building.row, building.col)) {
                continue;
            }
            worker.taskBuildingId = building.id;
            worker.state = WorkerKitten.TO_CONSTRUCTION;
            worker.path.clear();
            worker.path.addAll(route);
        }
    }

    private WorkerKitten firstIdleWorker() {
        for (WorkerKitten worker : workers) {
            if (worker.isIdleAndUnassigned()) {
                return worker;
            }
        }
        return null;
    }

    private WorkerKitten constructionWorker(int buildingId) {
        for (WorkerKitten worker : workers) {
            if (worker.taskBuildingId == buildingId) {
                return worker;
            }
        }
        return null;
    }

    boolean selectActiveTech(int techId) {
        if (techId < 0 || techId >= TechNode.COUNT || techUnlocked[techId]) {
            return false;
        }
        if (!TechNode.conditionsMet(techNodes[techId], techUnlocked, turn, population,
                resources, getBuildingCountSnapshot())) {
            return false;
        }
        activeTechId = techId;
        return true;
    }

    void endTurn() {
        if (state != State.PLAYING) {
            return;
        }
        turn++;

        int[] delta = new int[ResourceType.COUNT];
        int cap = TurnMath.computeStorageCap(buildings, buildingTypes);
        prepareProductionBatches(delta);

        int foodUpkeep = TurnMath.computeFoodUpkeep(population);
        if (resources[ResourceType.FISH] >= foodUpkeep) {
            resources[ResourceType.FISH] -= foodUpkeep;
            delta[ResourceType.FISH] -= foodUpkeep;
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

        DiplomacySystem.TurnReport diplomacyReport = diplomacy.advanceTurn(resources, cap);
        for (int resource = 0; resource < ResourceType.COUNT; resource++) {
            delta[resource] += diplomacyReport.resourceDelta[resource];
        }
        for (int settlement = 0; settlement < Settlement.COUNT; settlement++) {
            if ((diplomacyReport.events[settlement]
                    & DiplomacySystem.EVENT_ENVOY_RETURNED) != 0) {
                returnDiplomaticWorker(envoyWorkerIds[settlement]);
                envoyWorkerIds[settlement] = BuildingType.NONE;
            }
            if ((diplomacyReport.events[settlement]
                    & DiplomacySystem.EVENT_COURIER_RETURNED) != 0) {
                returnDiplomaticWorker(courierWorkerIds[settlement]);
                courierWorkerIds[settlement] = BuildingType.NONE;
            }
            if (listener != null) {
                if (diplomacyReport.events[settlement] != 0) {
                    listener.onDiplomacyEvent(settlement, diplomacyReport.events[settlement]);
                }
            }
        }

        if (listener != null) {
            listener.onTurnResolved(delta);
        }
    }

    private void prepareProductionBatches(int[] delta) {
        for (PlacedBuilding building : buildings) {
            if (!building.isComplete()) {
                continue;
            }
            int resourceId = outputResourceId(building.typeId);
            if (resourceId == ResourceType.NONE || assignedWorker(building.id) == null) {
                continue;
            }
            BuildingType type = buildingTypes[building.typeId];
            int amount = TurnMath.computeYield(type, techUnlocked, techNodes, resourceId);
            int previousAmount = building.pendingAmount;
            int queueCap = Math.max(amount, amount * 3);
            if (building.pendingAmount >= queueCap || !TurnMath.canAffordUpkeep(type, resources)) {
                continue;
            }
            for (int resource = 0; resource < ResourceType.COUNT; resource++) {
                int upkeep = type.upkeepPerTurn[resource];
                resources[resource] -= upkeep;
                delta[resource] -= upkeep;
            }
            building.pendingResourceId = resourceId;
            building.pendingAmount = Math.min(queueCap, building.pendingAmount + amount);
            if (previousAmount == 0 && building.pendingAmount > 0 && listener != null) {
                listener.onGoodsReady(building.id, resourceId, building.pendingAmount);
            }
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
            data.buildings.add(new int[]{building.id, building.typeId, building.row, building.col,
                    building.turnsRemaining, building.pendingResourceId, building.pendingAmount});
        }
        data.workers = new ArrayList<>(workers.size());
        for (WorkerKitten worker : workers) {
            data.workers.add(new int[]{worker.id, worker.row, worker.col, worker.assignedBuildingId,
                    worker.carriedResourceId, worker.carriedAmount, worker.cargoSourceBuildingId,
                    worker.releaseAfterDelivery ? 1 : 0});
        }
        data.explored = map.exploredSnapshot();
        data.diplomaticRelations = diplomacy.relationsSnapshot();
        data.envoyTurns = diplomacy.envoyTurnsSnapshot();
        data.courierTurns = diplomacy.courierTurnsSnapshot();
        data.tradeRoutes = diplomacy.tradeRoutesSnapshot();
        data.envoyWorkerIds = envoyWorkerIds.clone();
        data.courierWorkerIds = courierWorkerIds.clone();
        return data;
    }

    private void restore(KingdomSaveData data) {
        map = new WorldMap();
        map.restoreExplored(data.explored);
        buildings.clear();
        workers.clear();
        nextBuildingId = 0;
        nextWorkerId = 0;
        for (int[] entry : data.buildings) {
            if (entry.length < 5) {
                continue;
            }
            int buildingId = entry[0];
            int typeId = entry[1];
            int buildingRow = entry[2];
            int buildingCol = entry[3];
            int turnsRemaining = entry[4];
            if (typeId < 0 || typeId >= BuildingType.COUNT || !map.inBounds(buildingRow, buildingCol)) {
                continue;
            }
            PlacedBuilding building = new PlacedBuilding(
                    buildingId, typeId, buildingRow, buildingCol, Math.max(0, turnsRemaining));
            if (entry.length >= 7 && entry[5] >= 0 && entry[5] < ResourceType.COUNT) {
                building.pendingResourceId = entry[5];
                building.pendingAmount = Math.max(0, entry[6]);
            }
            buildings.add(building);
            nextBuildingId = Math.max(nextBuildingId, buildingId + 1);
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
        if (!map.isWalkable(row, col)) {
            int[] safeCell = findOpenCellBeside(row, col);
            row = safeCell[0];
            col = safeCell[1];
        }
        visualRow = row;
        visualCol = col;
        facingDirection = DIRECTION_DOWN;
        path.clear();
        pendingBuildingTypeId = BuildingType.NONE;
        diplomacy.restore(data.diplomaticRelations, data.envoyTurns,
                data.courierTurns, data.tradeRoutes);
        restoreWorkers(data.workers);
        restoreDiplomaticWorkers(data.envoyWorkerIds, data.courierWorkerIds);
        dispatchWaitingConstruction();
    }

    private void restoreWorkers(List<int[]> savedWorkers) {
        if (savedWorkers != null) {
            for (int[] entry : savedWorkers) {
                if (entry.length < 6) {
                    continue;
                }
                int workerId = Math.max(0, entry[0]);
                int workerRow = clampCoord(entry[1]);
                int workerCol = clampCoord(entry[2]);
                if (!map.isWalkable(workerRow, workerCol)) {
                    int[] home = findOpenCellBeside(WorldMap.START_ROW, WorldMap.START_COL);
                    workerRow = home[0];
                    workerCol = home[1];
                }
                WorkerKitten worker = new WorkerKitten(workerId, workerRow, workerCol);
                worker.assignedBuildingId = findBuilding(entry[3]) == null
                        ? BuildingType.NONE : entry[3];
                worker.cargoSourceBuildingId = entry.length >= 8
                        && findBuilding(entry[6]) != null ? entry[6] : BuildingType.NONE;
                worker.releaseAfterDelivery = entry.length >= 8 && entry[7] != 0;
                if (worker.releaseAfterDelivery) {
                    worker.assignedBuildingId = BuildingType.NONE;
                }
                if (entry[4] >= 0 && entry[4] < ResourceType.COUNT && entry[5] > 0) {
                    worker.carriedResourceId = entry[4];
                    worker.carriedAmount = entry[5];
                    if (worker.cargoSourceBuildingId == BuildingType.NONE) {
                        PlacedBuilding legacySource = findCompatibleCargoSource(
                                worker.assignedBuildingId, worker.carriedResourceId);
                        if (legacySource != null) {
                            worker.cargoSourceBuildingId = legacySource.id;
                        }
                    }
                    workers.add(worker);
                    routeWorkerToStorage(worker);
                } else {
                    workers.add(worker);
                    if (worker.releaseAfterDelivery) {
                        makeWorkerIdle(worker);
                    } else if (worker.assignedBuildingId != BuildingType.NONE) {
                        returnWorkerToAssignment(worker);
                    }
                }
                nextWorkerId = Math.max(nextWorkerId, workerId + 1);
            }
        }
        if (workers.isEmpty()) {
            int workerCount = Math.min(STARTING_WORKERS, Math.max(1, population));
            for (int index = 0; index < workerCount; index++) {
                int[] home = findOpenCellBeside(WorldMap.START_ROW, WorldMap.START_COL);
                workers.add(new WorkerKitten(nextWorkerId++, home[0], home[1]));
            }
        }
    }

    private static int clampCoord(int value) {
        return Math.max(0, Math.min(WorldMap.SIZE - 1, value));
    }

    private static int encode(int encodedRow, int encodedCol) {
        return encodedRow * WorldMap.SIZE + encodedCol;
    }

    private boolean hasAccessibleApproach(int targetRow, int targetCol) {
        if (workers.isEmpty()) {
            return false;
        }
        WorkerKitten worker = workers.get(0);
        ArrayDeque<Integer> route = GridPathfinder.besideCell(
                map, worker.row, worker.col, targetRow, targetCol);
        return !route.isEmpty() || isBeside(worker.row, worker.col, targetRow, targetCol);
    }

    private int[] findOpenCellBeside(int targetRow, int targetCol) {
        for (int direction = 0; direction < ROW_STEP.length; direction++) {
            int candidateRow = targetRow + ROW_STEP[direction];
            int candidateCol = targetCol + COL_STEP[direction];
            if (map.isWalkable(candidateRow, candidateCol)
                    && isWorkerCellFree(candidateRow, candidateCol)) {
                return new int[]{candidateRow, candidateCol};
            }
        }
        for (int radius = 2; radius < WorldMap.SIZE; radius++) {
            for (int candidateRow = Math.max(0, targetRow - radius);
                 candidateRow <= Math.min(WorldMap.SIZE - 1, targetRow + radius); candidateRow++) {
                for (int candidateCol = Math.max(0, targetCol - radius);
                     candidateCol <= Math.min(WorldMap.SIZE - 1, targetCol + radius); candidateCol++) {
                    if (map.isWalkable(candidateRow, candidateCol)
                            && isWorkerCellFree(candidateRow, candidateCol)) {
                        return new int[]{candidateRow, candidateCol};
                    }
                }
            }
        }
        return new int[]{clampCoord(targetRow), clampCoord(targetCol)};
    }

    private boolean isWorkerCellFree(int candidateRow, int candidateCol) {
        for (WorkerKitten worker : workers) {
            if (worker.state != WorkerKitten.DIPLOMACY
                    && worker.row == candidateRow && worker.col == candidateCol) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBeside(int firstRow, int firstCol, int secondRow, int secondCol) {
        return Math.abs(firstRow - secondRow) + Math.abs(firstCol - secondCol) == 1;
    }

    private PlacedBuilding findBuilding(int buildingId) {
        for (PlacedBuilding building : buildings) {
            if (building.id == buildingId) {
                return building;
            }
        }
        return null;
    }

    private WorkerKitten assignedWorker(int buildingId) {
        for (WorkerKitten worker : workers) {
            if (worker.assignedBuildingId == buildingId) {
                return worker;
            }
        }
        return null;
    }

    private int outputResourceId(int buildingTypeId) {
        BuildingType type = buildingTypes[buildingTypeId];
        for (int resource = 0; resource < ResourceType.COUNT; resource++) {
            if (type.outputPerTurn[resource] > 0) {
                return resource;
            }
        }
        return ResourceType.NONE;
    }

    private void releaseWorker(WorkerKitten worker) {
        worker.assignedBuildingId = BuildingType.NONE;
        worker.taskBuildingId = BuildingType.NONE;
        worker.actionTimer = 0f;
        if (worker.carriedAmount > 0) {
            worker.releaseAfterDelivery = true;
            routeWorkerToStorage(worker);
        } else {
            makeWorkerIdle(worker);
        }
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

    List<WorkerKitten> getWorkers() {
        return Collections.unmodifiableList(workers);
    }

    PlacedBuilding getBuildingAt(int atRow, int atCol) {
        for (PlacedBuilding building : buildings) {
            if (building.row == atRow && building.col == atCol) {
                return building;
            }
        }
        return null;
    }

    PlacedBuilding getBuilding(int buildingId) {
        return findBuilding(buildingId);
    }

    WorkerKitten getAssignedWorker(int buildingId) {
        return assignedWorker(buildingId);
    }

    WorkerKitten getConstructionWorker(int buildingId) {
        return constructionWorker(buildingId);
    }

    int getOutputResourceId(int buildingTypeId) {
        return outputResourceId(buildingTypeId);
    }

    boolean needsWorker(int buildingId) {
        PlacedBuilding building = findBuilding(buildingId);
        return building != null && building.isComplete()
                && outputResourceId(building.typeId) != ResourceType.NONE
                && assignedWorker(buildingId) == null;
    }

    int assignWorker(int buildingId) {
        PlacedBuilding building = findBuilding(buildingId);
        if (building == null || !building.isComplete()
                || outputResourceId(building.typeId) == ResourceType.NONE) {
            return WORKFORCE_INVALID;
        }
        if (assignedWorker(buildingId) != null) {
            return WORKFORCE_OK;
        }
        WorkerKitten worker = firstIdleWorker();
        if (worker == null) {
            return WORKFORCE_NO_KITTEN;
        }
        worker.assignedBuildingId = building.id;
        worker.state = WorkerKitten.TO_WORK;
        worker.path.clear();
        worker.path.addAll(GridPathfinder.besideCell(map, worker.row, worker.col,
                building.row, building.col));
        if (worker.path.isEmpty() && !isBeside(worker.row, worker.col, building.row, building.col)) {
            releaseWorker(worker);
            return WORKFORCE_INVALID;
        }
        return WORKFORCE_OK;
    }

    int unassignWorker(int buildingId) {
        WorkerKitten worker = assignedWorker(buildingId);
        if (worker == null) {
            return WORKFORCE_INVALID;
        }
        releaseWorker(worker);
        dispatchWaitingConstruction();
        return WORKFORCE_OK;
    }

    boolean canHireWorker() {
        return workers.size() < population
                && resources[ResourceType.FISH] >= HIRE_FISH_COST
                && resources[ResourceType.CATNIP] >= HIRE_CATNIP_COST;
    }

    int hireWorker() {
        if (workers.size() >= population) {
            return WORKFORCE_POPULATION_LIMIT;
        }
        if (resources[ResourceType.FISH] < HIRE_FISH_COST
                || resources[ResourceType.CATNIP] < HIRE_CATNIP_COST) {
            return WORKFORCE_NEEDS_RESOURCES;
        }
        resources[ResourceType.FISH] -= HIRE_FISH_COST;
        resources[ResourceType.CATNIP] -= HIRE_CATNIP_COST;
        PlacedBuilding hall = null;
        for (PlacedBuilding building : buildings) {
            if (building.typeId == BuildingType.TOWN_HALL) {
                hall = building;
                break;
            }
        }
        int[] home = hall == null ? findOpenCellBeside(row, col)
                : findOpenCellBeside(hall.row, hall.col);
        WorkerKitten worker = new WorkerKitten(nextWorkerId++, home[0], home[1]);
        workers.add(worker);
        dispatchWaitingConstruction();
        if (listener != null) {
            listener.onWorkerHired(worker.id);
        }
        return WORKFORCE_OK;
    }

    int getWorkerCount() {
        return workers.size();
    }

    int getIdleWorkerCount() {
        int count = 0;
        for (WorkerKitten worker : workers) {
            if (worker.isIdleAndUnassigned()) {
                count++;
            }
        }
        return count;
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

    boolean isStorageFullForResource(int resourceId) {
        return availableStorageRoom(resourceId) == 0;
    }

    int[] getResourceSnapshot() {
        return resources.clone();
    }

    int countCompletedBuildings(int typeId) {
        int count = 0;
        for (PlacedBuilding building : buildings) {
            if (building.typeId == typeId && building.isComplete()) {
                count++;
            }
        }
        return count;
    }

    int[] getBuildingCountSnapshot() {
        int[] counts = new int[BuildingType.COUNT];
        for (PlacedBuilding building : buildings) {
            if (building.isComplete()) {
                counts[building.typeId]++;
            }
        }
        return counts;
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

    /** Packed for handing tech progress to {@code TechTreeActivity} via an Intent extra. */
    int getTechUnlockedBits() {
        return KingdomSerializer.packTechBits(techUnlocked);
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

    int getFacingDirection() {
        return facingDirection;
    }

    int getPathLength() {
        return path.size();
    }

    Settlement[] getSettlements() {
        return diplomacy.getSettlements();
    }

    int getRelation(int settlementId) {
        return diplomacy.getRelation(settlementId);
    }

    int getEnvoyTurns(int settlementId) {
        return diplomacy.getEnvoyTurns(settlementId);
    }

    int getCourierTurns(int settlementId) {
        return diplomacy.getCourierTurns(settlementId);
    }

    int getEnvoyPhase(int settlementId) {
        return diplomacy.getEnvoyPhase(settlementId);
    }

    int getCourierPhase(int settlementId) {
        return diplomacy.getCourierPhase(settlementId);
    }

    float getEnvoyRouteProgress(int settlementId) {
        return diplomacy.getEnvoyRouteProgress(settlementId);
    }

    float getCourierRouteProgress(int settlementId) {
        return diplomacy.getCourierRouteProgress(settlementId);
    }

    int getEnvoyWorkerId(int settlementId) {
        return envoyWorkerIds[settlementId];
    }

    int getCourierWorkerId(int settlementId) {
        return courierWorkerIds[settlementId];
    }

    boolean hasTradeRoute(int settlementId) {
        return diplomacy.hasTradeRoute(settlementId);
    }

    int sendEnvoy(int settlementId) {
        int result = diplomacy.sendEnvoy(settlementId);
        if (result != DiplomacySystem.ACTION_OK) {
            return result;
        }
        WorkerKitten worker = firstIdleWorker();
        if (worker == null) {
            diplomacy.cancelEnvoy(settlementId);
            return DiplomacySystem.ACTION_NEEDS_WORKER;
        }
        reserveDiplomaticWorker(worker);
        envoyWorkerIds[settlementId] = worker.id;
        return DiplomacySystem.ACTION_OK;
    }

    int sendCourier(int settlementId) {
        int result = diplomacy.sendCourier(settlementId);
        if (result != DiplomacySystem.ACTION_OK) {
            return result;
        }
        WorkerKitten worker = firstIdleWorker();
        if (worker == null) {
            diplomacy.cancelCourier(settlementId);
            return DiplomacySystem.ACTION_NEEDS_WORKER;
        }
        reserveDiplomaticWorker(worker);
        courierWorkerIds[settlementId] = worker.id;
        return DiplomacySystem.ACTION_OK;
    }

    int giveGift(int settlementId) {
        return diplomacy.giveGift(settlementId, resources);
    }

    int establishTradeRoute(int settlementId) {
        return diplomacy.establishTradeRoute(settlementId, resources);
    }

    private void reserveDiplomaticWorker(WorkerKitten worker) {
        worker.path.clear();
        worker.assignedBuildingId = BuildingType.NONE;
        worker.taskBuildingId = BuildingType.NONE;
        worker.carriedResourceId = ResourceType.NONE;
        worker.carriedAmount = 0;
        worker.cargoSourceBuildingId = BuildingType.NONE;
        worker.releaseAfterDelivery = false;
        worker.actionTimer = 0f;
        worker.state = WorkerKitten.DIPLOMACY;
    }

    private void returnDiplomaticWorker(int workerId) {
        WorkerKitten worker = findWorker(workerId);
        if (worker == null) {
            return;
        }
        PlacedBuilding hall = null;
        for (PlacedBuilding building : buildings) {
            if (building.typeId == BuildingType.TOWN_HALL) {
                hall = building;
                break;
            }
        }
        int[] home = hall == null ? findOpenCellBeside(row, col)
                : findOpenCellBeside(hall.row, hall.col);
        worker.row = home[0];
        worker.col = home[1];
        worker.visualRow = home[0];
        worker.visualCol = home[1];
        makeWorkerIdle(worker);
        dispatchWaitingConstruction();
    }

    private WorkerKitten findWorker(int workerId) {
        for (WorkerKitten worker : workers) {
            if (worker.id == workerId) {
                return worker;
            }
        }
        return null;
    }

    private void restoreDiplomaticWorkers(int[] restoredEnvoyWorkers,
            int[] restoredCourierWorkers) {
        Arrays.fill(envoyWorkerIds, BuildingType.NONE);
        Arrays.fill(courierWorkerIds, BuildingType.NONE);
        for (int settlement = 0; settlement < Settlement.COUNT; settlement++) {
            if (diplomacy.getEnvoyTurns(settlement) > 0) {
                WorkerKitten worker = restoredMissionWorker(restoredEnvoyWorkers, settlement);
                if (worker == null) {
                    diplomacy.cancelEnvoy(settlement);
                } else {
                    reserveDiplomaticWorker(worker);
                    envoyWorkerIds[settlement] = worker.id;
                }
            }
            if (diplomacy.getCourierTurns(settlement) > 0) {
                WorkerKitten worker = restoredMissionWorker(restoredCourierWorkers, settlement);
                if (worker == null) {
                    diplomacy.cancelCourier(settlement);
                } else {
                    reserveDiplomaticWorker(worker);
                    courierWorkerIds[settlement] = worker.id;
                }
            }
        }
    }

    private WorkerKitten restoredMissionWorker(int[] restoredIds, int settlement) {
        if (restoredIds != null && settlement < restoredIds.length) {
            WorkerKitten restored = findWorker(restoredIds[settlement]);
            if (restored != null && restored.state != WorkerKitten.DIPLOMACY) {
                return restored;
            }
        }
        return firstIdleWorker();
    }
}
