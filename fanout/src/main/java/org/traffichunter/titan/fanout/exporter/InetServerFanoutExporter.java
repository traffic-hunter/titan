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
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.fanout.FanoutResult;

/**
 * @author yun
 */
public class InetServerFanoutExporter implements FanoutExporter {

    private final InetServer inetServer;

    public InetServerFanoutExporter(InetServer inetServer) {
        this.inetServer = Assert.checkNotNull(inetServer, "inetServer");
    }

    @Override
    public String name() {
        return "inet";
    }

    @Override
    public FanoutResult send(Destination destination, Buffer payload) {
        Assert.checkState(inetServer.isStart(), "Cannot send an unstarted inet server");

        int matched = 0;
        int success = 0;
        int failure = 0;

        for (NetChannel channel : inetServer.connections().stream().toList()) {
            if (!channel.isActive() || channel.isClosed()) {
                continue;
            }

            matched++;
            Buffer copiedPayload = payload.copy();
            try {
                channel.writeAndFlush(copiedPayload);
                success++;
            } catch (Exception e) {
                copiedPayload.release();
                failure++;
            }
        }

        return new FanoutResult(destination, matched, success, failure);
    }

    @Override
    public boolean isOpen() {
        return inetServer.isStart();
    }

    @Override
    public boolean isClose() {
        return inetServer.isShutdown();
    }

    @Override
    public void close() {
        inetServer.shutdown();
    }
}
