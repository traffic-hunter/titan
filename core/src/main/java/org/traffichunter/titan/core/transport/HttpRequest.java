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
package org.traffichunter.titan.core.transport;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.codec.frame.Headers;

import java.net.http.HttpClient;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author yun
 */
public final class HttpRequest {

    private static final String CRLF = "\r\n";
    private static final String SP = " ";

    private String uri = "/";
    private String method = "GET";
    private HttpClient.Version version = HttpClient.Version.HTTP_1_1;
    private final HttpHeaders headers = new HttpHeaders();

    public static HttpRequest parse(String rawRequest) {
        String[] lines = rawRequest.split(CRLF);
        if (lines.length == 0) {
            throw new IllegalArgumentException("Missing HTTP request line");
        }

        String[] requestLine = lines[0].split(SP, 3);
        if (requestLine.length != 3) {
            throw new IllegalArgumentException("Invalid HTTP request line: " + lines[0]);
        }

        HttpRequest request = new HttpRequest()
                .method(requestLine[0])
                .uri(requestLine[1])
                .version(parseVersion(requestLine[2]));

        for (int i = 1; i < lines.length; i++) {
            int delimiter = lines[i].indexOf(':');
            if (delimiter < 0) {
                continue;
            }

            String name = lines[i].substring(0, delimiter).trim();
            String value = lines[i].substring(delimiter + 1).trim();
            request.header(name, value);
        }

        return request;
    }

    @CanIgnoreReturnValue
    public HttpRequest uri(String uri) {
        this.uri = uri;
        return this;
    }

    @CanIgnoreReturnValue
    public HttpRequest method(String method) {
        this.method = method;
        return this;
    }

    @CanIgnoreReturnValue
    public HttpRequest version(HttpClient.Version version) {
        this.version = version;
        return this;
    }

    public String uri() {
        return uri;
    }

    public String method() {
        return method;
    }

    public HttpClient.Version version() {
        return version;
    }

    @CanIgnoreReturnValue
    public HttpRequest header(String key, String value) {
        headers.put(key, value);
        return this;
    }

    @CanIgnoreReturnValue
    public @Nullable String header(String key) {
        return headers.get(key);
    }

    @CanIgnoreReturnValue
    public HttpRequest headerIfAbsent(String key, String value) {
        headers.putIfAbsent(key, value);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(method)
                .append(SP)
                .append(uri)
                .append(SP)
                .append(formatVersion(version))
                .append(CRLF);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(CRLF);
        }
        sb.append(CRLF);
        return sb.toString();
    }

    private static String formatVersion(HttpClient.Version version) {
        return switch (version) {
            case HTTP_1_1 -> "HTTP/1.1";
            case HTTP_2 -> "HTTP/2";
        };
    }

    private static HttpClient.Version parseVersion(String version) {
        return switch (version) {
            case "HTTP/1.1" -> HttpClient.Version.HTTP_1_1;
            case "HTTP/2" -> HttpClient.Version.HTTP_2;
            default -> throw new IllegalArgumentException("Unsupported HTTP version: " + version);
        };
    }

    private static final class HttpHeaders extends Headers<String, String, HttpHeaders> {

        private HttpHeaders() {
            super(new LinkedHashMap<>());
        }

        @Override
        public void put(String key, String value) {
            map.put(key, value);
        }

        @Override
        public void putIfAbsent(String key, String value) {
            map.putIfAbsent(key, value);
        }

        @Override
        public String getOrDefault(String key, String defaultValue) {
            String value = get(key);
            return value == null ? defaultValue : value;
        }

        @Override
        public @Nullable String get(String key) {
            String value = map.get(key);
            if (value != null) {
                return value;
            }
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        @Override
        public boolean containsKey(String key) {
            if (map.containsKey(key)) {
                return true;
            }
            for (String header : map.keySet()) {
                if (header.equalsIgnoreCase(key)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Set<String> keySet() {
            return map.keySet();
        }

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            return map.entrySet();
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            return map.entrySet().iterator();
        }

        @Override
        public HttpHeaders getHeader() {
            return this;
        }
    }
}
