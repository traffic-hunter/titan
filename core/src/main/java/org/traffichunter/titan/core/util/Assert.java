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
import java.util.function.Supplier;

/**
 * @author yungwang-o
 */
public final class Assert {

    public static void checkArgument(final boolean expression, final String exceptionMessage) {
        check(expression, () -> new IllegalArgumentException(exceptionMessage));
    }

    public static void checkState(final boolean expression, final String exceptionMessage) {
        check(expression, () -> new IllegalStateException(exceptionMessage));
    }

    public static void checkOverflow(final boolean expression) {
        check(expression, BufferOverflowException::new);
    }

    public static void checkUnderflow(final boolean expression) {
        check(expression, BufferUnderflowException::new);
    }

    public static void checkNull(final Object obj, final String exceptionMessage) {
        check(obj == null, () -> new NullPointerException(exceptionMessage));
    }

    public static void check(final boolean expression, final Supplier<? extends Throwable> throwable) {
        if(!expression) {
            throwAsUnchecked(throwable.get());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwAsUnchecked(Throwable throwable) throws T {
        throw (T) throwable;
    }

    private Assert() { }
}
