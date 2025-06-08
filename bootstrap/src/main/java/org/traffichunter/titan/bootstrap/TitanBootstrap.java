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

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.Configurations.Property;
import org.traffichunter.titan.bootstrap.environment.ConfigurationInitializer;

/**
 * @author yungwang-o
 */
@Slf4j
public final class TitanBootstrap {

    private static final String CALL_CORE_APPLICATION =
            "org.traffichunter.titan.core.CoreApplication";

    private final TitanShutdownHook shutdownHook = new TitanShutdownHook();

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

        final  StartUp startUp = new StartUp();

        banner.print(bannerMode);

        Settings settings = configurationInitializer.load();

        try {
            ApplicationStarter applicationStarter = invokeCoreApplication(TitanBootstrap.class.getClassLoader());

            applicationStarter.start(settings);
        } catch (Exception e) {
            log.error("Failed bootstrapping titan = {}", e.getMessage());
            throw new BootstrapException("Failed bootstrapping titan", e);
        }

        log.info("Started titan in {} second", startUp.getUpTime().toMillis() / 1_000.0);
    }

    public static void run(final String env) {
        new TitanBootstrap(env).run();
    }

    private static class StartUp extends StopWatch {

        public StartUp() {
            super();
        }

        @Override
        public Instant getStartTime() {
            return this.startTime;
        }

        @Override
        public Instant getEndTime() {
            if(endTime == null) {
                this.endTime = Instant.now();
            }
            return endTime;
        }

        @Override
        public Duration getUpTime() {
            if(getStartTime() == null && getEndTime() == null) {
                throw new IllegalStateException("No start time or end time specified");
            }

            return Duration.between(getStartTime(), getEndTime());
        }
    }

    /**
     * To avoid module cyclic dependencies, reflection was used.
     */
    private ApplicationStarter invokeCoreApplication(final ClassLoader classLoader) throws Exception {

        Class<?> coreApp = classLoader.loadClass(CALL_CORE_APPLICATION);

        Constructor<?> constructor = coreApp.getDeclaredConstructor();

        return (ApplicationStarter) constructor.newInstance();
    }

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
