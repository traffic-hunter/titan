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

import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Handler;

/**
 * Logical subscription retained independently of one physical STOMP connection.
 *
 * <p>The client stores this metadata after SUBSCRIBE succeeds. On a replacement connection,
 * it reuses the destination, headers, and frame handler to restore the subscription before
 * returning to the connected state.</p>
 *
 * @param id stable subscription identifier used for unsubscribe and reconnect
 * @param destination logical destination to restore
 * @param stompHeaders headers sent when creating the subscription
 * @param framesHandler handler retained across physical connections
 *
 * @author yun
 */
public record Subscription(
        String id,
        Destination destination,
        StompHeaders stompHeaders,
        Handler<StompFrames> framesHandler
) {
}
