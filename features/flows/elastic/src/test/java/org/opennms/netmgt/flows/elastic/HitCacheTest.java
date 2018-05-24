/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.flows.elastic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import com.google.common.hash.Funnels;

public class HitCacheTest {

    @Test
    public void test() {
        final MarkerCache<Integer> cache = new MarkerCache<>(Funnels.integerFunnel(), 32);

        assertFalse(cache.checkAndAdd(0));
        assertFalse(cache.checkAndAdd(1));
        assertTrue(cache.checkAndAdd(0));
        assertTrue(cache.checkAndAdd(0));
        assertFalse(cache.checkAndAdd(2));
        assertTrue(cache.checkAndAdd(2));
        assertTrue(cache.checkAndAdd(1));
        assertFalse(cache.checkAndAdd(3));
    }

    @Test
    public void testCollisions() {
        final MarkerCache<Integer> cache = new MarkerCache<>(Funnels.integerFunnel(), 16);
        assertFalse(cache.checkAndAdd(0x0));
        assertFalse(cache.checkAndAdd(0x1));
        assertFalse(cache.checkAndAdd(0x2));
        assertFalse(cache.checkAndAdd(0x3));
        assertFalse(cache.checkAndAdd(0x4));
        assertFalse(cache.checkAndAdd(0x5));
        assertFalse(cache.checkAndAdd(0x6));
        assertFalse(cache.checkAndAdd(0x7));
        assertFalse(cache.checkAndAdd(0x8));
        assertFalse(cache.checkAndAdd(0x9));
        assertFalse(cache.checkAndAdd(0xA));
        assertFalse(cache.checkAndAdd(0xB));
        assertFalse(cache.checkAndAdd(0xC));
        assertFalse(cache.checkAndAdd(0xD));
        assertFalse(cache.checkAndAdd(0xE));
        assertFalse(cache.checkAndAdd(0xF));

        assertFalse(cache.checkAndAdd(0x0));
        assertFalse(cache.checkAndAdd(0x1));
        assertTrue(cache.checkAndAdd(0x2));
        assertFalse(cache.checkAndAdd(0x3));
        assertTrue(cache.checkAndAdd(0x4));
        assertTrue(cache.checkAndAdd(0x5));
        assertTrue(cache.checkAndAdd(0x6));
        assertTrue(cache.checkAndAdd(0x7));
        assertFalse(cache.checkAndAdd(0x8));
        assertFalse(cache.checkAndAdd(0x9));
        assertFalse(cache.checkAndAdd(0xA));
        assertTrue(cache.checkAndAdd(0xB));
        assertFalse(cache.checkAndAdd(0xC));
        assertFalse(cache.checkAndAdd(0xD));
        assertFalse(cache.checkAndAdd(0xE));
        assertFalse(cache.checkAndAdd(0xF));
    }

    @Test
    public void testCollisionsEx() {
        final MarkerCache<Integer> cache = new MarkerCache<>(Funnels.integerFunnel(), 2 << 12);

        for (int i = 0; i < cache.getSize() / 4; i++) {
            assertFalse(cache.checkAndAdd(i * 17));
        }

        int t = 0, f = 0;
        for (int i = 0; i < cache.getSize() / 4; i++) {
            if (cache.check(i * 17)) {
                t++;
            } else {
                f++;
            }
        }

        System.out.println("s = " + cache.getSize());
        System.out.println("t = " + t);
        System.out.println("f = " + f);
    }
}
