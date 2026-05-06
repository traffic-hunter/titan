/**
 * Environment loading and relaxed YAML binding.
 *
 * <p>This package turns external configuration files into immutable bootstrap
 * settings. YAML parsing is kept separate from {@code Settings} so SnakeYAML
 * can bind into mutable property objects first, then the initializer can map
 * those objects into validated runtime records.</p>
 */
@NullMarked
package org.traffichunter.titan.bootstrap.environment;

import org.jspecify.annotations.NullMarked;
