/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.transport.stomp;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;

public final class StompServerExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(StompServerExtension.class);
    private static final String KEY = "stomp-test-server";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        EnableStompServer config = context.getRequiredTestClass().getAnnotation(EnableStompServer.class);
        if (config == null) {
            throw new IllegalStateException("@EnableStompServer is required");
        }

        EventLoopGroups groups = EventLoopGroups.group(config.primaryThreads(), config.secondaryThreads());
        StompServerOption serverOption = StompServerOption.builder()
                .maxBodyLength(config.maxFrameLength())
                .build();

        StompServer server = StompServer.builder()
                .group(groups)
                .option(serverOption)
                .build();

        server.start();
        server.listen(config.host(), config.port()).get(3, TimeUnit.SECONDS);

        StompTestServer testServer = new StompTestServer(config.host(), config.port(), server);
        context.getStore(NS).put(KEY, testServer);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        StompTestServer testServer = context.getStore(NS).remove(KEY, StompTestServer.class);
        if (testServer != null) {
            testServer.server().shutdown();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return type == StompTestServer.class || type == StompServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        StompTestServer testServer = extensionContext.getStore(NS).get(KEY, StompTestServer.class);
        if (testServer == null) {
            throw new ParameterResolutionException("Stomp test server is not initialized");
        }

        if (parameterContext.getParameter().getType() == StompServer.class) {
            return testServer.server();
        }

        return testServer;
    }
}
