/**
 * Protocol-specific fanout exporters.
 *
 * <p>Exporters perform the final step of fanout. After destination selection and
 * dequeueing, an exporter converts the message to the protocol used by connected
 * consumers.</p>
 *
 * <p>Exporter implementations should copy or retain payload buffers according
 * to the channel semantics they use. A single fanout payload may be written to
 * many clients, so sharing a mutable or consumable buffer across writes is not
 * generally safe.</p>
 */
@NullMarked
package org.traffichunter.titan.dispatch.exporter;

import org.jspecify.annotations.NullMarked;
