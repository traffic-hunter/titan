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
package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;

/**
 * @author yun
 */
public class Subscription {

    private final String group;
    private final Destination destination;
    private final String id;

    /** Subscription in the default group. */
    public Subscription(Destination destination, String id) {
        this(DestinationGroups.DEFAULT, destination, id);
    }

    public Subscription(String group, Destination destination, String id) {
        this.group = group;
        this.destination = destination;
        this.id = id;
    }

    public String getGroup() {
        return group;
    }

    public Destination getDestination() {
        return destination;
    }

    public String getId() {
        return id;
    }
}
