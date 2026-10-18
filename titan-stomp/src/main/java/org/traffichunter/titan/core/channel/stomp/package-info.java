/**
 * STOMP channels and inbound frame handlers.
 *
 * <p>TCP and WebSocket channels use the same frame handlers. The handlers pass decoded commands
 * and their connection to registered callbacks. Destination routing and fanout live in the
 * dispatch module.</p>
 *
 * @author yun
 */
@NullMarked
package org.traffichunter.titan.core.channel.stomp;

import org.jspecify.annotations.NullMarked;
