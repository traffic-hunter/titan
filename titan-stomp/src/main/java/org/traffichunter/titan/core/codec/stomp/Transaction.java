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
package org.traffichunter.titan.core.codec.stomp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;

/**
 * Transaction is thread safe.
 *
 * @author yungwang-o
 */
public final class Transaction {

    private final StompClientChannel stompClientChannel;
    private final String txId;

    private final List<StompFrame> frames = new ArrayList<>();

    private int DEFAULT_TX_SIZE = 1000;

    private Transaction(final StompClientChannel stompClientChannel, final String txId) {
        this.stompClientChannel = stompClientChannel;
        this.txId = txId;
    }

    public static Transaction create(final StompClientChannel serverConnection, final String txId) {
        return new Transaction(serverConnection, txId);
    }

    public StompClientChannel getStompClientChannel() {
        return stompClientChannel;
    }

    public String getTxId() {
        return txId;
    }

    public synchronized void addFrame(final StompFrame frame) {
        if(frames.size() > DEFAULT_TX_SIZE) {
            return;
        }

        frames.add(frame);
    }

    public synchronized void setTransactionSize(final int txSize) {
        this.DEFAULT_TX_SIZE = txSize;
    }

    public synchronized void clear() {
        frames.clear();
    }

    public synchronized List<StompFrame> getFrames() {
        return Collections.unmodifiableList(frames);
    }
}
