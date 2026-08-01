/**
 * Public Titan client facade and its hidden transport adapters.
 *
 * <p>{@link org.traffichunter.titan.client.TitanClient} is the public entry point. Native Titan
 * and Vert.x implementations, active connection adapters, and runtime configuration remain
 * package-private so Spring and other integrations cannot depend on transport details.</p>
 *
 * @author yun
 */
package org.traffichunter.titan.client;
