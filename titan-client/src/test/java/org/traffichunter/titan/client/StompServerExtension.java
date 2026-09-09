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
package org.traffichunter.titan.client;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.traffichunter.titan.dispatch.Dispatcher;
import org.traffichunter.titan.core.transport.stomp.StompServer;

public final class StompServerExtension implements
        BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        AfterEachCallback,
        ParameterResolver {

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(StompServerExtension.class);
    private static final String KEY = "stomp-test-server";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        EnableStompServer config = context.getRequiredTestClass().getAnnotation(EnableStompServer.class);
        if (config == null) {
            throw new IllegalStateException("@EnableStompServer is required");
        }

        context.getStore(NS).put(KEY, new StompTestServer(config));
    }

    @Override
    public void afterAll(ExtensionContext context) {
    }

    @Override
    public void afterEach(ExtensionContext context) {
        StompTestServer testServer = context.getStore(NS).remove(KEY, StompTestServer.class);
        if (testServer != null) {
            testServer.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return type == StompTestServer.class || type == StompServer.class || type == Dispatcher.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        StompTestServer testServer = extensionContext.getStore(NS).get(KEY, StompTestServer.class);
        if (testServer == null) {
            throw new ParameterResolutionException("Stomp test server is not initialized");
        }

        Class<?> type = parameterContext.getParameter().getType();
        if (type == StompServer.class) {
            return testServer.server();
        } else if (type == Dispatcher.class) {
            return testServer.dispatcher();
        }

        return testServer;
    }
}
