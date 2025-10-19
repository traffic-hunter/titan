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
package org.traffichunter.titan.core.util;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;

/**
 * @author yungwang-o
 */
public final class Assert {

    public static void checkArgument(final boolean expression, final String exceptionMessage) {
        if (!expression) {
            throw new IllegalArgumentException(exceptionMessage);
        }
    }

    public static void checkState(final boolean expression, final String exceptionMessage) {
        if (!expression) {
            throw new IllegalStateException(exceptionMessage);
        }
    }

    public static void checkRange(final int min, final int max, final String exceptionMessage) {
        if (min > max) {
            throw new IllegalArgumentException(exceptionMessage);
        }
    }

    public static void checkOverflow(final boolean expression) {
        if (!expression) {
            throw new BufferOverflowException();
        }
    }

    public static void checkUnderflow(final boolean expression) {
        if (!expression) {
            throw new BufferUnderflowException();
        }
    }

    public static void checkNull(final Object obj, final String exceptionMessage) {
        if(obj == null) {
            throw new NullPointerException(exceptionMessage);
        }
    }

    private Assert() { }
}
