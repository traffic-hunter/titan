/*
 * Copyright 2024 yungwang-o
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
package org.traffichunter.titan.bootstrap.environment;

import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

/**
 * SnakeYAML property resolver that accepts kebab-case YAML keys for camel-case
 * Java bean properties.
 *
 * <p>For example, {@code primary-threads} is resolved as
 * {@code primaryThreads}. This keeps configuration files idiomatic for users
 * while preserving normal Java naming in the property DTOs.</p>
 */
final class RelaxedBindingUtils extends PropertyUtils {

    @Override
    public Property getProperty(final Class<?> type, final String name) {
        return super.getProperty(type, kebabToCamel(name));
    }

    private String kebabToCamel(final String kebab) {
        final StringBuilder sb = new StringBuilder();

        String[] split = kebab.split("-");

        for(int i = 0; i < split.length; i++) {
            if(i == 0) {
                sb.append(split[i]);
                continue;
            }
            sb.append(split[i].replaceFirst("^[a-z]", String.valueOf(split[i].charAt(0)).toUpperCase()));
        }

        return sb.toString();
    }
}
