/**
 * Protocol-specific fanout exporters.
 *
 * <p>An exporter is the final delivery boundary of fanout. The gateway has
 * already selected a destination and dequeued a message; the exporter converts
 * that message into the protocol surface required by currently connected
 * consumers.</p>
 *
 * <p>Exporter implementations should copy or retain payload buffers according
 * to the channel semantics they use. A single fanout payload may be written to
 * many clients, so sharing a mutable or consumable buffer across writes is not
 * generally safe.</p>
 */
@NullMarked
package org.traffichunter.titan.fanout.exporter;

import org.jspecify.annotations.NullMarked;
