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

/**
 * Operation type persisted in the Titan AOF stream.
 *
 * <p>Each enum value has a stable numeric id because records are stored on disk. Do not reorder or
 * reuse ids once a release writes them.</p>
 *
 * @author yun
 */
public enum Type {

    /**
     * A message was appended to a destination queue.
     */
    MESSAGE_APPEND(1),

    /**
     * A destination queue was explicitly created.
     */
    CREATE_QUEUE(2),

    /**
     * A destination queue was explicitly deleted.
     */
    DELETE_QUEUE(3),
    ;

    private final int value;

    Type(int value) {
        this.value = value;
    }

    /**
     * Returns the stable on-disk numeric representation.
     */
    public int getValue() {
        return value;
    }

    /**
     * Resolves a persisted numeric value into a record type.
     */
    public static Type fromValue(short value) {
        for (Type type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new InvalidBackupRecordException("Unknown backup record type: " + value);
    }
}
