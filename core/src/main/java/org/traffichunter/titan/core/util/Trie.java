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
package org.traffichunter.titan.core.util;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * @author yungwang-o
 */
public interface Trie<T> {

    T insert(String word, T value);

    @Nullable T get(String word);

    List<T> searchAll();

    List<T> searchAll(String word);

    boolean startsWith(String prefix);

    T computeIfAbsent(String word, Function<? super String, ? extends T> mappingFunction);

    @Nullable T putIfAbsent(String word, T value);

    /** Removes and returns the value for {@code word}, or {@code null} when no value exists. */
    @Nullable T remove(String word);

    boolean isEmpty();
}
