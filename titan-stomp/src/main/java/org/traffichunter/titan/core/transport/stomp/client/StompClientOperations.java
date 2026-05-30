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
package org.traffichunter.titan.core.transport.stomp.client;

import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.Map;
import java.util.concurrent.Future;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;

/**
 * Transport-neutral STOMP client operations.
 *
 * @author yun
 */
public interface StompClientOperations {

    Future<StompFrames> send(String destination, Buffer payload);

    Future<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers);

    Future<String> subscribe(String destination, Handler<StompFrames> handler);

    Future<String> subscribe(String destination, Map<Elements, String> headers, Handler<StompFrames> handler);

    Future<StompFrames> unsubscribe(String subscriptionId);

    Future<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers);

    Future<StompFrames> ack(String messageId);

    Future<StompFrames> nack(String messageId);

    Future<StompFrames> disconnect();

    boolean isConnected();
}
