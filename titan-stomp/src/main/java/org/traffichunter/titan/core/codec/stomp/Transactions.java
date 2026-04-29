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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;

/**
 * @author yungwang-o
 */
public final class Transactions {

    private final List<Transaction> transactions = new ArrayList<>();

    private static final Transactions INSTANCE = new Transactions();

    public static Transactions getInstance() {
        return INSTANCE;
    }

    public synchronized @Nullable Transaction getTransaction(final StompClientConnection sc, final String txId) {
        return transactions.stream()
                .filter(tx -> tx.getTxId().equals(txId) && tx.getStompClientConnection().equals(sc))
                .findFirst()
                .orElse(null);
    }

    public synchronized boolean registerTransaction(final StompClientConnection sc, final String txId) {
        if(getTransaction(sc, txId) != null) {
            return false;
        }

        return transactions.add(Transaction.create(sc, txId));
    }

    public synchronized boolean removeTransaction(final StompClientConnection sc, final String txId) {
        Transaction tx = getTransaction(sc, txId);
        if (tx == null) {
            return false;
        }

        return transactions.remove(tx);
    }

    @CanIgnoreReturnValue
    public synchronized boolean removeTransactions(@Nullable final StompClientConnection sc) {
        if (sc == null) {
            return false;
        }

        return transactions.removeIf(transaction -> transaction.getStompClientConnection().equals(sc));
    }

    public synchronized void clear() {
        transactions.clear();
    }

    public synchronized int size() {
        return transactions.size();
    }

    private Transactions() {}
}
