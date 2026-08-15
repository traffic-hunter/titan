/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
