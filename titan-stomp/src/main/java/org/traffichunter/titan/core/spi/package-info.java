/**
 * STOMP server engine service-provider implementation.
 *
 * <p>The STOMP module contributes {@link org.traffichunter.titan.core.spi.NetworkServerEngineProvider}
 * implementations for {@code protocol=stomp} over {@code transport=tcp} and
 * {@code transport=websocket}. Core bootstrap discovers them through
 * {@link java.util.ServiceLoader} and receives a managed lifecycle wrapper.</p>
 *
 * @author yun
 */
package org.traffichunter.titan.core.spi;
