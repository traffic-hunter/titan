/**
 * Titan process bootstrap and runtime settings model.
 *
 * <p>The bootstrap module is intentionally small. It owns process startup,
 * environment loading, and shutdown hook registration, then hands the resolved
 * {@link org.traffichunter.titan.bootstrap.Settings} to the core application.
 * Core server construction stays outside this module to avoid coupling the
 * command-line entry point to transport and protocol implementations.</p>
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
