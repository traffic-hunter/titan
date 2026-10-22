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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Applies flow-control operations to a collection of dampers.
 *
 * <p>The composite remains open only while every registered damper is open. Regulation is
 * applied to every damper so each one can refresh its state from the resource it observes.
 * An empty composite is open.
 *
 * @author yun
 */
public class CompositeDamper implements Damper {

    private final CopyOnWriteArrayList<Damper> dampers = new CopyOnWriteArrayList<>();

    /**
     * Adds a damper unless the same instance is already registered.
     *
     * @param damper damper to add
     * @return this composite
     */
    public CompositeDamper add(Damper damper) {
        dampers.addIfAbsent(damper);
        return this;
    }

    /**
     * Adds each damper that is not already registered.
     *
     * @param dampers dampers to add
     * @return this composite
     */
    public CompositeDamper addAll(Damper... dampers) {
        this.dampers.addAllAbsent(List.of(dampers));
        return this;
    }

    /**
     * Removes a registered damper.
     *
     * @param damper damper to remove
     * @return this composite
     */
    public CompositeDamper remove(Damper damper) {
        dampers.remove(damper);
        return this;
    }

    /**
     * Removes every registered damper.
     *
     * @return this composite
     */
    public CompositeDamper clear() {
        dampers.clear();
        return this;
    }

    /**
     * Returns an immutable snapshot of the registered dampers.
     *
     * @return registered dampers in invocation order
     */
    public List<Damper> dampers() {
        return List.copyOf(dampers);
    }

    @Override
    public DamperStatus regulate() {
        DamperStatus status = DamperStatus.OPEN;
        for (Damper damper : dampers) {
            if (damper.regulate() == DamperStatus.CLOSED) {
                status = DamperStatus.CLOSED;
            }
        }
        return status;
    }

    @Override
    public void open() {
        dampers.forEach(Damper::open);
    }

    @Override
    public void close() {
        dampers.forEach(Damper::close);
    }

    @Override
    public DamperStatus getStatus() {
        for (Damper damper : dampers) {
            if (damper.isClosed()) {
                return DamperStatus.CLOSED;
            }
        }
        return DamperStatus.OPEN;
    }
}
