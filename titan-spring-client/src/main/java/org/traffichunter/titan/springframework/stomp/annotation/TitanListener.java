/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.springframework.stomp.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as a Titan STOMP message listener.
 * The annotated method is registered against a destination.
 * Listener endpoints are created during Spring bean initialization.
 * Typical listener methods receive a payload, Spring {@code Message}, or {@code StompFrames}.
 *
 * <pre> {@code
 * @TitanListener(destination = "/topic/alerts")
 * void onAlert(String payload) {
 *     // handle message
 * }
 *
 * @TitanListener(group = "market", destination = "/topic/alerts")
 * void onMarketAlert(String payload) {
 *     // handle message published to the market group
 * }
 * }</pre>
 *
 * @author yun
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TitanListener {

    /**
     * Destination group to subscribe within.
     *
     * <p>Blank selects the default group. The value may be a {@code ${...}} property reference,
     * which is resolved against the Spring environment before the listener starts.</p>
     */
    String group() default "";

    String destination();

    String id() default "";

    int concurrency() default 1;

    String clientRef() default "titanClientManager";
}
