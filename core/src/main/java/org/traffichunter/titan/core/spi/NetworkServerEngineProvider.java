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
package org.traffichunter.titan.core.spi;

import org.traffichunter.titan.bootstrap.ServerSettings;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * @author yun
 */
public interface NetworkServerEngineProvider {

    static List<NetworkServerEngineProvider> load() {
        return ServiceLoader.load(NetworkServerEngineProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    static NetworkServerEngineProvider find(final ServerSettings settings) {
        List<NetworkServerEngineProvider> providers = load().stream()
                .filter(provider -> provider.supports(settings))
                .toList();

        if (providers.size() == 1) {
            return providers.getFirst();
        }

        if (providers.isEmpty()) {
            throw new NoSuchElementException(
                    "No network server provider for protocol=%s transport=%s"
                            .formatted(settings.protocol(), settings.hasTransport() ? settings.transport() : "<auto>")
            );
        }

        throw new IllegalStateException(
                "Ambiguous network server providers for protocol=%s transport=%s: %s"
                        .formatted(
                                settings.protocol(),
                                settings.hasTransport() ? settings.transport() : "<auto>",
                                providers.stream()
                                        .map(provider -> provider.transport() + "/" + provider.protocol())
                                        .collect(Collectors.joining(", "))
                        )
        );
    }

    String transport();

    String protocol();

    ManagedServer create(ServerSettings settings);

    default boolean supports(final ServerSettings settings) {
        if (!protocol().equalsIgnoreCase(settings.protocol())) {
            return false;
        }

        return !settings.hasTransport() || transport().equalsIgnoreCase(settings.transport());
    }
}
