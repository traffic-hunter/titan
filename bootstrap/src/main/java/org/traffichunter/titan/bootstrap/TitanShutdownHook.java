/*
 * The MIT License
 *
 * Copyright (c) 2024 traffic-hunter
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
package org.traffichunter.titan.bootstrap;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers Titan cleanup callbacks with the JVM shutdown hook mechanism.
 *
 * <p>Callbacks may be added by multiple runtime components. When this hook is
 * enabled and run, each callback is registered as a JVM shutdown hook thread.
 * The indirection allows bootstrap code to decide when shutdown integration is
 * active while still letting modules contribute their own cleanup work.</p>
 */
public class TitanShutdownHook implements Runnable {

    private final Set<Runnable> shutdownCallbacks = ConcurrentHashMap.newKeySet();

    private volatile boolean enabledShutdown;

    public synchronized void enableShutdown() {
        this.enabledShutdown = true;
    }

    public boolean isEnabled() {
        return this.enabledShutdown;
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

        for(Runnable callback : shutdownCallbacks) {
            Runtime.getRuntime().addShutdownHook(new Thread(callback));
        }
    }
}
