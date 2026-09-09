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
