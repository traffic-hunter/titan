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
package org.traffichunter.titan.smoke.springframework.smoke.junit;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import org.traffichunter.titan.bootstrap.TitanBootstrap;

/**
 * @author yun
 */
@NullMarked
public final class TitanBootstrapExtension implements BeforeAllCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(TitanBootstrapExtension.class);
    private static final String KEY = "titan-bootstrap-started";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        TitanBootstrapper annotation =
                AnnotationSupport.findAnnotation(context.getRequiredTestClass(), TitanBootstrapper.class).orElse(null);
        if (annotation == null) {
            throw new IllegalStateException("TitanBootstrapper annotation is required");
        }

        ExtensionContext.Store store = context.getRoot().getStore(NS);
        Boolean started = store.get(KEY, Boolean.class);
        if (Boolean.TRUE.equals(started)) {
            return;
        }

        TitanBootstrap.run(resolveEnvironmentPath(annotation.environmentPath()));
        store.put(KEY, Boolean.TRUE);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // Titan server lifecycle is managed by GlobalShutdownHook in core.
    }

    private static String resolveEnvironmentPath(String configuredPath) {
        Path direct = Path.of(configuredPath);
        if (Files.exists(direct)) {
            return direct.toAbsolutePath().toString();
        }

        Path cwd = Path.of("").toAbsolutePath();
        while (cwd != null) {
            Path candidate = cwd.resolve(configuredPath).normalize();
            if (Files.exists(candidate)) {
                return candidate.toString();
            }

            Path titanEnv = cwd.resolve("titan-env.yml");
            if (Files.exists(titanEnv)) {
                return titanEnv.toString();
            }
            cwd = cwd.getParent();
        }

        throw new IllegalStateException("Titan environment file not found: " + configuredPath);
    }
}
