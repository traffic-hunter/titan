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
package org.traffichunter.titan.core.channel;

import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * I/O event loop responsible for server accept readiness.
 *
 * <p>Accepted sockets are initialized as child {@link NetChannel} instances. The actual
 * read/write registration for those child channels is delegated by the server acceptor to a
 * secondary event loop.</p>
 */
public class ChannelPrimaryIOEventLoop extends SingleThreadIOEventLoop {

    private static final Logger log = LoggerFactory.getLogger(ChannelPrimaryIOEventLoop.class);

    public ChannelPrimaryIOEventLoop() {
        this(EventLoopConstants.PRIMARY_EVENT_LOOP_THREAD_NAME);
    }

    public ChannelPrimaryIOEventLoop(String eventLoopName) {
        super(eventLoopName);
    }

    @Override
    protected void processIO(final Set<SelectionKey> keySet) {
        Iterator<SelectionKey> iter = keySet.iterator();
        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();

            if(!key.isValid()) {
                continue;
            }

            if(key.isAcceptable()) {
                try {
                    NetServerChannel serverChannel = (NetServerChannel) key.attachment();
                    NetChannel channel;
                    while ((channel = serverChannel.internal().accept()) != null) {
                        // The server acceptor initializes the child and assigns its secondary I/O loop.
                        ((AbstractChannel) channel).accept(channel);

                        if(log.isDebugEnabled()) {
                            log.debug("Accepted connection from {}", channel.remoteAddress());
                        }
                    }
                } catch (Throwable e) {
                    log.error("Failed to accept incoming connection", e);
                    key.cancel();
                }
            }
        }
    }
}
