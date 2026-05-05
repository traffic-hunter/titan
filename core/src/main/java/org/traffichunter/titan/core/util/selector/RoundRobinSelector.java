/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.util.selector;

import org.jspecify.annotations.NullUnmarked;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
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
