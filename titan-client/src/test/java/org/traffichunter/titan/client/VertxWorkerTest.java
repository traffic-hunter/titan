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
package org.traffichunter.titan.client;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class VertxWorkerTest {

    private Vertx vertx;
    private Context context;
    private VertxWorker worker;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        context = vertx.getOrCreateContext();
        worker = new VertxWorker(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        worker.close();
        vertx.close().await(5, TimeUnit.SECONDS);
    }

    @Test
    void execute_on_the_configured_context() throws Exception {
        assertThat(worker.inWorker()).isFalse();

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        worker.execute(() -> result.complete(worker.inWorker() && Vertx.currentContext() == context));

        assertThat(result.get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void submit_completes_with_the_callable_result() throws Exception {
        CompletableFuture<String> result = worker.submit(() -> {
            assertThat(worker.inWorker()).isTrue();
            return "completed";
        });

        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("completed");
    }

    @Test
    void submit_completes_exceptionally_when_the_callable_fails() {
        CompletableFuture<String> result = worker.submit(() -> {
            throw new IllegalStateException("failed");
        });

        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void close_rejects_new_work_without_closing_the_vertx_context() throws Exception {
        worker.close();

        assertThatThrownBy(() -> worker.execute(() -> {}))
                .isInstanceOf(RejectedExecutionException.class);
        assertThatThrownBy(() -> worker.submit(() -> "ignored").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RejectedExecutionException.class);

        CompletableFuture<Void> contextResult = new CompletableFuture<>();
        context.runOnContext(ignored -> contextResult.complete(null));
        contextResult.get(5, TimeUnit.SECONDS);
    }
}
