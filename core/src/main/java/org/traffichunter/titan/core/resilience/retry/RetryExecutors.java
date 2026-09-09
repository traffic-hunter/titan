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
package org.traffichunter.titan.core.resilience.retry;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.traffichunter.titan.core.channel.EventLoop;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Factory methods for retry executors.
 *
 * <p>Use these helpers when callers should not depend on concrete executor
 * constructors directly.</p>
 *
 * @author yun
 */
public final class RetryExecutors {

    public static EventLoopRetryExecutor eventLoopRetryExecutor(RetryPolicy retryPolicy, RetryListener retryListener) {
        return new EventLoopRetryExecutor(retryPolicy, retryListener);
    }

    public static EventLoopRetryExecutor eventLoopRetryExecutor(EventLoop eventLoop, RetryListener retryListener, RetryPolicy retryPolicy) {
        return new EventLoopRetryExecutor(eventLoop, retryListener, retryPolicy);
    }

    public static EventLoopRetryExecutor eventLoopRetryExecutor(
            EventLoop eventLoop,
            RetryPolicy retryPolicy,
            RetryListener retryListener
    ) {
        return new EventLoopRetryExecutor(eventLoop, retryListener, retryPolicy);
    }

    public static JdkScheduledRetryExecutor jdkScheduledRetryExecutor(RetryPolicy retryPolicy) {
        return new JdkScheduledRetryExecutor(retryPolicy);
    }

    public static JdkScheduledRetryExecutor jdkScheduledRetryExecutor(RetryPolicy retryPolicy, RetryListener retryListener) {
        return new JdkScheduledRetryExecutor(retryPolicy, retryListener);
    }

    public static JdkScheduledRetryExecutor jdkScheduledRetryExecutor(
            ScheduledExecutorService scheduledExecutorService,
            RetryPolicy retryPolicy
    ) {
        return new JdkScheduledRetryExecutor(scheduledExecutorService, retryPolicy);
    }

    public static JdkScheduledRetryExecutor jdkScheduledRetryExecutor(
            ScheduledExecutorService scheduledExecutorService,
            RetryPolicy retryPolicy,
            RetryListener retryListener
    ) {
        return new JdkScheduledRetryExecutor(scheduledExecutorService, retryPolicy, retryListener);
    }

    public static VertxRetryExecutor vertxRetryExecutor(RetryPolicy retryPolicy) {
        return new VertxRetryExecutor(retryPolicy);
    }

    public static VertxRetryExecutor vertxRetryExecutor(
            RetryPolicy retryPolicy,
            RetryListener retryListener
    ) {
        return new VertxRetryExecutor(retryPolicy, retryListener);
    }

    public static VertxRetryExecutor vertxRetryExecutor(
            Vertx vertx,
            RetryPolicy retryPolicy
    ) {
        return new VertxRetryExecutor(vertx, retryPolicy);
    }

    public static VertxRetryExecutor vertxRetryExecutor(
            Vertx vertx,
            RetryPolicy retryPolicy,
            RetryListener retryListener
    ) {
        return new VertxRetryExecutor(vertx, retryPolicy, retryListener);
    }

    public static VertxRetryExecutor vertxRetryExecutor(
            VertxOptions options,
            RetryPolicy retryPolicy
    ) {
        return new VertxRetryExecutor(options, retryPolicy);
    }

    public static VertxRetryExecutor vertxRetryExecutor(
            VertxOptions options,
            RetryPolicy retryPolicy,
            RetryListener retryListener
    ) {
        return new VertxRetryExecutor(options, retryPolicy, retryListener);
    }

    public static RetryExecutor noopRetryExecutor() {
        return new NoopRetryExecutor();
    }

    private RetryExecutors() { }
}
