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
package org.traffichunter.titan.smoke.titan;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Creates one isolated Titan process controller for each smoke test invocation.
 *
 * @author yun
 */
final class TitanSmokeExtension implements ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(TitanSmokeExtension.class);
    private static final String RUNTIME = "runtime";

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == TitanRuntime.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return store(extensionContext).computeIfAbsent(
                RUNTIME,
                ignored -> new TitanRuntime(),
                TitanRuntime.class
        );
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        TitanRuntime runtime = store(context).remove(RUNTIME, TitanRuntime.class);
        if (runtime == null) {
            return;
        }

        if (context.getExecutionException().isPresent()) {
            context.publishReportEntry("titan-process.log", runtime.logs());
        }
        runtime.close();
    }

    private static ExtensionContext.Store store(ExtensionContext context) {
        return context.getStore(NAMESPACE);
    }
}
