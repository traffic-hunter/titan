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
package org.traffichunter.titan.fanout.exporter;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.stomp.Command;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.StompServer;
import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.fanout.CompletableResult;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * @author yun
 */
public final class VertxStompFanoutExporter implements FanoutExporter {

    private final StompServer server;
    private final EventLoop eventLoop;

    public VertxStompFanoutExporter(StompServer server) {
        this.server = Assert.checkNotNull(server, "server");
        this.eventLoop = new VertxEventLoop(server.vertx().getOrCreateContext());
    }

    @Override
    public String name() {
        return "vertx-stomp";
    }

    @Override
    public CompletableResult export(Destination destination, Buffer payload) {
        Assert.checkState(server.isListening(), "Vert.x STOMP server is not listening");

        io.vertx.ext.stomp.Destination stompDestination = server.stompHandler()
                .getDestination(destination.path());
        if (stompDestination == null) {
            Promise<CompletableResult> resultPromise = Promise.newPromise(eventLoop);
            return CompletableResult.completed(List.of(destination), 0, 0, 0, resultPromise);
        }

        int attempted = stompDestination.numberOfSubscriptions();
        int succeeded = 0;
        int failed = 0;

        try {
            Frame frame = new Frame()
                    .setCommand(Command.SEND)
                    .setDestination(destination.path())
                    .setBody(io.vertx.core.buffer.Buffer.buffer(payload.getBytes()));
            frame.addHeader(Frame.CONTENT_LENGTH, Integer.toString(payload.length()));
            stompDestination.dispatch(null, frame);
            succeeded = attempted;
        } catch (Exception e) {
            failed = attempted;
        }

        Promise<CompletableResult> resultPromise = Promise.newPromise(eventLoop);
        return CompletableResult.completed(
                List.of(destination),
                attempted,
                succeeded,
                failed,
                resultPromise
        );
    }

    private static final class VertxEventLoop implements EventLoop {

        private final Context context;

        private VertxEventLoop(Context context) {
            this.context = context;
        }

        @Override
        public void start() {
        }

        @Override
        public void register(Runnable task) {
            context.runOnContext(ignored -> task.run());
        }

        @Override
        public <V> Promise<V> submit(Runnable task) {
            Promise<V> promise = Promise.newPromise(this, task);
            register(promise);
            return promise;
        }

        @Override
        public <V> Promise<V> submit(Callable<V> task) {
            Promise<V> promise = Promise.newPromise(this, task);
            register(promise);
            return promise;
        }

        @Override
        public <V> ScheduledPromise<V> schedule(Runnable task, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("Vert.x fanout result event loop does not support scheduling");
        }

        @Override
        public <V> ScheduledPromise<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("Vert.x fanout result event loop does not support scheduling");
        }

        @Override
        public <V> ScheduledPromise<V> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException("Vert.x fanout result event loop does not support scheduling");
        }

        @Override
        public <V> ScheduledPromise<V> scheduleWithFixedDelay(Runnable task, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException("Vert.x fanout result event loop does not support scheduling");
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return Context.isOnEventLoopThread() && Vertx.currentContext() == context;
        }

        @Override
        public void gracefullyShutdown(long timeout, TimeUnit unit) {
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isNotStarted() {
            return false;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isShuttingDown() {
            return false;
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }
    }
}
