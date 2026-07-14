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

import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.util.Handler;

/**
 * STOMP client channel backed by an upgraded WebSocket connection.
 *
 * <p>The STOMP session, subscriptions, receipts, and heartbeat lifecycle are
 * identical to the TCP implementation. The wrapped {@link WebSocketChannel}
 * supplies the transport codec installed during the HTTP upgrade.</p>
 *
 * @author yun
 */
public final class StompClientWebSocketChannel extends StompClientTcpChannel {

    public StompClientWebSocketChannel(
            WebSocketChannel channel,
            StompClientOption option
    ) {
        this(channel, option, handler -> { });
    }

    public StompClientWebSocketChannel(
            WebSocketChannel channel,
            StompClientOption option,
            Handler<StompClientHandler> clientHandlerConfigurer
    ) {
        super(channel, option, clientHandlerConfigurer);
    }

    @Override
    public WebSocketChannel channel() {
        NetChannel channel = super.channel();
        return (WebSocketChannel) channel;
    }
}
