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
package org.traffichunter.titan.core.util;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Provides typed access to externally supplied option values.
 *
 * <p>The source map is copied when this object is created. Values that already match the
 * requested type are returned directly, while string values can be converted to primitive
 * wrapper types and enums. Missing values are represented by {@code null}; callers that require
 * a value can use {@link #getRequired(String, Class)}.</p>
 *
 * @author yun
 */
public final class OptionValues {

    private final Map<String, Object> options;

    public OptionValues(Map<String, ?> options) {
        Map<String, Object> copied = new HashMap<>(options);
        this.options = Map.copyOf(copied);
    }

    public static OptionValues of(Map<String, ?> options) {
        return new OptionValues(options);
    }

    public boolean contains(String key) {
        return options.containsKey(key);
    }

    public boolean isEmpty() {
        return options.isEmpty();
    }

    public int size() {
        return options.size();
    }

    public @Nullable <V> V get(String key, Class<V> type) {
        Object value = options.get(key);
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return null;
        }
        return value == null ? null : convert(key, value, type);
    }

    public <V> V getOrDefault(String key, Class<V> type, V defaultValue) {
        V value = get(key, type);
        return value == null ? defaultValue : value;
    }

    public <V> V getRequired(String key, Class<V> type) {
        V value = get(key, type);
        if (value == null) {
            throw new IllegalArgumentException("Required option is missing: " + key);
        }
        return value;
    }

    public Map<String, Object> asMap() {
        return options;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <V> V convert(String key, Object value, Class<V> type) {
        Class<?> targetType = boxedType(type);
        if (targetType.isInstance(value)) {
            return (V) value;
        }
        if (!(value instanceof String stringValue)) {
            throw typeMismatch(key, targetType, value);
        }

        String normalized = stringValue.trim();
        try {
            Object converted;
            if (targetType == String.class) {
                converted = stringValue;
            } else if (targetType == Integer.class) {
                converted = Integer.valueOf(normalized);
            } else if (targetType == Long.class) {
                converted = Long.valueOf(normalized);
            } else if (targetType == Boolean.class) {
                if (!normalized.equalsIgnoreCase("true") && !normalized.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Boolean option must be true or false");
                }
                converted = Boolean.valueOf(normalized);
            } else if (targetType == Double.class) {
                converted = Double.valueOf(normalized);
            } else if (targetType == Float.class) {
                converted = Float.valueOf(normalized);
            } else if (targetType == Short.class) {
                converted = Short.valueOf(normalized);
            } else if (targetType == Byte.class) {
                converted = Byte.valueOf(normalized);
            } else if (targetType.isEnum()) {
                converted = Enum.valueOf((Class<? extends Enum>) targetType, normalized);
            } else {
                throw typeMismatch(key, targetType, value);
            }
            return (V) converted;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Invalid value for option '" + key + "' as " + targetType.getSimpleName() + ": " + stringValue,
                    error
            );
        }
    }

    private static Class<?> boxedType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        return type;
    }

    private static IllegalArgumentException typeMismatch(String key, Class<?> type, Object value) {
        return new IllegalArgumentException(
                "Option '" + key + "' must be " + type.getSimpleName()
                        + " but was " + value.getClass().getSimpleName()
        );
    }
}
