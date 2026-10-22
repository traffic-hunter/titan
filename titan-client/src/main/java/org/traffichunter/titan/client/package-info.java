/**
 * Public Titan client facade and endpoint configuration.
 *
 * <p>{@link org.traffichunter.titan.client.TitanClient} is the application-facing entry point.
 * Transport drivers, workers, subscription state, and physical STOMP connection adapters remain
 * package-private. Spring and other integrations can therefore depend on {@code TitanClient}
 * without branching on Titan-native or Vert.x connection types.</p>
 *
 * @author yun
 */
package org.traffichunter.titan.client;
