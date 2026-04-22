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
