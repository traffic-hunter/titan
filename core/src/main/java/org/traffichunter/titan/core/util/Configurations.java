/*
 * The MIT License
 *
 * Copyright (c) 2024 traffic-hunter
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
package org.traffichunter.titan.core.util;

/**
 * Core runtime system properties.
 */
public final class Configurations {

    public static int taskPendingCapacity() {
        String property = System.getProperty(Property.EVENTLOOP_PENDING_MAX_CAPACITY.value);

        if (property == null || property.isEmpty()) {
            return 500;
        }

        return Integer.parseInt(property);
    }

    public static String name() {
        String property = System.getProperty(Property.NAME.value);

        if (property == null || property.isEmpty()) {
            return "titan";
        }

        return property;
    }

    public enum Property {
        EVENTLOOP_PENDING_MAX_CAPACITY("titan.eventloop.pending.capacity"),
        NAME("titan.name"),
        ;

        private final String value;

        Property(final String value) {
            this.value = value;
        }
    }

    private Configurations() {
    }
}
