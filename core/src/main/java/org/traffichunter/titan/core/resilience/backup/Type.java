/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.resilience.backup;

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
