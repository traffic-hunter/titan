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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandler;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Service-provider contract for network server engines.
 *
 * <p>Providers are discovered with {@link ServiceLoader}. Each provider declares a protocol and
 * transport pair, translates {@link ServerSettings} into concrete server options, and returns a
 * {@link ManagedServer} that bootstrap code can start and stop uniformly.</p>
 *
 * @author yun
 */
public interface NetworkServerEngineProvider {

    /**
     * Loads all provider implementations visible to the current class loader.
     */
    static List<NetworkServerEngineProvider> load() {
        return ServiceLoader.load(NetworkServerEngineProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    /**
     * Finds the single provider matching protocol and optional transport settings.
     */
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

    /**
     * Transport name, for example {@code tcp}.
     */
    String transport();

    /**
     * Protocol name, for example {@code stomp}.
     */
    String protocol();

    @CanIgnoreReturnValue
    NetworkServerEngineProvider setInboundHandler(ChannelInBoundHandler channelInBoundHandler);

    @CanIgnoreReturnValue
    NetworkServerEngineProvider setOutboundHandler(ChannelOutBoundHandler channelOutBoundHandler);

    /**
     * Creates a managed server instance from bootstrap settings.
     */
    ManagedServer create(ServerSettings settings);

    default boolean supports(final ServerSettings settings) {
        if (!protocol().equalsIgnoreCase(settings.protocol())) {
            return false;
        }

        return !settings.hasTransport() || transport().equalsIgnoreCase(settings.transport());
    }
}
