/**
 * Public Titan client facade, transport drivers, and STOMP connection adapters.
 *
 * <p>{@link org.traffichunter.titan.client.TitanClient} is the application-facing entry point.
 * {@link org.traffichunter.titan.client.StompClientDriver} is the extension point for networking
 * implementations, while package-private connection adapters isolate their native APIs from the
 * facade. Spring and other integrations can therefore depend on {@code TitanClient} without
 * branching on Titan-native or Vert.x connection types.</p>
 *
 * @author yun
 */
package org.traffichunter.titan.client;
