package org.traffichunter.titan.core.test.implementation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.concurrent.EventLoop;
import org.traffichunter.titan.core.util.concurrent.AsyncListener;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.concurrent.PromiseImpl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        AsyncListener listener1 = mock(AsyncListener.class);
        willDoNothing().given(listener1).onComplete(any(Promise.class));
        promise.addListener(listener1);

        AsyncListener listener2 = mock(AsyncListener.class);
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

        assertTrue(promise.isFailed());
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
