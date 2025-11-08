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
package org.traffichunter.titan.core.codec.stomp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.traffichunter.titan.core.transport.stomp.StompServerChannel;

/**
 * @author yungwang-o
 */
@Getter
public final class Transaction {

    private final StompServerChannel serverConnection;
    private final String txId;

    private final List<StompFrame> frames = new ArrayList<>();

    private int DEFAULT_TX_SIZE = 1000;

    private Transaction(final StompServerChannel serverConnection, final String txId) {
        this.serverConnection = serverConnection;
        this.txId = txId;
    }

    public static Transaction create(final StompServerChannel serverConnection, final String txId) {
        return new Transaction(serverConnection, txId);
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
