/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ResourceTypeTest {
    @Test
    public void registryContainsExactlyFiveOrderedEntries() {
        ResourceType[] all = ResourceType.createAll();
        assertEquals(ResourceType.COUNT, all.length);
        for (int i = 0; i < all.length; i++) {
            assertEquals(i, all[i].id);
        }
    }
}
