/**
 * Titan process bootstrap and runtime settings model.
 *
 * <p>This module handles process startup, environment loading, and shutdown
 * hook registration, then passes the resolved
 * {@link org.traffichunter.titan.bootstrap.Settings} to the core application.
 * The core module constructs servers, so the command-line entry point does not
 * depend on transport or protocol implementations.</p>
 *
 * <pre>{@code
 * Titan.main
 *     |
 *     v
 * TitanBootstrap.run(environment path)
 *     |
 *     v
 * ConfigurationInitializer.load()
 *     |
 *     v
 * Settings / ServerSettings
 *     |
 *     v
 * org.traffichunter.titan.core.TitanApplication
 * }</pre>
 */
@NullMarked
package org.traffichunter.titan.bootstrap;

import org.jspecify.annotations.NullMarked;
