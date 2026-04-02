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
package org.traffichunter.titan.core.channel.stomp;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.Subscription;
import org.traffichunter.titan.core.concurrent.Completable;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.AckMode;
import static org.traffichunter.titan.core.codec.stomp.StompFrame.create;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;

/**
 * @author yun
 */
public class StompClientConnectionImpl implements StompClientConnection {

    private static final int MAX_SUBSCRIBER = 100;

    private final String sessionId = IdGenerator.uuid();
    private final NetChannel netChannel;
    private final StompClientHandler stompClientHandler;
    private final StompClientOption option;

    private final Map<String, Subscription> subscriptions = new HashMap<>(MAX_SUBSCRIBER);
    private final Map<Long, ScheduledPromise<?>> pingPongTaskMap = new HashMap<>();
    private final Map<String, Promise<Void>> receiptMap = new HashMap<>();
    private final AtomicLong timer = new AtomicLong();

    private final @Nullable Promise<Void> connectPromise = Promise.newPromise(eventLoop());
    private volatile boolean stompConnected;

    private long pingTimer = -1;
    private long pongTimer = -1;

    StompClientConnectionImpl(
            ChannelHandShakeEventListener channelHandShakeEventListener,
            StompClientOption option
    ) throws IOException {
        this(NetChannel.open(channelHandShakeEventListener), option);
    }

    StompClientConnectionImpl(NetChannel netChannel, StompClientOption option) {
        this.netChannel = netChannel;
        this.stompClientHandler = new StompClientHandler();
        this.option = option;
    }

    @Override
    public Channel channel() {
        return netChannel;
    }

    @Override
    public String session() {
        return sessionId;
    }

    @Override
    public Instant setLastActivatedAt() {
        return netChannel.setLastActivatedAt();
    }

    @Override
    public Instant lastActivatedAt() {
        return netChannel.lastActivatedAt();
    }

    @Override
    public String version() {
        return option.stompVersion().getVersion();
    }

    @Override
    public Promise<StompFrame> disconnect() {
        StompFrame frame = create(StompHeaders.create(), StompCommand.DISCONNECT);
        frame.addHeader(Elements.RECEIPT, IdGenerator.uuid());
        return disconnect(frame);
    }

    @Override
    public Promise<StompFrame> disconnect(StompFrame frame) {
        Promise<StompFrame> framePromise = Promise.newPromise(eventLoop());
        if (netChannel.isActive()) {
            send(frame, framePromise);
            framePromise.addListener(f -> close());
            eventLoop().schedule(() -> {
                if (framePromise.trySuccess(frame)) {
                    close();
                }
            }, 100, TimeUnit.MILLISECONDS);
        } else {
            return framePromise.fail(new IllegalStateException("Channel is not active"));
        }

        return framePromise;
    }

    @Override
    public Promise<StompFrame> begin(String id) {
        return begin(id, StompHeaders.create());
    }

    @Override
    public Promise<StompFrame> begin(String id, StompHeaders headers) {
        Promise<StompFrame> framePromise = Promise.newPromise(eventLoop());
        headers.put(Elements.TRANSACTION, id);
        send(create(headers, StompCommand.BEGIN), framePromise);
        return framePromise;
    }

    @Override
    public Promise<StompFrame> commit(String id) {
        return commit(id, StompHeaders.create());
    }

    @Override
    public Promise<StompFrame> commit(String id, StompHeaders headers) {
        Promise<StompFrame> framePromise = Promise.newPromise(eventLoop());
        headers.put(Elements.TRANSACTION, id);
        send(create(headers, StompCommand.COMMIT), framePromise);
        return framePromise;
    }

    @Override
    public Promise<StompFrame> abort(String id) {
        return abort(id, StompHeaders.create());
    }

    @Override
    public Promise<StompFrame> abort(String id, StompHeaders headers) {
        Promise<StompFrame> framePromise = Promise.newPromise(eventLoop());
        headers.put(Elements.TRANSACTION, id);
        send(create(headers, StompCommand.ABORT), framePromise);
        return framePromise;
    }

    @Override
    public Promise<StompFrame> ack(String id) {
        Promise<StompFrame> framePromise = Promise.newPromise(eventLoop());
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ID, id);
        send(create(headers, StompCommand.ACK), framePromise);
        return framePromise;
    }

    @Override
    public Promise<StompFrame> ack(String id, String txId) {
        Promise<StompFrame> framePromise = Promise.newPromise(eventLoop());
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ID, id);
        headers.put(Elements.TRANSACTION, txId);
        send(create(headers, StompCommand.ACK), framePromise);
        return framePromise;
    }

    @Override
    public Promise<StompFrame> nack(String id) {
        Promise<StompFrame> framePromise = Promise.newPromise(eventLoop());
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ID, id);
        send(create(headers, StompCommand.NACK), framePromise);
        return framePromise;
    }

    @Override
    public Promise<StompFrame> nack(String id, String txId) {
        Promise<StompFrame> framePromise = Promise.newPromise(eventLoop());
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ID, id);
        headers.put(Elements.TRANSACTION, txId);
        send(create(headers, StompCommand.NACK), framePromise);
        return framePromise;
    }

    @Override
    public Promise<StompFrame> send(StompFrame frame) {
        Promise<StompFrame> framePromise = Promise.newPromise(eventLoop());
        send(frame, framePromise);
        return framePromise;
    }

    @Override
    public Promise<StompFrame> send(String destination, Buffer body) {
        return send(destination, body, StompHeaders.create());
    }

    @Override
    public Promise<StompFrame> send(String destination, Buffer body, StompHeaders headers) {
        return send(destination, create(headers, StompCommand.SEND, body));
    }

    @Override
    public Promise<StompFrame> send(String destination, StompFrame body) {
        body.addHeader(Elements.DESTINATION, destination);
        return send(body);
    }

    @Override
    public Promise<StompFrame> subscribe(String destination) {
        return subscribe(destination, StompHeaders.create());
    }

    @Override
    public Promise<StompFrame> subscribe(String destination, StompHeaders headers) {
        return subscribe(destination, headers, frame -> { });
    }

    @Override
    public Promise<StompFrame> subscribe(String destination, Handler<StompFrame> handler) {
        return subscribe(destination, StompHeaders.create(), handler);
    }

    @Override
    public Promise<StompFrame> subscribe(String destination, StompHeaders headers, Handler<StompFrame> handler) {
        String id = headers.getOrDefault(Elements.ID, IdGenerator.uuid());

        String ackMode = headers.get(Elements.ACK);
        if (ackMode == null) {
            ackMode = AckMode.AUTO;
        }

        if (subscriptions.containsKey(id)) {
            return Promise.failedPromise(netChannel.eventLoop(), new StompNetChannelException("Subscription already exists"));
        }

        Subscription subscription = Subscription.builder()
                .id(id)
                .ackMode(ackMode)
                .handler(handler)
                .key(Destination.create(destination))
                .channel(this)
                .build();

        subscriptions.put(id, subscription);

        headers.put(Elements.DESTINATION, destination);
        headers.put(Elements.ID, id);

        return send(create(headers, StompCommand.SUBSCRIBE));
    }

    @Override
    public Promise<StompFrame> unsubscribe(String destination) {
        return unsubscribe(destination, StompHeaders.create());
    }

    @Override
    public Promise<StompFrame> unsubscribe(String destination, StompHeaders headers) {
        String id = headers.get(Elements.ID);
        if (id == null) {
            return Promise.failedPromise(netChannel.eventLoop(), new StompNetChannelException("Missing id header"));
        }

        Subscription subscription = subscriptions.get(id);
        if (subscription == null) {
            return Promise.failedPromise(netChannel.eventLoop(), new StompNetChannelException("Subscription already not exists"));
        }

        subscriptions.remove(id);
        return send(create(headers, StompCommand.UNSUBSCRIBE));
    }

    @Override
    public Promise<StompFrame> error(StompFrame frame) {
        return send(frame);
    }

    @Override
    public List<Subscription> subscriptions() {
        return subscriptions.values().stream().toList();
    }

    @Override
    public void setHeartbeat(long ping, long pong, Runnable handler) {
        cancelHeartbeat();

        if (ping > 0) {
            pingTimer = setInterval(ping, ping, handler);
        }
        if (pong > 0) {
            pongTimer = setInterval(pong, pong, () -> {
                long d = Duration.between(netChannel.lastActivatedAt(), Instant.now()).toMillis();
                if (d > pong * 2) {
                    close();
                }
            });
        }
    }

    @Override
    public void registerInboundSubscription(String id, String destination, String ackMode) {
        Subscription subscription = Subscription.builder()
                .id(id)
                .ackMode(ackMode)
                .key(Destination.create(destination))
                .channel(this)
                .build();

        subscriptions.put(id, subscription);
    }

    @Override
    public void removeInboundSubscription(String id) {
        subscriptions.remove(id);
    }

    @Override
    public void receipt(String receiptId) {
        if (receiptId.isBlank()) {
            return;
        }

        Promise<Void> resultPromise = receiptMap.remove(receiptId);
        if (resultPromise != null) {
            resultPromise.success();
        }
    }

    @Override
    public void connected() {
        if(netChannel.isConnected() && !stompConnected) {
            stompConnected = true;
        }

        Promise<Void> promise = connectPromise;
        if (promise != null && !promise.isDone()) {
            promise.success();
        }
    }

    @Override
    public void failConnect(Throwable error) {
        Promise<Void> promise = connectPromise;
        if (promise != null && !promise.isDone()) {
            promise.fail(error);
        }
    }

    @Override
    public StompHandler handler() {
        return stompClientHandler;
    }

    @Override
    public boolean isConnected() {
        return netChannel.isConnected();
    }

    @Override
    public void close() {
        cancelHeartbeat();
        failConnect(new StompNetChannelException("Channel closed before STOMP connect completed"));
        receiptMap.values().forEach(promise ->
                promise.fail(new StompNetChannelException("Channel closed before receipt received")));
        receiptMap.clear();
        pingPongTaskMap.clear();
        subscriptions.clear();
        netChannel.close();
    }

    private void send(StompFrame frame, Completable<StompFrame> receiptPromise) {
        Buffer body = frame.getBody();
        if (body != null && !frame.getHeaders().containsKey(Elements.CONTENT_LENGTH)) {
            frame.addHeader(Elements.CONTENT_LENGTH, String.valueOf(body.length()));
        }

        String receiptId = frame.getHeader(Elements.RECEIPT);
        if (receiptId != null && !receiptId.isBlank()) {
            Promise<Void> resultPromise = Promise.newPromise(eventLoop());
            resultPromise.addListener(future -> {
                if (future.isSuccess()) {
                    receiptPromise.success(frame);
                } else {
                    receiptPromise.fail(new StompNetChannelException("Failed to receive receipt"));
                }
            });
            receiptMap.put(receiptId, resultPromise);
        }

        try {
            netChannel.writeAndFlush(frame.toBuffer());
            if (receiptId == null || receiptId.isBlank()) {
                receiptPromise.success(frame);
            }
        } catch (Exception e) {
            if (receiptId != null && !receiptId.isBlank()) {
                receiptMap.remove(receiptId);
            }
            close();
            receiptPromise.fail(new StompNetChannelException("Failed to write STOMP frame", e));
        }
    }

    private void cancelHeartbeat() {
        if (pingTimer >= 0) {
            cancelInterval(pingTimer);
            pingTimer = -1;
        }
        if (pongTimer >= 0) {
            cancelInterval(pongTimer);
            pongTimer = -1;
        }
    }

    private long setInterval(long initialDelay, long interval, Runnable handler) {
        if (initialDelay <= 0) {
            throw new StompNetChannelException("initialDelay must be greater than zero");
        }

        final long timerId = timer.incrementAndGet();
        EventLoop eventLoop = netChannel.eventLoop();

        final ScheduledPromise<?> scheduledTask =
                eventLoop.scheduleAtFixedRate(handler, initialDelay, interval, TimeUnit.MILLISECONDS);

        pingPongTaskMap.put(timerId, scheduledTask);
        return timerId;
    }

    private void cancelInterval(long timerId) {
        final ScheduledPromise<?> removeTask = pingPongTaskMap.remove(timerId);
        if (removeTask != null) {
            removeTask.cancel();
        }
    }

    private IOEventLoop eventLoop() {
        return netChannel.eventLoop();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StompClientConnectionImpl that)) {
            return false;
        }
        return this.netChannel.id().equals(that.netChannel.id());
    }

    @Override
    public int hashCode() {
        return netChannel.id().hashCode();
    }
}
