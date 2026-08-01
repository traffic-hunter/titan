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

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.net.TlsContext;
import org.traffichunter.titan.core.net.TlsSide;
import org.traffichunter.titan.core.resilience.retry.RetryListener;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.Assert;

/**
 * Default builder hidden behind {@link TitanClient#builder()}.
 *
 * <p>The builder stores values only. Native event loops are allocated by {@link #build()} after
 * the implementation has been selected, which prevents a Vert.x client from allocating unused
 * Titan selectors. Protocol values are assembled into {@link StompSessionOption}; connection and
 * reconnect values are assembled into {@link ClientConfiguration}.</p>
 *
 * @author yun
 */
final class DefaultTitanClientBuilder implements TitanClient.Builder {

    private TitanClient.Implementation implementation = TitanClient.Implementation.TITAN;
    private int workers = 1;
    private String host = ClientConfiguration.DEFAULT_HOST;
    private int port = ClientConfiguration.DEFAULT_PORT;
    private StompSessionOption session = StompSessionOption.DEFAULT;
    private Duration connectTimeout = ClientConfiguration.DEFAULT_CONNECT_TIMEOUT;
    private RetryPolicy reconnectPolicy = ClientConfiguration.DEFAULT_RECONNECT_POLICY;
    private RetryListener reconnectListener = RetryListener.NOOP;
    private InetClientOption inetOption = InetClientOption.DEFAULT_INET_CLIENT_OPTION;
    private @Nullable String webSocketPath;
    private @Nullable TlsContext tlsContext;

    @Override
    public TitanClient.Builder implementation(TitanClient.Implementation implementation) {
        this.implementation = implementation;
        return this;
    }

    @Override
    public TitanClient.Builder worker(int workers) {
        Assert.checkArgument(workers > 0, "workers must be greater than zero");
        this.workers = workers;
        return this;
    }

    @Override
    public TitanClient.Builder host(String host) {
        this.host = host;
        return this;
    }

    @Override
    public TitanClient.Builder port(int port) {
        this.port = port;
        return this;
    }

    @Override
    public TitanClient.Builder session(StompSessionOption option) {
        this.session = option;
        return this;
    }

    @Override
    public TitanClient.Builder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public TitanClient.Builder reconnect(RetryPolicy policy) {
        this.reconnectPolicy = policy;
        return this;
    }

    @Override
    public TitanClient.Builder reconnect(RetryPolicy policy, RetryListener listener) {
        this.reconnectPolicy = policy;
        this.reconnectListener = listener;
        return this;
    }

    @Override
    public TitanClient.Builder inetOption(InetClientOption option) {
        this.inetOption = option;
        return this;
    }

    @Override
    public TitanClient.Builder webSocket(String path) {
        this.webSocketPath = path;
        return this;
    }

    @Override
    public TitanClient.Builder tls(TlsContext context) {
        this.tlsContext = context;
        return this;
    }

    @Override
    public TitanClient build() {
        TlsContext context = tlsContext;
        if (context != null && implementation == TitanClient.Implementation.VERTX) {
            throw new UnsupportedOperationException("Vert.x client TLS is not supported by Titan's TLS context");
        }
        if (context != null && context.side() != TlsSide.CLIENT) {
            throw new IllegalArgumentException("TitanClient requires a client-side TLS context");
        }

        ClientConfiguration option = new ClientConfiguration(
                host,
                port,
                session,
                inetOption,
                connectTimeout,
                reconnectPolicy,
                reconnectListener,
                tlsContext,
                webSocketPath
        );

        TitanClient client;
        if (implementation == TitanClient.Implementation.VERTX) {
            client = VertxStompClient.open(option);
        } else {
            // A client only needs secondary I/O loops; it never accepts inbound connections.
            client = TitanStompClient.open(EventLoopGroups.group(workers), option);
        }

        return client;
    }
}
