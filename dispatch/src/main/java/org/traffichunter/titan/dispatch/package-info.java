/**
 * Dispatch module entry points.
 *
 * <p>The dispatch module delivers each message a producer sends to every client subscribed
 * to the same {@code Destination}:</p>
 *
 * <pre>{@code
 * Producer SEND frame
 *        |
 *        v
 * StompSendToFanoutHandler
 *        |
 *        v
 * Gateway.sparkDispatch(message)
 *        |
 *        v
 * DispatcherQueue per Destination
 *        |
 *        v
 * FanoutDispatcher consumer task
 *        |
 *        v
 * DispatchExporter (STOMP, TCP, ...)
 *        |
 *        v
 * subscribed clients
 * }</pre>
 *
 * <p>{@link org.traffichunter.titan.dispatch.Gateway} routes a message into its queue,
 * {@link org.traffichunter.titan.dispatch.PathFinder} resolves destination queues,
 * {@link org.traffichunter.titan.dispatch.Dispatcher} delivers from the queues, and exporters
 * write to clients in their own protocol, so routing does not depend on the protocol. Gateway
 * and dispatcher are built separately on the same path finder.</p>
 */
@NullMarked
package org.traffichunter.titan.dispatch;

import org.jspecify.annotations.NullMarked;
