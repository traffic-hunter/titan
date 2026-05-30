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
package org.traffichunter.titan.core.transport.stomp.client;

import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.codec.stomp.StompVersion;
import org.traffichunter.titan.core.transport.stomp.TitanStompClient;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;

import java.util.function.Supplier;

/**
 * @author yun
 */
public final class TitanStompClientProvider implements StompClientProvider {

    private final Supplier<EventLoopGroups> eventLoopGroups;

    public TitanStompClientProvider(Supplier<EventLoopGroups> eventLoopGroups) {
        this.eventLoopGroups = eventLoopGroups;
    }

    @Override
    public String name() {
        return "titan";
    }

    @Override
    public String version() {
        return StompVersion.STOMP_1_2.getVersion();
    }

    @Override
    public boolean supports(String transport, String version) {
        return name().equalsIgnoreCase(transport) && version().equals(version);
    }

    @Override
    public StompClient create(StompClientOption option) {
        return TitanStompClient.open(eventLoopGroups.get(), option);
    }
}
