/*
 * The MIT License
 *
 * Copyright (c) 2024 traffic-hunter
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
package org.traffichunter.titan.bootstrap;

import org.traffichunter.titan.bootstrap.Banner.Mode;

/**
 * @author yungwang-o
 */
public final class Configurations {

    public static Banner.Mode banner(final Property property) {
        boolean mode = Boolean.getBoolean(property.value);

        if(!mode) {
            return Mode.OFF;
        }

        return Mode.ON;
    }

    public static int port(final Property property) {
        String port = System.getProperty(property.value);

        if(port == null || port.isEmpty()) {
            return 7777;
        }

        return Integer.parseInt(port);
    }

    public static String environment() {
        String environmentPath = System.getProperty(Property.ENVIRONMENT.value);

        if(environmentPath == null || environmentPath.isEmpty()) {
            return "./titan-env.yml";
        }

        return environmentPath;
    }

    public static int taskPendingCapacity() {
        String property = System.getProperty(Property.EVENTLOOP_PENDING_MAX_CAPACITY.value);

        if(property == null || property.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        return Integer.parseInt(property);
    }

    public enum Property {
        BANNER_MODE("titan.banner.mode"),
        PORT("titan.web.server.port"),
        TRANSPORT_PORT("titan.transport.server.port"),
        ENVIRONMENT("titan.environment.path"),
        EVENTLOOP_PENDING_MAX_CAPACITY("titan.eventloop.pending.capacity"),
        ;

        private final String value;

        Property(final String value) {
            this.value = value;
        }
    }
}
