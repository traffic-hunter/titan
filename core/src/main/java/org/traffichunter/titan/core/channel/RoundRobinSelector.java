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
package org.traffichunter.titan.core.channel;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yun
 */
public class RoundRobinSelector<E> {

    private final List<E> group;
    private final AtomicInteger counter = new AtomicInteger();

    public RoundRobinSelector(final List<E> group) {
        this.group = group;
    }

    public E next() {
        final int adjustIdx = adjustSignedArrayIndex(counter.getAndIncrement(), group.size());

        E e = group.get(adjustIdx);
        if(e == null) {
            throw new NoSuchElementException("No more elements");
        }

        return e;
    }

    public E peek() {
        return group.get(currentIdx());
    }

    public List<E> group() {
        return group;
    }

    public int currentIdx() {
        return adjustSignedArrayIndex(counter.get(), group.size());
    }

    private static int adjustSignedArrayIndex(final int idx, final int size) {
        return (idx & Integer.MAX_VALUE) % size;
    }
}