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
package org.traffichunter.titan.core.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import org.traffichunter.titan.bootstrap.LifeCycle;
import org.traffichunter.titan.bootstrap.LifeCycle.State;

/**
 * @author yungwang-o
 */
final class DefaultServerNIOConnector implements ServerNIOConnector {

    private ServerSocketChannel serverSocketChannel;

    private Selector selector;

    private final int port;

    private final ServerNIOConnectorState connectorState = new ServerNIOConnectorState();

    public DefaultServerNIOConnector(final int port){
        this.port = port;
        this.connectorState.setState(State.INITIALIZED);
    }

    @Override
    public void open() throws IOException {
        if(!connectorState.isInitialized() || serverSocketChannel == null){
            return;
        }

        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT | SelectionKey.OP_CONNECT);
        connectorState.setState(State.STATING);
    }

    @Override
    public ServerSocketChannel serverSocketChannel() {
        if(!isOpen()) {
            throw new IllegalStateException("connector is closed or not open");
        }

        return serverSocketChannel;
    }

    @Override
    public Selector selector() {
        if(!selector.isOpen() || selector == null) {
            throw new IllegalStateException("selector is not open");
        }

        return selector;
    }

    @Override
    public SelectionKey selectionKey() {
        if(!selector.isOpen() || selector == null) {
            throw new IllegalStateException("selector is not open");
        }

        return serverSocketChannel.keyFor(selector);
    }

    @Override
    public boolean isOpen() {
        return serverSocketChannel != null && serverSocketChannel.isOpen() && connectorState.isStating();
    }

    @Override
    public boolean isClosed() {
        return serverSocketChannel != null && connectorState.isStopped();
    }

    @Override
    public void close() throws IOException {
        if(serverSocketChannel == null) {
            return;
        }

        serverSocketChannel.close();
    }

    public State getState() {
        return connectorState.getState();
    }

    private static class ServerNIOConnectorState extends LifeCycle {

        @Override
        public boolean isInitialized() {
            return state.get() == State.INITIALIZED;
        }

        @Override
        public boolean isStating() {
            return state.get() == State.STATING;
        }

        @Override
        public boolean isStopped() {
            return state.get() == State.STOPPED;
        }

        @Override
        public void setState(final State state) {
            super.state.compareAndSet(super.state.get(), state);
        }
    }
}