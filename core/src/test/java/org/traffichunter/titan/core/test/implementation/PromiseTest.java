package org.traffichunter.titan.core.test.implementation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.util.concurrent.AsyncListener;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.concurrent.PromiseException;
import org.traffichunter.titan.core.util.concurrent.PromiseImpl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DisplayNameGenerator.*;
import static org.mockito.BDDMockito.*;

/**
 * @author yun
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PromiseTest {

    private static final Runnable NOOP = () -> {};

    private final EventLoop eventLoop = mock(EventLoop.class);

    @BeforeEach
    void setUp() {
        given(eventLoop.inEventLoop()).willReturn(true);
    }

    @Test
    void promise_success_test() throws ExecutionException, InterruptedException {
        String result = "test";

        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        promise.success(result);

        assertThat(promise.get()).isEqualTo(result);
    }

    @Test
    void promise_successfully_complete_test() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        promise.run();

        promise.addListener(future -> assertTrue(future.isSuccess()));
        assertTrue(promise.isDone());
    }

    @Test
    void promise_failed_test() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        promise.fail(new IllegalStateException());

        assertThatThrownBy(promise::get).isExactlyInstanceOf(ExecutionException.class);
    }

    @Test
    void promise_should_complete_with_exception() {
        Runnable exceptionRunnable = () -> { throw new RuntimeException(); };
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, exceptionRunnable);

        promise.run();

        promise.addListener(future -> assertTrue(future.isFailed()));
        assertTrue(promise.isDone());
    }

    @Test
    void addListener_should_not_notify_same_listener_twice() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        AsyncListener<String> listener1 = mock(AsyncListener.class);
        willDoNothing().given(listener1).onComplete(any(Promise.class));
        promise.addListener(listener1);

        AsyncListener<String> listener2 = mock(AsyncListener.class);
        willDoNothing().given(listener2).onComplete(any(Promise.class));
        promise.addListener(listener2);

        promise.run();

        verify(listener1, times(1)).onComplete(any(Promise.class));
        verify(listener2, times(1)).onComplete(any(Promise.class));
    }

    @Test
    void await_complete_test() throws Exception {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        promise.run();

        assertTrue(promise.await(1, TimeUnit.SECONDS).isDone());
    }

    @Test
    @Timeout(1)
    void timeout_test() throws InterruptedException {

        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(100);
                promise.success("test");
            } catch (Exception ignored) { }
        });
        t.start();

        promise.await(50, TimeUnit.MILLISECONDS);

        assertFalse(promise.isDone());
        assertFalse(promise.isFailed());
        t.join();
    }

    @Test
    void cancel_test() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        promise.cancel();

        assertTrue(promise.isCancelled());
    }

    @Test
    void map_should_transform_result_test() throws Exception {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        Promise<Integer> mapped = promise.map(String::length);
        promise.success("test");

        assertThat(mapped.get()).isEqualTo(4);
    }

    @Test
    void map_should_fail_without_result() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicBoolean invoked = new AtomicBoolean();

        Promise<Integer> mapped = promise.map(value -> {
            invoked.set(true);
            return value.length();
        });
        promise.success();

        assertThatThrownBy(mapped::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PromiseException.class)
                .hasRootCauseMessage("Cannot map a promise without a result");
        assertThat(invoked).isFalse();
    }

    @Test
    void map_should_propagate_failure_without_invoking_mapper() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicBoolean invoked = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("boom");

        Promise<Integer> mapped = promise.map(value -> {
            invoked.set(true);
            return value.length();
        });
        promise.fail(failure);

        assertThatThrownBy(mapped::get)
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);
        assertThat(invoked).isFalse();
    }

    @Test
    void thenCompose_should_chain_result_test() throws Exception {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        Promise<Integer> chained = promise.thenCompose(value -> {
            Promise<Integer> next = new TestPromiseImpl<>(eventLoop, NOOP);
            next.success(value.length());
            return next;
        });

        promise.success("test");

        assertThat(chained.get()).isEqualTo(4);
    }

    @Test
    void thenCompose_should_fail_without_result() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicBoolean invoked = new AtomicBoolean();

        Promise<Integer> chained = promise.thenCompose(value -> {
            invoked.set(true);
            return Promise.newPromise(eventLoop, () -> value.length());
        });
        promise.success();

        assertThatThrownBy(chained::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PromiseException.class)
                .hasRootCauseMessage("Cannot compose a promise without a result");
        assertThat(invoked).isFalse();
    }

    @Test
    void onSuccess_should_receive_value_test() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicReference<String> result = new AtomicReference<>();

        promise.onSuccess(result::set);
        promise.success("test");

        assertThat(result.get()).isEqualTo("test");
    }

    @Test
    void onFailure_should_receive_error_test() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicReference<Throwable> error = new AtomicReference<>();

        promise.onFailure(error::set);
        IllegalStateException exception = new IllegalStateException("boom");
        promise.fail(exception);

        assertThat(error.get()).isSameAs(exception);
    }

    @Test
    void onSuccess_should_not_run_after_failure() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicBoolean invoked = new AtomicBoolean();

        promise.onSuccess(ignored -> invoked.set(true));
        promise.fail(new IllegalStateException("boom"));

        assertThat(invoked).isFalse();
    }

    @Test
    void onFailure_should_not_run_after_success() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicBoolean invoked = new AtomicBoolean();

        promise.onFailure(ignored -> invoked.set(true));
        promise.success("test");

        assertThat(invoked).isFalse();
    }

    @Test
    void callbacks_registered_after_completion_should_run_once() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        promise.success("test");
        promise.onSuccess(ignored -> successCount.incrementAndGet());
        promise.onFailure(ignored -> failureCount.incrementAndGet());

        assertThat(successCount).hasValue(1);
        assertThat(failureCount).hasValue(0);
    }

    @Test
    void onFailure_registered_after_completion_should_receive_error_once() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        IllegalStateException failure = new IllegalStateException("boom");
        AtomicReference<Throwable> received = new AtomicReference<>();
        AtomicInteger invocationCount = new AtomicInteger();

        promise.fail(failure);
        promise.onFailure(error -> {
            received.set(error);
            invocationCount.incrementAndGet();
        });

        assertThat(received.get()).isSameAs(failure);
        assertThat(invocationCount).hasValue(1);
    }

    @Test
    void callback_failure_should_not_prevent_remaining_callbacks() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicBoolean invoked = new AtomicBoolean();

        promise.onSuccess(ignored -> {
            throw new IllegalStateException("callback failure");
        });
        promise.onSuccess(ignored -> invoked.set(true));
        promise.success("test");

        assertThat(invoked).isTrue();
    }

    @Test
    void onSuccess_should_receive_nullable_result() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicBoolean invoked = new AtomicBoolean();
        AtomicReference<String> result = new AtomicReference<>("value");

        promise.onSuccess(value -> {
            invoked.set(true);
            result.set(value);
        });
        promise.success();

        assertThat(invoked).isTrue();
        assertThat(result.get()).isNull();
    }

    @Test
    void cancellation_should_run_onFailure_with_cancellation_error() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        AtomicReference<Throwable> error = new AtomicReference<>();

        promise.onFailure(error::set);
        promise.cancel();

        assertThat(error.get()).isInstanceOf(java.util.concurrent.CancellationException.class);
    }

    @Test
    void callback_should_be_dispatched_to_event_loop() {
        given(eventLoop.inEventLoop()).willReturn(false);
        AtomicReference<String> result = new AtomicReference<>();
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        promise.onSuccess(result::set);
        promise.success("test");

        assertThat(result.get()).isNull();

        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(eventLoop).execute(callback.capture());

        given(eventLoop.inEventLoop()).willReturn(true);
        callback.getValue().run();

        assertThat(result.get()).isEqualTo("test");
    }

    @Test
    void trySuccess_should_report_completion_state_test() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);

        assertThat(promise.trySuccess("test")).isTrue();
        assertThat(promise.trySuccess("again")).isFalse();
        assertThat(promise.getNow()).isEqualTo("test");
    }

    @Test
    void tryFail_should_report_completion_state_test() {
        Promise<String> promise = new TestPromiseImpl<>(eventLoop, NOOP);
        IllegalStateException failure = new IllegalStateException("boom");

        assertThat(promise.tryFail(failure)).isTrue();
        assertThat(promise.tryFail(new IllegalArgumentException())).isFalse();
        assertThat(promise.isFailed()).isTrue();
        assertThat(promise.error()).isSameAs(failure);
    }

    private static final class TestPromiseImpl<V> extends PromiseImpl<V> {

        public TestPromiseImpl(EventLoop eventLoop, Runnable task) {
            super(eventLoop, task);
        }

        public TestPromiseImpl(EventLoop eventLoop, Callable<V> task) {
            super(eventLoop, task);
        }
    }
}
