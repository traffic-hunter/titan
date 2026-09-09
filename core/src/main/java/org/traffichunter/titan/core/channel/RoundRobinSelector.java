/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.core.channel;

import org.jspecify.annotations.NullUnmarked;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin selector that keeps only cursor state.
 *
 * <p>Candidates are supplied by the caller on each selection so the selector can be reused
 * with dynamic registries without owning their storage.</p>
 *
 * @author yun
 */
@NullUnmarked
public class RoundRobinSelector<E> implements Selector<E> {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public E next(List<E> candidates) {
        if (candidates.isEmpty()) {
            throw new NoSuchElementException("No more elements");
        }

        int index = adjustSignedArrayIndex(counter.getAndIncrement(), candidates.size());
        E candidate = candidates.get(index);
        if (candidate == null) {
            throw new NoSuchElementException("No more elements");
        }

        return candidate;
    }

    private static int adjustSignedArrayIndex(final int idx, final int size) {
        return (idx & Integer.MAX_VALUE) % size;
    }
}
