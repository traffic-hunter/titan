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
package org.traffichunter.titan.dispatch;

import java.util.concurrent.atomic.LongAdder;

/**
 * Process-wide counter for messages skipped because a consumer cannot accept more writes.
 *
 * @author yun
 */
public final class SlowConsumerMetrics implements SlowConsumerMetricsMbean {

    private static final SlowConsumerMetrics GLOBAL = createGlobal();

    private final LongAdder skippedMessages = new LongAdder();

    public static SlowConsumerMetrics global() {
        return GLOBAL;
    }

    public void recordSkippedMessage() {
        skippedMessages.increment();
    }

    @Override
    public long getSkippedMessages() {
        return skippedMessages.sum();
    }

    private static SlowConsumerMetrics createGlobal() {
        SlowConsumerMetrics metrics = new SlowConsumerMetrics();
        SlowConsumerMetricsMbeans.register(metrics);
        return metrics;
    }
}
