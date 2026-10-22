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
package org.traffichunter.titan.core.resilience.flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompositeDamperTest {

    @Test
    void remains_open_when_empty() {
        CompositeDamper composite = new CompositeDamper();

        assertThat(composite.regulate()).isEqualTo(DamperStatus.OPEN);
        assertThat(composite.getStatus()).isEqualTo(DamperStatus.OPEN);
    }

    @Test
    void regulates_every_damper_and_closes_when_any_damper_closes() {
        TestDamper open = new TestDamper(DamperStatus.OPEN);
        TestDamper closed = new TestDamper(DamperStatus.CLOSED);
        CompositeDamper composite = new CompositeDamper().addAll(open, closed);

        assertThat(composite.regulate()).isEqualTo(DamperStatus.CLOSED);
        assertThat(open.regulations).isEqualTo(1);
        assertThat(closed.regulations).isEqualTo(1);
    }

    @Test
    void propagates_open_and_close_to_every_damper() {
        TestDamper first = new TestDamper(DamperStatus.OPEN);
        TestDamper second = new TestDamper(DamperStatus.OPEN);
        CompositeDamper composite = new CompositeDamper().addAll(first, second);

        composite.close();

        assertThat(composite.getStatus()).isEqualTo(DamperStatus.CLOSED);
        assertThat(first.getStatus()).isEqualTo(DamperStatus.CLOSED);
        assertThat(second.getStatus()).isEqualTo(DamperStatus.CLOSED);

        composite.open();

        assertThat(composite.getStatus()).isEqualTo(DamperStatus.OPEN);
        assertThat(first.getStatus()).isEqualTo(DamperStatus.OPEN);
        assertThat(second.getStatus()).isEqualTo(DamperStatus.OPEN);
    }

    private static final class TestDamper implements Damper {

        private DamperStatus status;
        private int regulations;

        private TestDamper(DamperStatus status) {
            this.status = status;
        }

        @Override
        public DamperStatus regulate() {
            regulations++;
            return status;
        }

        @Override
        public void open() {
            status = DamperStatus.OPEN;
        }

        @Override
        public void close() {
            status = DamperStatus.CLOSED;
        }

        @Override
        public DamperStatus getStatus() {
            return status;
        }
    }
}
