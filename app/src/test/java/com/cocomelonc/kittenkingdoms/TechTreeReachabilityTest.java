/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class TechTreeReachabilityTest {
    @Test
    public void registryContainsExactlyNineOrderedEntries() {
        TechNode[] all = TechNode.createAll();
        assertEquals(TechNode.COUNT, all.length);
        for (int i = 0; i < all.length; i++) {
            assertEquals(i, all[i].id);
        }
    }

    @Test
    public void everyPrerequisiteReferencesAnExistingNode() {
        TechNode[] all = TechNode.createAll();
        for (TechNode node : all) {
            for (int prereq : node.prerequisites) {
                assertTrue(prereq >= 0 && prereq < all.length);
            }
        }
    }

    @Test
    public void techTreeIsAcyclic() {
        TechNode[] all = TechNode.createAll();
        int[] inDegree = new int[all.length];
        for (TechNode node : all) {
            inDegree[node.id] = node.prerequisites.length;
        }
        List<List<Integer>> unlocks = buildUnlocksAdjacency(all);

        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int i = 0; i < inDegree.length; i++) {
            if (inDegree[i] == 0) {
                ready.add(i);
            }
        }
        int processed = 0;
        while (!ready.isEmpty()) {
            int current = ready.removeFirst();
            processed++;
            for (int next : unlocks.get(current)) {
                inDegree[next]--;
                if (inDegree[next] == 0) {
                    ready.add(next);
                }
            }
        }
        assertEquals("Tech tree contains a cycle", all.length, processed);
    }

    @Test
    public void everyNodeIsReachableFromARootNode() {
        TechNode[] all = TechNode.createAll();
        List<List<Integer>> unlocks = buildUnlocksAdjacency(all);
        boolean[] reached = new boolean[all.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (TechNode node : all) {
            if (node.prerequisites.length == 0) {
                reached[node.id] = true;
                queue.add(node.id);
            }
        }
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next : unlocks.get(current)) {
                if (!reached[next]) {
                    reached[next] = true;
                    queue.add(next);
                }
            }
        }
        for (boolean nodeReached : reached) {
            assertTrue("Tech tree has an unreachable node", nodeReached);
        }
    }

    @Test
    public void rootNodeHasNoPrerequisitesToMeet() {
        TechNode[] all = TechNode.createAll();
        boolean[] noneUnlocked = new boolean[all.length];
        assertTrue(TechNode.prerequisitesMet(all[TechNode.BASIC_TOOLS], noneUnlocked));
    }

    @Test
    public void nodeWithUnmetPrerequisiteIsNotAvailable() {
        TechNode[] all = TechNode.createAll();
        boolean[] noneUnlocked = new boolean[all.length];
        assertFalse(TechNode.prerequisitesMet(all[TechNode.CURIOUS_MINDS], noneUnlocked));
    }

    @Test
    public void nodeBecomesAvailableOnceAllPrerequisitesUnlock() {
        TechNode[] all = TechNode.createAll();
        boolean[] unlocked = new boolean[all.length];
        unlocked[TechNode.FISHING_NETS] = true;
        assertFalse(TechNode.prerequisitesMet(all[TechNode.CURIOUS_MINDS], unlocked));
        unlocked[TechNode.TEXTILE_CRAFT] = true;
        assertTrue(TechNode.prerequisitesMet(all[TechNode.CURIOUS_MINDS], unlocked));
    }

    private static List<List<Integer>> buildUnlocksAdjacency(TechNode[] all) {
        List<List<Integer>> unlocks = new ArrayList<>();
        for (int i = 0; i < all.length; i++) {
            unlocks.add(new ArrayList<>());
        }
        for (TechNode node : all) {
            for (int prereq : node.prerequisites) {
                unlocks.get(prereq).add(node.id);
            }
        }
        return unlocks;
    }
}
