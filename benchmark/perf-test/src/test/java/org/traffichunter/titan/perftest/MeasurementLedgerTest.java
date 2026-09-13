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
package org.traffichunter.titan.perftest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.perftest.MeasurementLedger.Reception;

/**
 * @author yun
 */
class MeasurementLedgerTest {

    @Test
    void only_a_confirmed_pre_transport_refusal_is_local_not_sent() {
        MeasurementLedger ledger = new MeasurementLedger(3, 0);
        StompPerfTest.classify(ledger, 0,
                new org.traffichunter.titan.client.ClientException("STOMP client is not connected"));
        StompPerfTest.classify(ledger, 1,
                new org.traffichunter.titan.client.ClientException("Connection failed after submitting"));
        StompPerfTest.classify(ledger, 2, new IllegalStateException("Write outcome is unavailable"));

        MeasurementSnapshot snapshot = ledger.freeze();
        assertThat(snapshot.localNotSent()).isEqualTo(1);
        assertThat(snapshot.unknown()).isEqualTo(2);
        assertThat(snapshot.countsBalanced()).isTrue();
    }

    @Test
    void an_identifier_seen_twice_counts_once_and_leaves_a_duplicate() {
        MeasurementLedger ledger = new MeasurementLedger(2, 0);
        ledger.accepted(0, 10);
        ledger.accepted(1, 20);

        assertThat(ledger.received(0, 100)).isEqualTo(Reception.FIRST);
        assertThat(ledger.received(0, 900)).isEqualTo(Reception.DUPLICATE);
        assertThat(ledger.received(1, 200)).isEqualTo(Reception.FIRST);

        MeasurementSnapshot snapshot = ledger.freeze();
        assertThat(snapshot.received()).isEqualTo(2);
        assertThat(snapshot.duplicates()).isEqualTo(1);
        // The repeat sighting must not pull the latency distribution towards itself.
        assertThat(snapshot.deliveryLatencyNanos()).containsExactly(100, 200);
    }

    @Test
    void a_message_that_arrives_after_a_refusal_is_a_contradiction() {
        MeasurementLedger ledger = new MeasurementLedger(2, 0);
        ledger.localNotSent(0);
        ledger.rejected(1);

        ledger.received(0, 5);
        ledger.received(1, 5);

        MeasurementSnapshot snapshot = ledger.freeze();
        assertThat(snapshot.contradiction()).isEqualTo(2);
        assertThat(snapshot.received()).isEqualTo(2);
    }

    @Test
    void an_accepted_message_that_never_arrives_is_reported_as_missing() {
        MeasurementLedger ledger = new MeasurementLedger(3, 0);
        ledger.accepted(0, 10);
        ledger.accepted(1, 10);
        ledger.unknown(2);
        ledger.received(0, 50);

        MeasurementSnapshot snapshot = ledger.freeze();
        assertThat(snapshot.acceptedNotReceived()).isEqualTo(1);
        assertThat(snapshot.unknown()).isEqualTo(1);
        assertThat(snapshot.countsBalanced()).isTrue();
    }

    @Test
    void an_identifier_never_attempted_is_not_counted_as_an_attempt() {
        MeasurementLedger ledger = new MeasurementLedger(4, 0);
        ledger.accepted(0, 1);
        ledger.writeSubmitted(1, 1);

        MeasurementSnapshot snapshot = ledger.freeze();
        assertThat(snapshot.attempted()).isEqualTo(2);
        assertThat(snapshot.notAttempted()).isEqualTo(2);
        assertThat(snapshot.requested()).isEqualTo(4);
        assertThat(snapshot.countsBalanced()).isTrue();
    }

    @Test
    void nothing_observed_after_the_freeze_reaches_the_report() {
        MeasurementLedger ledger = new MeasurementLedger(2, 1);
        ledger.accepted(0, 10);
        ledger.received(0, 10);

        MeasurementSnapshot snapshot = ledger.freeze();

        assertThat(ledger.received(1, 10)).isEqualTo(Reception.IGNORED);
        assertThat(ledger.warmupReceived(0)).isEqualTo(Reception.IGNORED);
        ledger.accepted(1, 10);
        assertThat(ledger.lateEvents()).isEqualTo(3);
        assertThat(snapshot.received()).isEqualTo(1);
        assertThat(snapshot.accepted()).isEqualTo(1);
        assertThat(snapshot.warmupReceived()).isZero();
        // The same snapshot is what the runner reports; a second freeze must not revive the events.
        assertThat(ledger.freeze().received()).isEqualTo(1);
    }

    @Test
    void a_late_send_and_a_late_message_cannot_race_the_freeze() throws Exception {
        int requested = 2_000;
        MeasurementLedger ledger = new MeasurementLedger(requested, 0);
        ExecutorService writers = Executors.newFixedThreadPool(4);
        CountDownLatch started = new CountDownLatch(4);

        for (int worker = 0; worker < 4; worker++) {
            int offset = worker;
            writers.execute(() -> {
                started.countDown();
                for (int id = offset; id < requested; id += 4) {
                    ledger.accepted(id, id);
                    ledger.received(id, id);
                }
            });
        }
        started.await(5, TimeUnit.SECONDS);

        MeasurementSnapshot snapshot = ledger.freeze();
        writers.shutdown();
        assertThat(writers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // Whatever the writers managed before the freeze, the frozen numbers still add up and no
        // identifier is half recorded.
        assertThat(snapshot.countsBalanced()).isTrue();
        assertThat(snapshot.received()).isLessThanOrEqualTo(snapshot.accepted());
        assertThat(snapshot.received() + snapshot.acceptedNotReceived()).isEqualTo(snapshot.accepted());
        assertThat(snapshot.deliveryLatencyNanos()).hasSize(snapshot.received());
    }

    @Test
    void warm_up_traffic_stays_out_of_the_measurement() {
        MeasurementLedger ledger = new MeasurementLedger(1, 2);
        ledger.accepted(0, 10);
        ledger.received(0, 10);

        assertThat(ledger.warmupReceived(0)).isEqualTo(Reception.FIRST);
        assertThat(ledger.warmupReceived(0)).isEqualTo(Reception.DUPLICATE);
        assertThat(ledger.warmupReceived(1)).isEqualTo(Reception.FIRST);

        MeasurementSnapshot snapshot = ledger.freeze();
        assertThat(snapshot.warmupReceived()).isEqualTo(2);
        assertThat(snapshot.received()).isEqualTo(1);
        assertThat(snapshot.duplicates()).isZero();
    }

    @Test
    void a_message_from_another_run_is_counted_apart() {
        MeasurementLedger ledger = new MeasurementLedger(1, 0);
        ledger.foreignMessage();
        ledger.malformedMessage();
        ledger.received(7, 1);

        MeasurementSnapshot snapshot = ledger.freeze();
        assertThat(snapshot.foreignMessages()).isEqualTo(1);
        assertThat(snapshot.malformedMessages()).isEqualTo(2);
        assertThat(snapshot.received()).isZero();
    }
}
