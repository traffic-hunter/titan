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
 * Handles one step in message dispatch.
 *
 * <p>The handler receives a mutable {@link DispatchContext} and the remaining chain.
 * Call {@link DispatchChain#next(DispatchContext)} to continue dispatch, or return
 * the supplied chain without calling it to stop.</p>
 *
 * <p>Handlers run sequentially on the dispatch executor selected by the gateway.</p>
 *
 * @author yun
 */
public interface DispatchChainHandler {

    /** Sentinel behavior that completes without modifying the dispatch context. */
    DispatchChainHandler NOOP = (context, chain) -> chain.next(context);

    /**
     * Processes one dispatch stage.
     *
     * @param context state shared by the dispatch lifecycle
     * @param chain remaining dispatch handlers
     * @return continuation reached after this handler runs
     */
    DispatchChain handle(DispatchContext context, DispatchChain chain);
}
