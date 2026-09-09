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

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.bootstrap.Configurations.Property;
import org.traffichunter.titan.bootstrap.environment.ConfigurationInitializer;

/**
 * Loads configuration and starts the core application.
 *
 * <p>It loads environment settings, prints the banner, uses {@link BootState}
 * to reject duplicate starts, and invokes the core application by reflection.</p>
 *
 * <p>Bootstrap loads first. Reflection lets it start the core application without
 * depending directly on core transport and protocol packages. The modules share only
 * {@link ApplicationStarter#start(Settings)}.</p>
 */
public final class TitanBootstrap {

    private static final Logger log = LoggerFactory.getLogger(TitanBootstrap.class);

    private static final String CALL_CORE_APPLICATION =
            "org.traffichunter.titan.core.TitanApplication";

    private final Banner.Mode bannerMode = Configurations.banner(Property.BANNER_MODE);
    private final Banner banner = new Banner();

    private final ConfigurationInitializer configurationInitializer;

    private final BootState BOOT_STATE = new BootState();

    TitanBootstrap(final String configPath) {
        this.configurationInitializer = ConfigurationInitializer.getDefault(configPath);
    }

    private void run() {

        final boolean isStarted = BOOT_STATE.start();
        if(!isStarted) {
            log.error("Failed to start titan");
            return;
        }

        final StartUp startUp = new StartUp();

        banner.print(bannerMode);

        Settings settings = configurationInitializer.load();

        try {
            ApplicationStarter applicationStarter = invokeCoreApplication(TitanBootstrap.class.getClassLoader());

            GlobalShutdownHook.INSTANCE.registerShutdownHook();
            applicationStarter.start(settings);
        } catch (Exception e) {
            log.error("Failed bootstrapping titan = {}", e.getMessage());
            throw new BootstrapException("Failed bootstrapping titan", e);
        }

        startUp.setEndTime();
        log.info("Started titan in {} second", startUp.toMillis() / 1_000.0);
    }

    public static void run(final String env) {
        new TitanBootstrap(env).run();
    }

    private static class StartUp extends StopWatch {

        public StartUp() {
            super();
        }

        @Override
        public Duration getUpTime() {
            return Duration.between(getStartTime(), getEndTime());
        }

        public long toMillis() {
            return getUpTime().toMillis();
        }

        public long toSeconds() {
            return getUpTime().toSeconds();
        }
    }

    /**
     * Loads the core application without creating a compile-time dependency
     * from bootstrap back into the core runtime module.
     */
    private ApplicationStarter invokeCoreApplication(final ClassLoader classLoader) throws Exception {

        Class<?> coreApp = classLoader.loadClass(CALL_CORE_APPLICATION);

        Constructor<?> constructor = coreApp.getDeclaredConstructor();

        return (ApplicationStarter) constructor.newInstance();
    }

    /**
     * Contract implemented by the runtime module that bootstrap starts.
     *
     * <p>Implementations live outside the bootstrap module. Bootstrap is
     * responsible for loading and normalizing {@link Settings}; the starter is
     * responsible for turning those settings into concrete runtime components
     * such as managed servers, transports, and protocol handlers.</p>
     */
    public interface ApplicationStarter {

        void start(Settings settings);
    }

    static class BootstrapException extends RuntimeException {

        public BootstrapException() {
            super();
        }

        public BootstrapException(final String message) {
            super(message);
        }

        public BootstrapException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public BootstrapException(final Throwable cause) {
            super(cause);
        }
    }
}
