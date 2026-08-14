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
package org.traffichunter.titan.core.resilience.flowcontrol;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author yun
 */
public class CompositeDamper implements Damper {

    private final CopyOnWriteArrayList<Damper> dampers = new CopyOnWriteArrayList<>();

    public CompositeDamper add(Damper damper) {
        dampers.addIfAbsent(damper);
        return this;
    }

    public CompositeDamper addAll(Damper... dampers) {
        this.dampers.addAllAbsent(List.of(dampers));
        return this;
    }

    public CompositeDamper remove(Damper damper) {
        dampers.remove(damper);
        return this;
    }

    public CompositeDamper clear() {
        dampers.clear();
        return this;
    }

    public List<Damper> dampers() {
        return List.copyOf(dampers);
    }

    @Override
    public DamperStatus regulate() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void open() {
        dampers.forEach(Damper::open);
    }

    @Override
    public void close() {
        dampers.forEach(Damper::close);
    }

    @Override
    public DamperStatus getStatus() {
        throw new UnsupportedOperationException("Not supported");
    }
}
