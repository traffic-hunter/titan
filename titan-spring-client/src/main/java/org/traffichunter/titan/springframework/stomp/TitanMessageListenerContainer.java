package org.traffichunter.titan.springframework.stomp;

import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolverComposite;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.retry.support.RetryTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.springframework.stomp.messaging.TitanSpringMessageAdapter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TitanMessageListenerContainer {

    private static final Logger log = LoggerFactory.getLogger(TitanMessageListenerContainer.class);

    private final TitanListenerEndpoint endpoint;
    private final TitanClientManager manager;
    private final HandlerMethodArgumentResolverComposite argumentResolvers;
    private final RetryTemplate retryTemplate;
    private final ExecutorService workerExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public TitanMessageListenerContainer(
            TitanListenerEndpoint endpoint,
            TitanClientManager manager,
            HandlerMethodArgumentResolverComposite argumentResolvers,
            RetryTemplate retryTemplate
    ) {
        this.endpoint = endpoint;
        this.manager = manager;
        this.argumentResolvers = argumentResolvers;
        this.retryTemplate = retryTemplate;
        this.workerExecutor = Executors.newSingleThreadExecutor(Thread.ofPlatform()
                .name("titan-listener-worker-" + endpoint.id())
                .daemon(true)
                .factory());
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            StompClientConnection conn = manager.connection();
            conn.subscribe(endpoint.destination(), this::submitMessageTask);
            log.info("Started Titan listener. id={}, destination={}", endpoint.id(), endpoint.destination());
        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("Failed to start listener " + endpoint.id(), e);
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            workerExecutor.shutdownNow();
            StompClientConnection conn = manager.currentConnection();
            if(conn == null) {
                return;
            }

            if (conn.isConnected()) {
                conn.unsubscribe(endpoint.destination());
            }

            log.info("Stopped Titan listener. id={}, destination={}", endpoint.id(), endpoint.destination());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stop listener " + endpoint.id(), e);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void invoke(StompFrame frame) throws Exception {
        Message<byte[]> springMessage = TitanSpringMessageAdapter.from(frame);

        InvocableHandlerMethod invocable = new InvocableHandlerMethod(endpoint.bean(), endpoint.method());
        invocable.setMessageMethodArgumentResolvers(this.argumentResolvers);
        invocable.invoke(springMessage);
    }

    private void submitMessageTask(StompFrame frame) {
        if (!running.get()) {
            return;
        }

        try {
            workerExecutor.execute(() -> processWithRetry(frame));
        } catch (RejectedExecutionException e) {
            if (running.get()) {
                log.warn("Titan listener task rejected. id={}, destination={}", endpoint.id(), endpoint.destination(), e);
            }
        }
    }

    private void processWithRetry(StompFrame frame) {
        if (!running.get()) {
            return;
        }

        try {
            retryTemplate.execute(context -> {
                try {
                    invoke(frame);
                    return null;
                } catch (Exception e) {
                    int attempt = context.getRetryCount() + 1;
                    log.warn(
                            "Titan listener invocation failed. id={}, destination={}, attempt={}",
                            endpoint.id(),
                            endpoint.destination(),
                            attempt,
                            e
                    );
                    throw e;
                }
            }, context -> {
                Throwable exhaustedError = context.getLastThrowable();
                int attempts = context.getRetryCount();
                log.error(
                        "Titan listener retry exhausted. id={}, destination={}, attempts={}",
                        endpoint.id(),
                        endpoint.destination(),
                        attempts,
                        exhaustedError
                );
                return null;
            });
        } catch (Exception e) {
            log.error("Unexpected error while executing retry template. id={}, destination={}",
                    endpoint.id(),
                    endpoint.destination(),
                    e);
        }
    }
}
