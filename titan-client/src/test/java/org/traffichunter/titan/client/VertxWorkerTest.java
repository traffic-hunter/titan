/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
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
