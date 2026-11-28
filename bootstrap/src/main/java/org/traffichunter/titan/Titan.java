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
package org.traffichunter.titan;

import org.traffichunter.titan.bootstrap.Configurations;
import org.traffichunter.titan.bootstrap.TitanBootstrap;

/**
 * Command-line entry point for a Titan process.
 *
 * <p>The entry point reads the environment file path from system properties
 * and leaves all startup work to {@link TitanBootstrap}. Embedded tests and
 * other launchers call {@link TitanBootstrap} too, so they start the same way
 * as this main method.</p>
 */
public class Titan {

    public static void main(String[] args) {
        TitanBootstrap.run(Configurations.environment());
    }
}
