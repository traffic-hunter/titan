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
package org.traffichunter.titan.core.spi.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.stomp.StompServer;
import io.vertx.ext.stomp.StompServerHandler;
import io.vertx.ext.stomp.StompServerOptions;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandler;
import org.traffichunter.titan.core.spi.ManagedServer;
import org.traffichunter.titan.core.spi.NetworkServerEngineProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author yun
 */
@Slf4j
public final class VertxStompServerEngineProvider implements NetworkServerEngineProvider {

    @Override
    public String transport() {
        return "vertx";
    }

    @Override
    public String protocol() {
        return "stomp";
    }

    @Override
    public boolean supports(ServerSettings settings) {
        return settings.hasTransport()
                && transport().equalsIgnoreCase(settings.transport())
                && protocol().equalsIgnoreCase(settings.protocol());
    }

    @Override
    public NetworkServerEngineProvider setInboundHandler(ChannelInBoundHandler channelInBoundHandler) {
        return this;
    }

    @Override
    public NetworkServerEngineProvider setOutboundHandler(ChannelOutBoundHandler channelOutBoundHandler) {
        return this;
    }

    @Override
    public ManagedServer create(ServerSettings settings) {
        Vertx vertx = Vertx.vertx(new VertxOptions()
                .setEventLoopPoolSize(settings.primaryThreads())
                .setWorkerPoolSize(settings.secondaryThreads()));
        StompServerOptions stompServerOptions = buildOption(
                settings.resolvedProtocolOptions(),
                settings.resolvedTransportOptions()
        ).setPort(settings.port()).setHost(settings.host());

        StompServer stompServer = StompServer.create(vertx, stompServerOptions)
                .handler(StompServerHandler.create(vertx));

        return new VertxStompManagedServer(stompServer, settings);
    }

    private static StompServerOptions buildOption(Map<String, String> protocolOptions, Map<String, String> transportOptions) {
        StompServerOptions options = new StompServerOptions();

        int maxHeaderLength = intOption(protocolOptions, "max-header-length", options.getMaxHeaderLength());
        int maxHeaders = intOption(protocolOptions, "max-headers", options.getMaxHeaders());
        int maxBodyLength = intOption(protocolOptions, "max-body-length", options.getMaxBodyLength());
        int maxFrameInTransaction = intOption(
                protocolOptions,
                "max-frame-in-transaction",
                options.getMaxFrameInTransaction()
        );
        int timeFactor = intOption(protocolOptions, "time-factor", options.getTimeFactor());
        int transactionChunkSize = intOption(
                protocolOptions,
                "transaction-chunk-size",
                options.getTransactionChunkSize()
        );
        int maxSubscriptionsByClient = intOption(
                protocolOptions,
                "max-subscriptions-by-client",
                options.getMaxSubscriptionsByClient()
        );
        int receiveBufferSize = firstIntOption(
                transportOptions,
                options.getReceiveBufferSize(),
                "receive-buffer-size",
                "child-receive-buffer-size"
        );
        int sendBufferSize = firstIntOption(
                transportOptions,
                options.getSendBufferSize(),
                "send-buffer-size",
                "child-send-buffer-size"
        );
        int acceptBacklog = intOption(transportOptions, "accept-backlog", options.getAcceptBacklog());
        long heartbeatX = longOption(protocolOptions, "heartbeat-x", options.getHeartbeat().getLong("x"));
        long heartbeatY = longOption(protocolOptions, "heartbeat-y", options.getHeartbeat().getLong("y"));
        List<String> supportedVersions = stringListOption(protocolOptions, options.getSupportedVersions());
        String websocketPath = stringOption(protocolOptions, "websocket-path", options.getWebsocketPath());

        options.setMaxHeaderLength(maxHeaderLength);
        options.setMaxHeaders(maxHeaders);
        options.setMaxBodyLength(maxBodyLength);
        options.setMaxFrameInTransaction(maxFrameInTransaction);
        options.setSupportedVersions(supportedVersions);
        options.setTimeFactor(timeFactor);
        options.setTransactionChunkSize(transactionChunkSize);
        options.setMaxSubscriptionsByClient(maxSubscriptionsByClient);
        options.setHeartbeat(new JsonObject()
                .put("x", heartbeatX)
                .put("y", heartbeatY)
        );
        options.setReceiveBufferSize(receiveBufferSize);
        options.setSendBufferSize(sendBufferSize);
        options.setAcceptBacklog(acceptBacklog);
        options.setWebsocketPath(websocketPath);

        options.setSecured(booleanOption(protocolOptions, "secured", false));
        options.setSendErrorOnNoSubscriptions(booleanOption(protocolOptions, "send-error-on-no-subscriptions", false));
        options.setWebsocketBridge(booleanOption(protocolOptions, "websocket-bridge", false));
        options.setTrailingLine(booleanOption(protocolOptions, "trailing-line", false));
        options.setReuseAddress(booleanOption(transportOptions, "reuse-address", true));
        options.setReusePort(booleanOption(transportOptions, "reuse-port", false));
        options.setTcpNoDelay(booleanOption(transportOptions, "tcp-no-delay",
                booleanOption(transportOptions, "child-tcp-no-delay", true)));
        options.setTcpKeepAlive(booleanOption(transportOptions, "tcp-keep-alive",
                booleanOption(transportOptions, "child-keep-alive", false)));

        return options;
    }

    private static int intOption(Map<String, String> options, String key, int defaultValue) {
        String value = options.get(key);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static int firstIntOption(Map<String, String> options, int defaultValue, String... keys) {
        for (String key : keys) {
            String value = options.get(key);
            if (value != null && !value.isBlank()) {
                return Integer.parseInt(value);
            }
        }
        return defaultValue;
    }

    private static long longOption(Map<String, String> options, String key, long defaultValue) {
        String value = options.get(key);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
    }

    private static boolean booleanOption(Map<String, String> options, String key, boolean defaultValue) {
        String value = options.get(key);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    private static String stringOption(Map<String, String> options, String key, String defaultValue) {
        String value = options.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static List<String> stringListOption(Map<String, String> options, List<String> defaultValue) {
        String value = stringOption(options, "supported-versions", "");
        if (value.isBlank()) {
            return defaultValue;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
