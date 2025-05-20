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
import java.nio.channels.SocketChannel;
import org.traffichunter.titan.bootstrap.LifeCycle;
import org.traffichunter.titan.bootstrap.LifeCycle.State;

/**
 * @author yungwang-o
 */
final class DefaultClientNIOConnector implements ClientNIOConnector {

    private SocketChannel socketChannel;

    private final InetSocketAddress socketAddress;

    private final ClientNIOConnectorState state = new ClientNIOConnectorState();

    public DefaultClientNIOConnector(final InetSocketAddress socketAddress) {
        this.socketAddress = socketAddress;
        this.state.setState(State.INITIALIZED);
    }

    @Override
    public void open() throws IOException {
        if(socketChannel != null) {
            return;
        }

        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(socketAddress);

        state.setState(State.STATING);
    }

    @Override
    public SocketChannel socketChannel() {
        if(!isOpen()) {
            throw new IllegalStateException("Client socket is not open");
        }

        return socketChannel;
    }

    @Override
    public boolean isOpen() {
        return socketChannel.isOpen() && state.isStating();
    }

    @Override
    public boolean isConnected() {
        return socketChannel.isConnected();
    }

    @Override
    public boolean isClosed() {
        return state.isStopped();
    }

    @Override
    public void close() throws IOException {
        socketChannel.close();
        state.setState(State.STOPPED);
    }

    private static class ClientNIOConnectorState extends LifeCycle {

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
