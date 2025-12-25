package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.channel.TaskEventLoop;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;

/**
 * @author yungwang-o
 */
class TaskEventLoopTest {

    @Nested
    class TaskTest {

        private final EventLoop eventLoop = new TaskEventLoop();

        @BeforeEach
        void setUp() {
            eventLoop.start();
        }

        @AfterEach
        void tearDown() {
            eventLoop.gracefullyShutdown(5, TimeUnit.SECONDS);
        }

        @Test
        void taskRegisterTest() {
            AtomicInteger count = new AtomicInteger();

            RunnableTask runnableTask1 = new RunnableTask(count);
            eventLoop.register(runnableTask1);

            RunnableTask runnableTask2 = new RunnableTask(count);
            eventLoop.register(runnableTask2);

            RunnableTask runnableTask3 = new RunnableTask(count);
            eventLoop.register(runnableTask3);

            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> runnableTask1.isDone && runnableTask2.isDone && runnableTask3.isDone);

            assertThat(runnableTask1.value).isEqualTo(1);
            assertThat(runnableTask2.value).isEqualTo(2);
            assertThat(runnableTask3.value).isEqualTo(3);
        }

        @Test
        void taskCallablePromiseTest() throws Exception {
            AtomicInteger count = new AtomicInteger();

            CallableTask task1 = new CallableTask(count, 1);
            Promise<Integer> promise = eventLoop.submit(task1);

            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .until(promise::isDone);

            assertThat(promise.get().intValue()).isEqualTo(1);
        }

        @Test
        void scheduledTaskTest() {
            AtomicInteger counter = new AtomicInteger(0);

            RunnableTask runnableTask1 = new RunnableTask(counter);
            eventLoop.schedule(runnableTask1, 500, TimeUnit.MILLISECONDS);

            RunnableTask runnableTask2 = new RunnableTask(counter);
            eventLoop.schedule(runnableTask2, 800, TimeUnit.MILLISECONDS);

            RunnableTask runnableTask3 = new RunnableTask(counter);
            eventLoop.schedule(runnableTask3, 200, TimeUnit.MILLISECONDS);

            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> runnableTask1.isDone && runnableTask2.isDone && runnableTask3.isDone);

            assertThat(runnableTask1.value).isEqualTo(2);
            assertThat(runnableTask2.value).isEqualTo(3);
            assertThat(runnableTask3.value).isEqualTo(1);
        }

        @Test
        void scheduledTaskPromiseTest() {
            AtomicInteger counter = new AtomicInteger(0);

            CallableTask task1 = new CallableTask(counter, 1);
            Promise<Integer> promise1 = eventLoop.schedule(task1, 500, TimeUnit.MILLISECONDS);

            CallableTask task2 = new CallableTask(counter, 2);
            Promise<Integer> promise2 = eventLoop.schedule(task2, 100, TimeUnit.MILLISECONDS);

            CallableTask task3 = new CallableTask(counter, 3);
            Promise<Integer> promise3 = eventLoop.schedule(task3, 800, TimeUnit.MILLISECONDS);

            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> promise1.isDone() && promise2.isDone() && promise3.isDone());

            assertThat(task1.value).isEqualTo(1);
            assertThat(task2.value).isEqualTo(2);
            assertThat(task3.value).isEqualTo(3);
            assertThat(promise1.isSuccess()).isTrue();
            assertThat(promise2.isSuccess()).isTrue();
            assertThat(promise3.isSuccess()).isTrue();
            assertThat(task1.count).isEqualTo(2);
            assertThat(task2.count).isEqualTo(1);
            assertThat(task3.count).isEqualTo(3);
        }
    }

    @Nested
    class ShutdownTest {

        private final EventLoop eventLoop = new TaskEventLoop();

        @Test
        void isStartTest() {
            try {
                eventLoop.start();

                assertThat(eventLoop.isStarted()).isTrue();
            } finally {
                eventLoop.gracefullyShutdown(0, TimeUnit.MILLISECONDS);
            }
        }

        @Test
        void isShutdownTest() throws Exception {
            try {
                eventLoop.start();
            } finally {
                eventLoop.gracefullyShutdown(200, TimeUnit.MILLISECONDS);
            }

            Thread.sleep(300);

            assertThat(eventLoop.isStarted()).isFalse();
            assertThat(eventLoop.isShutdown()).isTrue();
        }

        @Test
        void shouldNotShutdownBeforeGracefulTimeoutTest() {
            eventLoop.start();

            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);

            Awaitility.await()
                    .atMost(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> assertFalse(eventLoop.isShutdown()));
        }

        @Test
        void completeQueuedTasksBeforeShutdownTest() {
            eventLoop.start();

            DelayRunnableTask task1 = new DelayRunnableTask(100);
            eventLoop.register(task1);

            DelayRunnableTask task2 = new DelayRunnableTask(200);
            eventLoop.register(task2);

            eventLoop.gracefullyShutdown(300, TimeUnit.MILLISECONDS);

            Awaitility.await()
                    .atMost(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        assertTrue(task1.isDone);
                        assertTrue(task2.isDone);
                        assertTrue(eventLoop.isShutdown());
                    });
        }

        @Test
        void removeQueuedScheduledTasksBeforeShutdownTest() {
            eventLoop.start();

            AtomicInteger counter = new AtomicInteger(0);
            RunnableTask task1 = new RunnableTask(counter);
            ScheduledPromise<?> scheduleTask1 = eventLoop.schedule(task1, 100, TimeUnit.MILLISECONDS);

            RunnableTask task2 = new RunnableTask(counter);
            ScheduledPromise<?> scheduleTask2 = eventLoop.schedule(task2, 300, TimeUnit.MILLISECONDS);

            eventLoop.gracefullyShutdown(200, TimeUnit.MILLISECONDS);

            Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        assertFalse(scheduleTask1.isDone());
                        assertFalse(scheduleTask2.isDone());
                        assertTrue(eventLoop.isShutdown());
                    });
        }
    }

    private static class DelayRunnableTask implements Runnable {

        final long delay;
        boolean isDone = false;

        public DelayRunnableTask(final long delay) {
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(delay);
                isDone = true;
            } catch (InterruptedException ignored) {}
        }
    }

    private static class RunnableTask implements Runnable {
        final AtomicInteger counter;
        int value;
        boolean isDone = false;

        public RunnableTask(final AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public void run() {
            value = counter.incrementAndGet();
            isDone = true;
        }
    }

    private static class CallableTask implements Callable<Integer> {
        final AtomicInteger counter;
        int count;
        final int value;

        public CallableTask(final AtomicInteger counter, final int value) {
            this.counter = counter;
            this.value = value;
        }

        @Override
        public Integer call() {
            count = counter.incrementAndGet();
            return value;
        }
    }
}