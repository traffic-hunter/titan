/**
 * Protocol-specific fanout exporters.
 *
 * <p>Exporters are the last step of fanout. After the dispatcher takes a message from its
 * queue, an exporter writes it to connected consumers in their protocol.</p>
 *
 * <p>Implementations should copy or retain payload buffers as their channels require. One
 * payload may be written to many clients, so sharing a mutable or consumable buffer across
 * writes is usually not safe.</p>
 */
@NullMarked
package org.traffichunter.titan.dispatch.exporter;

import org.jspecify.annotations.NullMarked;
