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
package org.traffichunter.titan.core.channel.stomp;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.secure.auth.authentication.AuthenticationImpl;

@Slf4j
public final class StompServerHandlerImpl implements StompServerHandler {

    private final StompServerHandlerContext context;

    private Handler<StompServerEvent> receivedFrameHandler = event -> {};
    private StompServerCommandHandler connectHandler = new DefaultStompServerHandlers.DefaultConnectHandler();
    private StompServerCommandHandler disconnectHandler = new DefaultStompServerHandlers.DefaultDisconnectHandler();
    private StompServerCommandHandler subscribeHandler = new DefaultStompServerHandlers.DefaultSubscribeHandler();
    private StompServerCommandHandler unsubscribeHandler = new DefaultStompServerHandlers.DefaultUnsubscribeHandler();
    private StompServerCommandHandler sendHandler = new DefaultStompServerHandlers.DefaultSendHandler();
    private StompServerCommandHandler ackHandler = new DefaultStompServerHandlers.DefaultAckHandler();
    private StompServerCommandHandler nackHandler = new DefaultStompServerHandlers.DefaultNackHandler();
    private StompServerCommandHandler beginHandler = new DefaultStompServerHandlers.DefaultBeginHandler();
    private StompServerCommandHandler abortHandler = new DefaultStompServerHandlers.DefaultAbortHandler();
    private StompServerCommandHandler commitHandler = new DefaultStompServerHandlers.DefaultCommitHandler();
    private StompServerCommandHandler pingHandler = new DefaultStompServerHandlers.DefaultPingHandler();

    public StompServerHandlerImpl(final StompServerConnection serverConnection) {
        this.context = new StompServerHandlerContext(serverConnection, new AuthenticationImpl());
    }

    @Override
    public void handle(final StompFrame frame, final StompClientConnection connection) {
        connection.setLastActivatedAt();
        StompServerEvent event = new StompServerEvent(frame, connection);
        receivedFrameHandler.handle(event);

        switch (frame.getCommand()) {
            case STOMP, CONNECT -> connectHandler.handle(event, context);
            case DISCONNECT -> disconnectHandler.handle(event, context);
            case SUBSCRIBE -> subscribeHandler.handle(event, context);
            case UNSUBSCRIBE -> unsubscribeHandler.handle(event, context);
            case ACK -> ackHandler.handle(event, context);
            case NACK -> nackHandler.handle(event, context);
            case BEGIN -> beginHandler.handle(event, context);
            case ABORT -> abortHandler.handle(event, context);
            case COMMIT -> commitHandler.handle(event, context);
            case SEND -> sendHandler.handle(event, context);
            case PING -> pingHandler.handle(event, context);
            default -> throw new StompException("Unknown command: " + frame.getCommand());
        }
    }

    @Override
    public StompServerHandler receivedFrameHandler(Handler<StompServerEvent> handler) {
        this.receivedFrameHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler connectHandler(StompServerCommandHandler handler) {
        this.connectHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler disconnectHandler(StompServerCommandHandler handler) {
        this.disconnectHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler subscribeHandler(StompServerCommandHandler handler) {
        this.subscribeHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler unsubscribeHandler(StompServerCommandHandler handler) {
        this.unsubscribeHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler sendHandler(StompServerCommandHandler handler) {
        this.sendHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler ackHandler(StompServerCommandHandler handler) {
        this.ackHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler nackHandler(StompServerCommandHandler handler) {
        this.nackHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler beginHandler(StompServerCommandHandler handler) {
        this.beginHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler abortHandler(StompServerCommandHandler handler) {
        this.abortHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler commitHandler(StompServerCommandHandler handler) {
        this.commitHandler = handler;
        return this;
    }

    @Override
    public StompServerHandler pingHandler(StompServerCommandHandler handler) {
        this.pingHandler = handler;
        return this;
    }
}
