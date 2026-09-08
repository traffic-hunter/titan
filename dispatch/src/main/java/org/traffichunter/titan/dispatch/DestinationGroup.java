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
package org.traffichunter.titan.dispatch;

/**
 * Named subset of a dispatcher's queues.
 *
 * <p>Every queue belongs to exactly one group. A group is a {@link Dispatcher} over its
 * own queues. Lookups and wildcard searches only see the queues it owns. Creation and
 * removal go through the owning {@link DestinationGroupRegistry}, which keeps one
 * destination from being registered in two groups.</p>
 *
 * @author yun
 */
public interface DestinationGroup extends Dispatcher {

    /** Identifier assigned when the group is created. It never changes. */
    String id();

    /** Name used by operators and the CLI. Unique within a registry. */
    String name();
}
