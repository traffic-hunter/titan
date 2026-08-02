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
package org.traffichunter.titan.client;

/**
 * Reports lifecycle, configuration, or transport failures exposed by the client facade.
 *
 * <p>Transport-specific failures may be retained as the cause, allowing callers to depend on one
 * client-level exception without losing the original diagnostic information.</p>
 *
 * @author yun
 */
public class ClientException extends RuntimeException {

    /**
     * Creates a client exception with a diagnostic message.
     *
     * @param message description of the client failure
     */
    public ClientException(String message) {
        super(message);
    }

    /**
     * Creates a client exception retaining the transport or protocol failure that caused it.
     *
     * @param message description of the client failure
     * @param cause underlying failure
     */
    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
