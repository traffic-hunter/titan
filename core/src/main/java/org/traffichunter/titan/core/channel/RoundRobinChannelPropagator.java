package org.traffichunter.titan.core.channel;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class RoundRobinChannelPropagator<E> implements Iterator<E> {

    private final List<E> group;
    private int counter = 0;

    public RoundRobinChannelPropagator(final List<E> group) {
        this.group = group;
    }

    @Override
    public boolean hasNext() {
        return group.iterator().hasNext();
    }

    @Override
    public E next() {
        final int adjustIdx = adjustSignedArrayIndex(counter++, group.size());

        E e = group.get(adjustIdx);
        if(e == null) {
            throw new NoSuchElementException("No more elements");
        }

        return e;
    }

    public int currentIdx() {
        return adjustSignedArrayIndex(counter, group.size());
    }

    private static int adjustSignedArrayIndex(final int idx, final int size) {
        return (idx & Integer.MAX_VALUE) % size;
    }
}