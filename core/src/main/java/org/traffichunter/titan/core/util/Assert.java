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
package org.traffichunter.titan.core.util;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.function.Supplier;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

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

    @Contract("null, _ -> fail")
    @CanIgnoreReturnValue
    public static <T> T checkNotNull(@Nullable final T obj, final String exceptionMessage) {
        if (obj == null) {
            throw new NullPointerException(exceptionMessage);
        }
        return obj;
    }

    @Contract("!null, _ -> fail")
    public static void checkNull(@Nullable final Object obj, final String exceptionMessage) {
        if (obj != null) {
            throw new NullPointerException(exceptionMessage);
        }
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
