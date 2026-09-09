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
package org.traffichunter.titan.bootstrap;

/**
 * Process-wide access point for Titan shutdown callbacks.
 *
 * <p>The enum singleton keeps callback registration centralized without
 * exposing the mutable callback set. Runtime modules can register cleanup work
 * through this facade, while {@link TitanShutdownHook} owns the actual JVM hook
 * integration.</p>
 */
public enum GlobalShutdownHook {
    INSTANCE;

    private final TitanShutdownHook shutdownHook = new TitanShutdownHook();

    public void enableShutdownHook() {
        shutdownHook.enableShutdown();
    }

    public boolean isEnabled() {
        return shutdownHook.isEnabled();
    }

    public void registerShutdownHook() {
        shutdownHook.register();
    }

    public void addShutdownCallback(final Runnable runnable) {
        if (shutdownHook.isEnabled()) {
            shutdownHook.addShutdownCallback(runnable);
        }
    }
}
