/**
 * Mutable YAML binding objects used while loading Titan's runtime configuration.
 *
 * <p>These types are public so SnakeYAML can create and populate them. They are bootstrap
 * implementation details rather than a programmatic configuration API. Applications should use
 * {@link org.traffichunter.titan.bootstrap.TitanBootstrap} or the immutable settings exposed by
 * the bootstrap module.</p>
 *
 * @author yun
 */
package org.traffichunter.titan.bootstrap.environment.property;
