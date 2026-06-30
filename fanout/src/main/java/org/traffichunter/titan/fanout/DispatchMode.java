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
package org.traffichunter.titan.fanout;

import lombok.Getter;
import org.traffichunter.titan.fanout.exporter.DispatchExporter;

/**
 * Execution strategy used by a dispatch gateway.
 *
 * <p>The mode intentionally selects only the threading model. Routing,
 * destination ownership, and exporter behavior remain the same for every mode,
 * so protocol launchers can switch execution strategy without changing the
 * fanout contract.</p>
 */
@Getter
public enum DispatchMode {

    PLATFORM_EXECUTOR("platform") {
        @Override
        public DispatchGateway dispatchGateway(DispatchExporter dispatchExporter) {
            return DispatchGateway.ofThread(dispatchExporter);
        }
    },
    VT_EXECUTOR("virtual") {
        @Override
        public DispatchGateway dispatchGateway(DispatchExporter dispatchExporter) {
            return DispatchGateway.ofVirtual(dispatchExporter);
        }
    },
    ;

    private final String name;

    DispatchMode(String name) {
        this.name = name;
    }

    public abstract DispatchGateway dispatchGateway(DispatchExporter dispatchExporter);

    public static DispatchMode resolveMode(String modeName) {
        return switch (modeName) {
            case "platform" -> DispatchMode.PLATFORM_EXECUTOR;
            case "virtual" -> DispatchMode.VT_EXECUTOR;
            default -> throw new IllegalStateException("Unexpected value: " + modeName);
        };
    }
}
