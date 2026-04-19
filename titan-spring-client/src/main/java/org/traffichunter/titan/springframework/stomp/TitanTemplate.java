package org.traffichunter.titan.springframework.stomp;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.util.buffer.Buffer;

public final class TitanTemplate {

    private final TitanClientManager clientManager;

    public TitanTemplate(TitanClientManager clientManager) {
        this.clientManager = clientManager;
    }

    public StompFrame send(String destination, String payload) throws Exception {
        return send(destination, payload.getBytes(StandardCharsets.UTF_8));
    }

    public StompFrame send(String destination, byte[] payload) throws Exception {
        StompClientConnection connection = resolveConnection();
        return connection.send(destination, Buffer.alloc(payload)).get(30, TimeUnit.SECONDS);
    }

    public StompFrame subscribe(String destination) throws Exception {
        StompClientConnection connection = resolveConnection();
        return connection.subscribe(destination).get(30, TimeUnit.SECONDS);
    }

    public StompFrame disconnect() throws Exception {
        StompClientConnection connection = resolveConnection();
        return connection.disconnect().get(30, TimeUnit.SECONDS);
    }

    private StompClientConnection resolveConnection() throws Exception {
        return clientManager.connection();
    }
}
