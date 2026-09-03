/**
 * Append-only backup support for Titan durability.
 *
 * <p>Contains binary record metadata, a record codec, file append and replay operations,
 * fsync policies, and truncated-tail recovery policies. Other layers are responsible for
 * queue restore, rewrite, manifest, and snapshot integration.</p>
 *
 * @author yun
 */
@NullMarked
package org.traffichunter.titan.incubator.resilience.backup;

import org.jspecify.annotations.NullMarked;
