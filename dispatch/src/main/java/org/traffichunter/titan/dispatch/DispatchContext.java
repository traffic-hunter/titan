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
package org.traffichunter.titan.dispatch;

import org.traffichunter.titan.core.message.Message;

/**
 * Mutable state shared while one dispatch operation moves through a handler chain.
 *
 * <p>The context retains the producer {@link Message} while handlers route, observe, and fan out
 * one dispatch operation. A routing failure terminates traversal by throwing rather than storing
 * a nullable routing result.</p>
 *
 * @author yun
 */
public class DispatchContext {

    private final Message message;

    public DispatchContext(Message message) {
        this.message = message;
    }

    public Message getMessage() {
        return message;
    }
}
