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
package org.traffichunter.titan.bootstrap.environment.proprerty.sub;

import java.util.Objects;

/**
 * YAML DTO for the optional HTTP server section.
 */
public class HttpServerProperty {

    private int port;

    private String pool;

    public HttpServerProperty() {
    }

    public HttpServerProperty(int port, String pool) {
        this.port = port;
        this.pool = pool;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPool() {
        return pool;
    }

    public void setPool(String pool) {
        this.pool = pool;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof HttpServerProperty other
                && port == other.port
                && Objects.equals(pool, other.pool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(port, pool);
    }

    @Override
    public String toString() {
        return "HttpServerProperty{port=" + port + ", pool='" + pool + "'}";
    }
}
