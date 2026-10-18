/**
 * STOMP commands, headers, frames, and streaming frame decoding.
 *
 * <p>{@link org.traffichunter.titan.core.codec.stomp.StompChannelDecoder} reads frames from
 * incoming channel data. {@link org.traffichunter.titan.core.codec.stomp.StompFrame} represents a
 * decoded frame and serializes outbound frames. {@link org.traffichunter.titan.core.codec.stomp.StompFrames}
 * is the common frame view used by the native and Vert.x client implementations.</p>
 *
 * <p>These types handle the STOMP protocol, not destination queue storage or replay.</p>
 *
 * @author yun
 */
@NullMarked
package org.traffichunter.titan.core.codec.stomp;

import org.jspecify.annotations.NullMarked;
