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
package org.traffichunter.titan.core.util.management;

/**
 * Immutable snapshot of JVM heap memory usage, expressed in bytes.
 *
 * <p>Some JVMs report an undefined maximum value. In that case,
 * {@link #limit()} falls back to committed memory so callers can still derive
 * a usable pressure ratio.</p>
 *
 * @param init initial heap size, or a negative value when undefined
 * @param used currently used heap size
 * @param committed heap size guaranteed to be available to the JVM
 * @param max maximum heap size, or a negative value when undefined
 * @author yun
 */
public record HeapResource(long init, long used, long committed, long max) {

    /**
     * Returns the effective upper bound used for pressure calculations.
     *
     * @return maximum heap size when defined, otherwise committed heap size
     */
    public long limit() {
        return max > 0 ? max : committed;
    }

    /**
     * Returns current heap usage as a value between {@code 0.0} and
     * {@code 1.0}. Invalid or unavailable limits produce {@code 0.0}.
     *
     * @return normalized heap usage
     */
    public double usage() {
        long limit = limit();
        if (limit <= 0 || used <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) used / limit);
    }
}
