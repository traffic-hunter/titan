/**
 * STOMP server engine service-provider implementation.
 *
 * <p>The STOMP module contributes a {@link org.traffichunter.titan.core.spi.NetworkServerEngineProvider}
 * for {@code protocol=stomp} over {@code transport=tcp}. Core bootstrap discovers it through
 * {@link java.util.ServiceLoader} and receives a managed lifecycle wrapper.</p>
 *
 * @author yun
 */
package org.traffichunter.titan.core.spi;
