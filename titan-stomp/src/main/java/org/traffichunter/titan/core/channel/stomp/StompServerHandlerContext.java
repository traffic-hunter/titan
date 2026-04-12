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

import static org.traffichunter.titan.core.codec.stomp.StompFrame.create;

import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.secure.auth.authentication.Authentication;
import org.traffichunter.titan.core.util.secure.auth.authentication.AuthenticationImpl;

public final class StompServerHandlerContext {

    private final StompServerConnection serverConnection;
    private final StompServerOption option;
    private final Authentication authentication;

    public StompServerHandlerContext(StompServerConnection serverConnection) {
        this(serverConnection, new AuthenticationImpl());
    }

    public StompServerHandlerContext(
            StompServerConnection serverConnection,
            Authentication authentication
    ) {
        this.serverConnection = serverConnection;
        this.option = serverConnection.option();
        this.authentication = authentication;
    }

    public StompServerConnection serverConnection() {
        return serverConnection;
    }

    public StompServerOption option() {
        return option;
    }

    public Authentication authentication() {
        return authentication;
    }

    public void receipt(StompFrame frame, StompClientConnection connection) {
        String receipt = frame.getHeader(Elements.RECEIPT);
        if(receipt != null) {
            StompFrame receiptFrame = create(StompHeaders.create(), StompCommand.RECEIPT);
            receiptFrame.addHeader(Elements.RECEIPT_ID, receipt);

            connection.send(receiptFrame);
        }
    }
}
