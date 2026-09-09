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
package org.traffichunter.titan.core.util.concurrent;

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
    default Promise<C> fail(Throwable err) {
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

    default boolean tryFail(Throwable error) {
        return tryComplete(null, error);
    }
}
