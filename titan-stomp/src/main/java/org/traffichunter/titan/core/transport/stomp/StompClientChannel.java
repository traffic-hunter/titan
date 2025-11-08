/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.transport.stomp;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.Id;
import org.traffichunter.titan.core.util.RoutingKey;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.inet.Frame;

/**
 * @author yungwang-o
 */
public interface StompClientChannel extends Channel {

    Future<StompFrame> send(RoutingKey destination, Buffer body);

    Future<StompFrame> send(RoutingKey destination, StompHeaders headers, Buffer body);

    Future<StompFrame> send(StompFrame frame);

    Future<StompFrame> send(StompHeaders headers, Buffer body);

    Future<String> subscribe(RoutingKey destination, Handler<StompFrame> handler);

    Future<String> subscribe(RoutingKey destination, StompHeaders headers, Handler<StompFrame> handler);

    Future<String> unsubscribe(RoutingKey destination);

    Future<String> unsubscribe(RoutingKey destination, Handler<StompFrame> handler);

    String version();

    InetSocketAddress server();

    @CanIgnoreReturnValue
    StompClientChannel exceptionHandler(Handler<Throwable> exceptionHandler);

    @CanIgnoreReturnValue
    StompClientChannel readHandler(Handler<StompFrame> readHandler);

    @CanIgnoreReturnValue
    StompClientChannel writeHandler(Handler<StompFrame> writeHandler);

    @CanIgnoreReturnValue
    StompClientChannel errorHandler(Handler<StompFrame> errorHandler);

    Future<StompFrame> begin(Id txId);

    Future<StompFrame> begin(Id txId, StompHeaders headers);

    Future<StompFrame> commit(Id txId);

    Future<StompFrame> commit(Id txId, StompHeaders headers);

    Future<StompFrame> abort(Id txId);

    Future<StompFrame> abort(Id txId, StompHeaders headers);

    Future<StompFrame> disconnect();

    Future<StompFrame> disconnect(Frame<Elements, String> frame);

    Future<StompFrame> ack(Id id);

    Future<StompFrame> ack(Id id, Id txId);

    Future<StompFrame> nack(Id id);

    Future<StompFrame> nack(Id id, Id txId);

    boolean isConnected();
}
