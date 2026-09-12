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
package org.traffichunter.titan.client;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.IdGenerator;

/**
 * Turns a group argument into the headers a STOMP frame carries.
 *
 * <p>The group API takes the group as an argument while the transport only understands headers.
 * The caller's map is copied before anything is added to it, so a map the caller keeps editing
 * cannot change a frame already on its way or a subscription already stored for reconnect.</p>
 *
 * @author yun
 */
final class GroupHeaders {

    private GroupHeaders() {
    }

    /**
     * Copies {@code headers} and stamps the resolved group on the copy.
     *
     * <p>The default group leaves no header behind, so a server that predates groups keeps
     * receiving exactly the frame it always received.</p>
     *
     * @param group group name; {@code null} or blank means the default group
     * @param headers headers supplied by the caller, left untouched
     * @return a mutable copy carrying the resolved group
     * @throws IllegalArgumentException when the name is malformed or disagrees with a
     *         {@code group} header the caller also supplied
     */
    static Map<Elements, String> forGroup(String group, Map<Elements, String> headers) {
        Objects.requireNonNull(headers, "headers");
        String resolved = DestinationGroups.normalize(group);

        Map<Elements, String> copied = new EnumMap<>(Elements.class);
        copied.putAll(headers);

        String declared = copied.get(Elements.GROUP);
        if (declared != null && !DestinationGroups.normalize(declared).equals(resolved)) {
            throw new IllegalArgumentException(
                    "Group " + resolved + " conflicts with the group header " + declared
            );
        }

        if (DestinationGroups.isDefault(resolved)) {
            copied.remove(Elements.GROUP);
        } else {
            copied.put(Elements.GROUP, resolved);
        }
        return copied;
    }

    /**
     * Gives a subscription its own identifier unless the caller named one.
     *
     * <p>Without this, two subscriptions to the same destination in different groups would both
     * fall back to the destination as their identifier and collide on one connection.</p>
     *
     * @param headers headers to complete, modified in place
     * @return the same map
     */
    static Map<Elements, String> withSubscriptionId(Map<Elements, String> headers) {
        headers.computeIfAbsent(Elements.ID, ignored -> IdGenerator.uuid());
        return headers;
    }
}
