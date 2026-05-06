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
package org.traffichunter.titan.core.concurrent;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;

/**
 * Manual completion contract for asynchronous results.
 *
 * <p>The non-{@code try} methods complete the target and return the underlying promise for
 * fluent use. The {@code try} variants report whether this call won the completion race.</p>
 *
 * @author yungwang-o
 */
public interface Completable<C> {

    @CanIgnoreReturnValue
    default Promise<C> success(@Nullable C result) {
        return complete(result, null);
    }

    default Promise<C> success() {
        return complete(null, null);
    }

    @CanIgnoreReturnValue
    default Promise<C> fail(@Nullable Throwable err) {
        return complete(null, err);
    }

    default Promise<C> fail(String message) {
        return complete(null, new PromiseException(message));
    }

    /**
     * Completes with either a result or a failure.
     */
    Promise<C> complete(@Nullable C result, @Nullable Throwable error);

    /**
     * Attempts to complete with either a result or a failure.
     */
    boolean tryComplete(@Nullable C result, @Nullable Throwable error);

    default boolean trySuccess(@Nullable C result) {
        return tryComplete(result, null);
    }

    default boolean trySuccess() {
        return tryComplete(null, null);
    }

    default boolean tryFail(@Nullable Throwable error) {
        return tryComplete(null, error);
    }
}
