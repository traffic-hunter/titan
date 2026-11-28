/**
 * Append-only backup support for Titan durability.
 *
 * <p>Contains binary record metadata, a record codec, file append and replay operations,
 * fsync policies, and truncated-tail recovery policies. Queue restore, rewrite, manifest, and
 * snapshot integration belong to other layers.</p>
 *
 * @author yun
 */
@NullMarked
package org.traffichunter.titan.incubator.resilience.backup;

import org.jspecify.annotations.NullMarked;
