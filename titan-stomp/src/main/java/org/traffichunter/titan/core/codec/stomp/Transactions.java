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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;

/**
 * @author yungwang-o
 */
public final class Transactions {

    private final List<Transaction> transactions = new ArrayList<>();

    private static final Transactions INSTANCE = new Transactions();

    public static Transactions getInstance() {
        return INSTANCE;
    }

    public synchronized @Nullable Transaction getTransaction(final StompClientChannel sc, final String txId) {
        return transactions.stream()
                .filter(tx -> tx.getTxId().equals(txId) && tx.getStompClientChannel().equals(sc))
                .findFirst()
                .orElse(null);
    }

    public synchronized boolean registerTransaction(final StompClientChannel sc, final String txId) {
        if(getTransaction(sc, txId) != null) {
            return false;
        }

        return transactions.add(Transaction.create(sc, txId));
    }

    public synchronized boolean removeTransaction(final StompClientChannel sc, final String txId) {
        Transaction tx = getTransaction(sc, txId);
        if (tx == null) {
            return false;
        }

        return transactions.remove(tx);
    }

    @CanIgnoreReturnValue
    public synchronized boolean removeTransactions(@Nullable final StompClientChannel sc) {
        if (sc == null) {
            return false;
        }

        return transactions.removeIf(transaction -> transaction.getStompClientChannel().equals(sc));
    }

    public synchronized void clear() {
        transactions.clear();
    }

    public synchronized int size() {
        return transactions.size();
    }

    private Transactions() {}
}
