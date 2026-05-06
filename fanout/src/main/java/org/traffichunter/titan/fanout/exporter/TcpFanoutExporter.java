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
package org.traffichunter.titan.fanout.exporter;

import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.fanout.CompletableResult;

import java.util.List;

/**
 * Fanout exporter that writes raw payload buffers to every active TCP child
 * channel owned by an {@link InetServer}.
 *
 * <p>This exporter is intentionally transport-level. It does not inspect
 * protocol subscriptions; all active child channels are considered eligible
 * consumers. Use protocol-aware exporters, such as {@link StompFanoutExporter},
 * when delivery must be scoped by session subscription state.</p>
 */
public class TcpFanoutExporter implements FanoutExporter {

    private final InetServer inetServer;

    public TcpFanoutExporter(InetServer inetServer) {
        this.inetServer = Assert.checkNotNull(inetServer, "inetServer");
    }

    @Override
    public String name() {
        return "inet";
    }

    @Override
    public CompletableResult export(Destination destination, Buffer payload) {
        Assert.checkState(inetServer.isStart(), "Cannot send an unstarted inet server");

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        for (NetChannel channel : inetServer.childChannel().stream().toList()) {
            if (!channel.isActive() || channel.isClosed()) {
                continue;
            }

            attempted++;
            Buffer copiedPayload = payload.copy();
            try {
                channel.writeAndFlush(copiedPayload);
                succeeded++;
            } catch (Exception e) {
                copiedPayload.release();
                failed++;
            }
        }

        Promise<CompletableResult> resultPromise = Promise.newPromise(inetServer.channel().eventLoop());
        return CompletableResult.completed(
                List.of(destination),
                attempted,
                succeeded,
                failed,
                resultPromise
        );
    }
}
