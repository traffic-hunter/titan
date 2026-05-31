/**
 * Small retry and backoff policies shared across Titan modules.
 *
 * <p>This package only calculates retry eligibility and delay. Transport,
 * reconnect, resubscribe, and listener recovery behavior belongs to the module
 * applying the policy.</p>
 *
 * @author yun
 */
@NullMarked
@Unstable
package org.traffichunter.titan.core.resilience.retry;

import org.jspecify.annotations.NullMarked;
import org.traffichunter.titan.core.util.Unstable;
