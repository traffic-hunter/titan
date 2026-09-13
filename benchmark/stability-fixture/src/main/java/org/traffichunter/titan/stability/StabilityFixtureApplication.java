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
package org.traffichunter.titan.stability;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Runs one stability fixture until it is asked to stop.
 *
 * <p>The manifest is printed on a single prefixed line and, when {@code --manifest} names a file,
 * written there as well. It carries the port, which the fixture is normally left to pick, so the
 * load generator can be pointed at it without a fixed port being reused between runs.</p>
 *
 * @author yun
 */
public final class StabilityFixtureApplication {

    private static final String MANIFEST_PREFIX = "TITAN_STABILITY_FIXTURE=";

    private StabilityFixtureApplication() {
    }

    public static void main(String[] arguments) {
        try {
            StabilityFixtureOptions options = StabilityFixtureOptions.parse(arguments);
            StabilityFixture fixture = StabilityFixture.start(options);
            String manifest = fixture.manifest().toJson();
            if (!options.manifestPath().isBlank()) {
                Files.writeString(Path.of(options.manifestPath()), manifest, StandardCharsets.UTF_8);
            }
            System.out.println(MANIFEST_PREFIX + manifest);
            System.out.flush();
            awaitShutdown(fixture);
        } catch (Exception error) {
            System.err.println("Stability fixture failed: " + error.getMessage());
            System.exit(2);
        }
    }

    /**
     * Blocks until the process is asked to stop, and closes the fixture from the hook itself.
     *
     * <p>A hook that only released this thread would race the exit it is part of: the JVM carries
     * on as soon as the hook returns, and the broker could be left to die with its sockets open.</p>
     */
    private static void awaitShutdown(StabilityFixture fixture) throws InterruptedException {
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                fixture.close();
            } finally {
                stopped.countDown();
            }
        }, "stability-fixture-stop"));
        stopped.await();
    }
}
