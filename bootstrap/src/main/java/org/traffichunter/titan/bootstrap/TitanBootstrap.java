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

import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.Configurations.Property;

/**
 * @author yungwang-o
 */
@Slf4j
public final class TitanBootstrap {

    private final TitanShutdownHook shutdownHook = new TitanShutdownHook();

    private final Banner.Mode bannerMode = Configurations.banner(Property.BANNER_MODE);
    private final Banner banner = new Banner();

    private final StartUp startUp = new StartUp();

    private final String env;

    TitanBootstrap(final String env) {
        this.env = env;
    }

    private void run() {

        banner.print(bannerMode);


        log.info("Started titan in {} second", startUp.getUpTime());
    }

    public static void run(final String env) {
        new TitanBootstrap(env).run();
    }

    private static class StartUp {

        private final Instant startTime;

        private Instant endTime;

        StartUp() {
            this.startTime = Instant.now();
        }

        public double getUpTime() {
            if(startTime == null && endTime == null) {
                throw new IllegalStateException("startTime and endTime are not set");
            }

            return Duration.between(getStartTime(), getEndTime()).toMillis() / 1_000.0;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public Instant getEndTime() {
            if(endTime == null) {
                endTime = Instant.now();
            }

            return endTime;
        }
    }
}
