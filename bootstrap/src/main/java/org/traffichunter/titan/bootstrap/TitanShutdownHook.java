/*
 * Copyright 2024 traffic-hunter
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
package org.traffichunter.titan.bootstrap;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers Titan cleanup callbacks with the JVM shutdown hook mechanism.
 *
 * <p>Callbacks may be added by multiple runtime components. When this hook is
 * enabled and registered, the hook itself is installed once with the JVM. At
 * shutdown time it runs every callback that modules contributed during
 * bootstrap.</p>
 */
public class TitanShutdownHook implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TitanShutdownHook.class);

    private final Set<Runnable> shutdownCallbacks = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean registered = new AtomicBoolean();

    private volatile boolean enabledShutdown;

    public synchronized void enableShutdown() {
        this.enabledShutdown = true;
    }

    public boolean isEnabled() {
        return this.enabledShutdown;
    }

    void register() {
        if (!enabledShutdown || !registered.compareAndSet(false, true)) {
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this, "titan-shutdown-hook"));
    }

    @CanIgnoreReturnValue
    public TitanShutdownHook addShutdownCallback(final Runnable callback) {
        shutdownCallbacks.add(callback);
        return this;
    }

    @Override
    public void run() {

        if(!enabledShutdown) {
            return;
        }

        for (Runnable callback : shutdownCallbacks) {
            try {
                callback.run();
            } catch (Exception e) {
                log.warn("Failed to run Titan shutdown callback", e);
            }
        }
    }
}
