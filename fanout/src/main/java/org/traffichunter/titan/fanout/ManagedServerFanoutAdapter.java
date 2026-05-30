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

import org.traffichunter.titan.core.spi.ManagedServer;
import org.traffichunter.titan.fanout.exporter.FanoutExporter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;

/**
 * Adapter SPI for installing fanout behavior into a concrete managed server.
 */
public interface ManagedServerFanoutAdapter {

    static List<ManagedServerFanoutAdapter> load() {
        return ServiceLoader.load(ManagedServerFanoutAdapter.class, ManagedServerFanoutAdapter.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(ManagedServerFanoutAdapter::order))
                .toList();
    }

    default int order() {
        return 0;
    }

    boolean supports(
            String protocol,
            String transport,
            Map<String, String> protocolOptions,
            ManagedServer managedServer
    );

    void apply(
            String protocol,
            String transport,
            Map<String, String> protocolOptions,
            ManagedServer managedServer,
            Function<FanoutExporter, FanoutGateway> gatewayFactory
    );
}
