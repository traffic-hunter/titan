/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    private final StompServerChannel serverConnection;
    private final StompServerOption option;
    private final Authentication authentication;

    public StompServerHandlerContext(StompServerChannel serverConnection) {
        this(serverConnection, new AuthenticationImpl());
    }

    public StompServerHandlerContext(
            StompServerChannel serverConnection,
            Authentication authentication
    ) {
        this.serverConnection = serverConnection;
        this.option = serverConnection.option();
        this.authentication = authentication;
    }

    public StompServerChannel serverConnection() {
        return serverConnection;
    }

    public StompServerOption option() {
        return option;
    }

    public Authentication authentication() {
        return authentication;
    }

    public void receipt(StompFrame frame, StompClientChannel connection) {
        String receipt = frame.getHeader(Elements.RECEIPT);
        if(receipt != null) {
            StompFrame receiptFrame = create(StompHeaders.create(), StompCommand.RECEIPT);
            receiptFrame.addHeader(Elements.RECEIPT_ID, receipt);

            connection.send(receiptFrame);
        }
    }
}
