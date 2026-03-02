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
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.ChannelHandShakeEventListener;
import org.traffichunter.titan.core.channel.ChannelHandlerChain;
import org.traffichunter.titan.core.channel.EventLoop;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompVersion;
import org.traffichunter.titan.core.codec.stomp.Subscription;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.RoutingKey;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.AckMode;
import static org.traffichunter.titan.core.codec.stomp.StompFrame.create;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;

/**
 * @author yun
 */
public class StompNetChannelImpl implements StompNetChannel {

    private static final int MAX_SUBSCRIBER = 100;

    private final String sessionId = IdGenerator.uuid();
    private final NetChannel netChannel;
    private final StompVersion stompVersion;
    private final StompClientHandler stompClientHandler;

    private final Map<String, Subscription> subscriptions = new HashMap<>(MAX_SUBSCRIBER);
    private final Map<Long, ScheduledPromise<?>> pingPongTaskMap = new HashMap<>();
    private final Map<String, ChannelPromise> receiptMap = new HashMap<>();
    private final AtomicLong timer = new AtomicLong();

    private long pingTimer = -1;
    private long pongTimer = -1;

    StompNetChannelImpl(
            ChannelHandShakeEventListener channelHandShakeEventListener,
            StompVersion stompVersion
    ) throws IOException {
        this(NetChannel.open(channelHandShakeEventListener), stompVersion);
    }

    StompNetChannelImpl(NetChannel netChannel, StompVersion stompVersion) {
        this.netChannel = netChannel;
        this.stompVersion = stompVersion;
        this.stompClientHandler = new StompClientHandler();
    }

    @Override
    public <T> NetChannel setOption(SocketOption<T> option, T value) {
        return netChannel.setOption(option, value);
    }

    @Override
    public ChannelHandlerChain chain() {
        return netChannel.chain();
    }

    @Override
    public ChannelPromise register(IOEventLoop eventLoop, ChannelPromise promise) {
        return netChannel.register(eventLoop, promise);
    }

    @Override
    public IOEventLoop eventLoop() {
        return netChannel.eventLoop();
    }

    @Override
    public String id() {
        return netChannel.id();
    }

    @Override
    public String session() {
        return sessionId;
    }

    @Override
    public @Nullable <T> T getOption(SocketOption<T> option) {
        return netChannel.getOption(option);
    }

    @Override
    public Instant lastActivatedAt() {
        return netChannel.lastActivatedAt();
    }

    @Override
    public Instant setLastActivatedAt() {
        return netChannel.setLastActivatedAt();
    }

    @Override
    public @Nullable SocketAddress localAddress() {
        return netChannel.localAddress();
    }

    @Override
    public @Nullable SocketAddress remoteAddress() {
        return netChannel.remoteAddress();
    }

    @Override
    public boolean isOpen() {
        return netChannel.isOpen();
    }

    @Override
    public boolean isRegistered() {
        return netChannel.isRegistered();
    }

    @Override
    public boolean isActive() {
        return netChannel.isActive();
    }

    @Override
    public boolean isClosed() {
        return netChannel.isClosed();
    }

    @Override
    public String version() {
        return stompVersion.getVersion();
    }

    @Override
    public ChannelPromise disconnect() {
        StompFrame frame = create(StompHeaders.create(), StompCommand.DISCONNECT);
        frame.addHeader(Elements.RECEIPT, IdGenerator.uuid());
        return disconnect(frame);
    }

    @Override
    public ChannelPromise disconnect(StompFrame frame) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(netChannel.eventLoop(), this);
        if (isActive()) {
            send(frame, channelPromise);
            channelPromise.addListener(f -> close());
        } else {
            return channelPromise.fail(new IllegalStateException("Channel is not active"));
        }

        return channelPromise;
    }

    @Override
    public ChannelPromise begin(String id) {
        return begin(id, StompHeaders.create());
    }

    @Override
    public ChannelPromise begin(String id, StompHeaders headers) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(netChannel.eventLoop(), this);
        headers.put(Elements.TRANSACTION, id);
        send(create(headers, StompCommand.BEGIN), channelPromise);
        return channelPromise;
    }

    @Override
    public ChannelPromise commit(String id) {
        return commit(id, StompHeaders.create());
    }

    @Override
    public ChannelPromise commit(String id, StompHeaders headers) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(netChannel.eventLoop(), this);
        headers.put(Elements.TRANSACTION, id);
        send(create(headers, StompCommand.COMMIT), channelPromise);
        return channelPromise;
    }

    @Override
    public ChannelPromise abort(String id) {
        return abort(id, StompHeaders.create());
    }

    @Override
    public ChannelPromise abort(String id, StompHeaders headers) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(netChannel.eventLoop(), this);
        headers.put(Elements.TRANSACTION, id);
        send(create(headers, StompCommand.ABORT), channelPromise);
        return channelPromise;
    }

    @Override
    public ChannelPromise ack(String id) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(netChannel.eventLoop(), this);
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ID, id);
        send(create(headers, StompCommand.ACK), channelPromise);
        return channelPromise;
    }

    @Override
    public ChannelPromise ack(String id, String txId) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(netChannel.eventLoop(), this);
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ID, id);
        headers.put(Elements.TRANSACTION, txId);
        send(create(headers, StompCommand.ACK), channelPromise);
        return channelPromise;
    }

    @Override
    public ChannelPromise nack(String id) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(netChannel.eventLoop(), this);
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ID, id);
        send(create(headers, StompCommand.NACK), channelPromise);
        return channelPromise;
    }

    @Override
    public ChannelPromise nack(String id, String txId) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(netChannel.eventLoop(), this);
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ID, id);
        headers.put(Elements.TRANSACTION, txId);
        send(create(headers, StompCommand.NACK), channelPromise);
        return channelPromise;
    }

    @Override
    public ChannelPromise send(StompFrame frame) {
        ChannelPromise channelPromise = ChannelPromise.newPromise(netChannel.eventLoop(), this);
        send(frame, channelPromise);
        return channelPromise;
    }

    @Override
    public ChannelPromise send(String destination, Buffer body) {
        return send(destination, body, StompHeaders.create());
    }

    @Override
    public ChannelPromise send(String destination, Buffer body, StompHeaders headers) {
        return send(destination, create(headers, StompCommand.SEND, body));
    }

    @Override
    public ChannelPromise send(String destination, StompFrame body) {
        body.addHeader(Elements.DESTINATION, destination);
        return send(body);
    }

    @Override
    public ChannelPromise subscribe(String destination) {
        return subscribe(destination, StompHeaders.create());
    }

    @Override
    public ChannelPromise subscribe(String destination, StompHeaders headers) {
        return subscribe(destination, headers, frame -> { });
    }

    @Override
    public ChannelPromise subscribe(String destination, Handler<StompFrame> handler) {
        return subscribe(destination, StompHeaders.create(), handler);
    }

    @Override
    public ChannelPromise subscribe(String destination, StompHeaders headers, Handler<StompFrame> handler) {
        String id = headers.getOrDefault(Elements.ID, IdGenerator.uuid());

        String ackMode = headers.get(Elements.ACK);
        if (ackMode == null) {
            ackMode = AckMode.AUTO;
        }

        if (subscriptions.containsKey(id)) {
            return ChannelPromise.failedPromise(netChannel.eventLoop(), this, new StompNetChannelException("Subscription already exists"));
        }

        Subscription subscription = Subscription.builder()
                .id(id)
                .ackMode(ackMode)
                .handler(handler)
                .key(RoutingKey.create(destination))
                .channel(this)
                .build();

        subscriptions.put(id, subscription);

        headers.put(Elements.DESTINATION, destination);
        headers.put(Elements.ID, id);

        return send(create(headers, StompCommand.SUBSCRIBE));
    }

    @Override
    public ChannelPromise unsubscribe(String destination) {
        return unsubscribe(destination, StompHeaders.create());
    }

    @Override
    public ChannelPromise unsubscribe(String destination, StompHeaders headers) {
        String id = headers.get(Elements.ID);
        if (id == null) {
            return ChannelPromise.failedPromise(netChannel.eventLoop(), this, new StompNetChannelException("Missing id header"));
        }

        Subscription subscription = subscriptions.get(id);
        if (subscription == null) {
            return ChannelPromise.failedPromise(netChannel.eventLoop(), this, new StompNetChannelException("Subscription already not exists"));
        }

        subscriptions.remove(id);
        return send(create(headers, StompCommand.UNSUBSCRIBE));
    }

    @Override
    public ChannelPromise error(StompFrame frame) {
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
                long d = Duration.between(lastActivatedAt(), Instant.now()).toMillis();
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
                .key(RoutingKey.create(destination))
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

        ChannelPromise promise = receiptMap.remove(receiptId);
        if (promise != null) {
            promise.success();
        }
    }

    @Override
    public StompHandler handler() {
        return stompClientHandler;
    }

    @Override
    public void close() {
        cancelHeartbeat();
        receiptMap.clear();
        pingPongTaskMap.clear();
        subscriptions.clear();
        netChannel.close();
    }

    private void send(StompFrame frame, ChannelPromise receiptPromise) {
        Buffer body = frame.getBody();
        if (body != null && !frame.getHeaders().containsKey(Elements.CONTENT_LENGTH)) {
            frame.addHeader(Elements.CONTENT_LENGTH, String.valueOf(body.length()));
        }

        String receiptId = frame.getHeader(Elements.RECEIPT);
        if (receiptId != null && !receiptId.isBlank()) {
            receiptMap.put(receiptId, receiptPromise);
        } else {
            receiptPromise.success();
        }

        netChannel.writeAndFlush(frame.toBuffer());
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StompNetChannelImpl that)) {
            return false;
        }
        return this.netChannel.id().equals(that.netChannel.id());
    }

    @Override
    public int hashCode() {
        return netChannel.id().hashCode();
    }
}
