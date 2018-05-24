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

import java.math.RoundingMode;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.google.common.base.Preconditions;
import com.google.common.hash.Funnel;
//import com.google.common.hash.HashFunction;
//import com.google.common.hash.Hashing;
import com.google.common.math.IntMath;

/**
 * A fixed size space efficient cache by implementing an "inverse" bloom filter.
 * The cache will never return false positives but may return false negatives.
 * <p>
 * Its original idea comes from Jeff Hodges {@link https://www.somethingsimilar.com/2012/05/21/the-opposite-of-a-bloom-filter/}
 * with changed hash function and optimizations for arbitrary object handling.
 *
 * @param <T>
 */

public class MarkerCache<T> {

//    private static final HashFunction HASH_FUNC = Hashing.murmur3_128();

    private final Funnel<T> funnel;

    private final int mask;
    private final AtomicReferenceArray<T> data;

    public MarkerCache(final Funnel<T> funnel,
                       final int size) {
        Preconditions.checkNotNull(funnel);
        Preconditions.checkArgument(size > 0);

        // Round size to next power of two
        final int poweredSize = IntMath.pow(2, IntMath.log2(size, RoundingMode.CEILING));

        this.funnel = funnel;

        this.mask = poweredSize - 1;
        this.data = new AtomicReferenceArray<>(poweredSize);
    }


    public boolean checkAndAdd(T object) {
        final T old = this.data.getAndSet(index(object), object);
        return Objects.equals(object, old);
    }

    public boolean check(T object) {
        final T old = this.data.get(index(object));
        return Objects.equals(object, old);
    }

    public int getSize() {
        return this.data.length();
    }

    private int index(final T object) {
//        final long hash64 = HASH_FUNC.hashObject(object, this.funnel).asLong();
//
//        final int hash32 = (int) ((hash64 >>> 0) + (hash64 >>> 32));
//        return (hash32 > 0)
//                ? hash32 & this.mask
//                : ~hash32 & this.mask;

        return object.hashCode() & this.mask;
    }
}
