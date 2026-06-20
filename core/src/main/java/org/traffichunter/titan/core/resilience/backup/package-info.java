/**
 * Append-only backup support for Titan durability.
 *
 * <p>The package contains the first backup log foundation: binary record metadata, record codec,
 * append/replay file access, fsync policy, and truncated-tail recovery policy. Queue restore,
 * rewrite, manifest, and snapshot integration are intentionally left to later layers.</p>
 *
 * @author yun
 */
@NullMarked
package org.traffichunter.titan.core.resilience.backup;

import org.jspecify.annotations.NullMarked;
