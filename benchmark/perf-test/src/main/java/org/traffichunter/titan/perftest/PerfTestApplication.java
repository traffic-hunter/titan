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
package org.traffichunter.titan.perftest;

/**
 * Internal process entry point launched by the Go CLI.
 *
 * @author yun
 */
public final class PerfTestApplication {

    private static final String RESULT_PREFIX = "TITAN_PERF_RESULT=";

    private PerfTestApplication() {
    }

    public static void main(String[] arguments) {
        try {
            PerfTestOptions options = PerfTestOptions.parse(arguments);
            System.out.println(RESULT_PREFIX + new StompPerfTest().run(options).toJson());
        } catch (Exception error) {
            System.err.println("Performance test failed: " + error.getMessage());
            System.exit(2);
        }
    }
}
