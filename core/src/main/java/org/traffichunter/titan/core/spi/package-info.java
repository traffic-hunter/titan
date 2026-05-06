/**
 * Service-provider interfaces for bootstrapping network server engines.
 *
 * <p>Bootstrap code discovers {@link org.traffichunter.titan.core.spi.NetworkServerEngineProvider}
 * implementations through {@link java.util.ServiceLoader}. A provider declares the protocol and
 * transport it supports, builds the concrete server, and exposes it through a
 * {@link org.traffichunter.titan.core.spi.ManagedServer} lifecycle wrapper.</p>
 *
 * <p>This package keeps optional engines and integrations decoupled from the bootstrap module.
 * Additional modules can contribute providers or fanout launchers without introducing direct
 * compile-time dependencies into core.</p>
 *
 * @author yun
 */
@NullMarked
package org.traffichunter.titan.core.spi;

import org.jspecify.annotations.NullMarked;