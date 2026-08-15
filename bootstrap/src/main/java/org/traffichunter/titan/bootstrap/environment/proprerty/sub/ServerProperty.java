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
package org.traffichunter.titan.bootstrap.environment.proprerty.sub;

import java.util.Map;
import java.util.Objects;

/**
 * Mutable YAML DTO for one configured server.
 *
 * <p>This class mirrors configuration keys closely so SnakeYAML can bind user
 * input with minimal ceremony. Validation and defaulting are intentionally
 * deferred to {@code ServerSettings}, which is the immutable runtime model.</p>
 */
public class ServerProperty {

    private String name;
    private String transport;
    private String protocol;
    private String host;
    private int port;
    private int primaryThreads;
    private int secondaryThreads;
    private Map<String, String> options;
    private Map<String, String> transportOptions;
    private Map<String, String> protocolOptions;
    private TlsProperty tls;

    public ServerProperty() {
    }

    public ServerProperty(
            String name,
            String transport,
            String protocol,
            String host,
            int port,
            int primaryThreads,
            int secondaryThreads,
            Map<String, String> options,
            Map<String, String> transportOptions,
            Map<String, String> protocolOptions,
            TlsProperty tls
    ) {
        this.name = name;
        this.transport = transport;
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.primaryThreads = primaryThreads;
        this.secondaryThreads = secondaryThreads;
        this.options = options;
        this.transportOptions = transportOptions;
        this.protocolOptions = protocolOptions;
        this.tls = tls;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPrimaryThreads() {
        return primaryThreads;
    }

    public void setPrimaryThreads(int primaryThreads) {
        this.primaryThreads = primaryThreads;
    }

    public int getSecondaryThreads() {
        return secondaryThreads;
    }

    public void setSecondaryThreads(int secondaryThreads) {
        this.secondaryThreads = secondaryThreads;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

    public Map<String, String> getTransportOptions() {
        return transportOptions;
    }

    public void setTransportOptions(Map<String, String> transportOptions) {
        this.transportOptions = transportOptions;
    }

    public Map<String, String> getProtocolOptions() {
        return protocolOptions;
    }

    public void setProtocolOptions(Map<String, String> protocolOptions) {
        this.protocolOptions = protocolOptions;
    }

    public TlsProperty getTls() {
        return tls;
    }

    public void setTls(TlsProperty tls) {
        this.tls = tls;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ServerProperty other
                && port == other.port
                && primaryThreads == other.primaryThreads
                && secondaryThreads == other.secondaryThreads
                && Objects.equals(name, other.name)
                && Objects.equals(transport, other.transport)
                && Objects.equals(protocol, other.protocol)
                && Objects.equals(host, other.host)
                && Objects.equals(options, other.options)
                && Objects.equals(transportOptions, other.transportOptions)
                && Objects.equals(protocolOptions, other.protocolOptions)
                && Objects.equals(tls, other.tls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name,
                transport,
                protocol,
                host,
                port,
                primaryThreads,
                secondaryThreads,
                options,
                transportOptions,
                protocolOptions,
                tls
        );
    }

    @Override
    public String toString() {
        return "ServerProperty{" +
                "name='" + name + '\'' +
                ", transport='" + transport + '\'' +
                ", protocol='" + protocol + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", primaryThreads=" + primaryThreads +
                ", secondaryThreads=" + secondaryThreads +
                ", options=" + options +
                ", transportOptions=" + transportOptions +
                ", protocolOptions=" + protocolOptions +
                ", tls=" + tls +
                '}';
    }
}
