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
package org.traffichunter.titan.bootstrap.environment.proprerty;

import java.util.Objects;

/**
 * Root object used only for YAML binding.
 *
 * <p>The expected top-level document shape is {@code titan: ...}. Runtime code
 * should not depend on this DTO directly; it is mapped to {@code Settings}
 * after parsing.</p>
 */
public class RootYamlProperty {

    private TitanSubProperty titan;

    public RootYamlProperty() {
    }

    public RootYamlProperty(TitanSubProperty titan) {
        this.titan = titan;
    }

    public TitanSubProperty getTitan() {
        return titan;
    }

    public void setTitan(TitanSubProperty titan) {
        this.titan = titan;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof RootYamlProperty other && Objects.equals(titan, other.titan);
    }

    @Override
    public int hashCode() {
        return Objects.hash(titan);
    }

    @Override
    public String toString() {
        return "RootYamlProperty{titan=" + titan + '}';
    }
}
