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
 * @author yun
 */
public class CompositeDamper implements Damper {

    private final CopyOnWriteArrayList<Damper> dampers = new CopyOnWriteArrayList<>();

    public CompositeDamper add(Damper damper) {
        dampers.addIfAbsent(damper);
        return this;
    }

    public CompositeDamper addAll(Damper... dampers) {
        this.dampers.addAllAbsent(List.of(dampers));
        return this;
    }

    public CompositeDamper remove(Damper damper) {
        dampers.remove(damper);
        return this;
    }

    public CompositeDamper clear() {
        dampers.clear();
        return this;
    }

    public List<Damper> dampers() {
        return List.copyOf(dampers);
    }

    @Override
    public DamperStatus regulate() {
        throw new UnsupportedOperationException("Not supported");
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
        throw new UnsupportedOperationException("Not supported");
    }
}
