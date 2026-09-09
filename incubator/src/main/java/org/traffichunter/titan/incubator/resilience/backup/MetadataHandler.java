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
package org.traffichunter.titan.incubator.resilience.backup;

import org.traffichunter.titan.core.util.Handler;

/**
 * Callback invoked for each metadata record recovered from an AOF file.
 *
 * <p>The handler receives immutable byte-array metadata. If a later replay layer converts payload
 * bytes back to Titan {@code Buffer}, that layer owns the created buffer and must release it.</p>
 *
 * @author yun
 */
@FunctionalInterface
public interface MetadataHandler extends Handler<Metadata> {

    /**
     * Handles one decoded metadata record in append order.
     */
    @Override
    void handle(Metadata metadata);
}
