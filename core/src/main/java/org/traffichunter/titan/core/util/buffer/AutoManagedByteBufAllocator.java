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
package org.traffichunter.titan.core.util.buffer;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.WrappedByteBuf;
import io.netty.util.IllegalReferenceCountException;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.Assert;

/**
 * @author yungwang-o
 */
public class AutoManagedByteBufAllocator extends AbstractByteBufAllocator {

    private final ByteBufAllocator delegate;

    public AutoManagedByteBufAllocator() {
        this(ByteBufAllocator.DEFAULT);
    }

    public AutoManagedByteBufAllocator(final ByteBufAllocator delegate) {
        Assert.checkNull(delegate, "allocate delegator is null!!");
        this.delegate = delegate;
    }

    @Override
    protected ByteBuf newHeapBuffer(final int initialCapacity, final int maxCapacity) {
        ByteBuf byteBuf = UnpooledByteBufAllocator.DEFAULT.heapBuffer(initialCapacity, maxCapacity);
        return AutoReleaseByteBuf.byteBuf(byteBuf);
    }

    @Override
    protected ByteBuf newDirectBuffer(final int initialCapacity, final int maxCapacity) {
        return AutoReleaseByteBuf.byteBuf(delegate.directBuffer(initialCapacity, maxCapacity));
    }

    @Override
    public boolean isDirectBufferPooled() {
        return delegate.isDirectBufferPooled();
    }

    @Slf4j
    static class AutoReleaseByteBuf extends WrappedByteBuf {

        private static final Cleaner CLEANER = Cleaner.create();

        private final Cleaner.Cleanable cleanable;

        private final AtomicBoolean isCleaned = new AtomicBoolean(false);

        private AutoReleaseByteBuf(final ByteBuf buf) {
            super(buf);
            this.cleanable = CLEANER.register(this, new Releaser(buf, isCleaned));
        }

        public static ByteBuf byteBuf(final ByteBuf buffer) {
            Assert.checkNull(buffer, "buffer is null!");

            if(buffer instanceof AutoReleaseByteBuf) {
                return buffer;
            }

            return new AutoReleaseByteBuf(buffer);
        }

        @Override
        public boolean release() {
            boolean result = super.release();
            maybeMarkCleaned();
            return result;
        }

        @Override
        public boolean release(int decrement) {
            boolean result = super.release(decrement);
            maybeMarkCleaned();
            return result;
        }

        @Override
        public ByteBuf retain() {
            super.retain();
            return this;
        }

        @Override
        public ByteBuf retain(int increment) {
            super.retain(increment);
            return this;
        }

        private void maybeMarkCleaned() {

            try {
                ByteBuf unwrap = unwrap();
                if (unwrap != null && unwrap.refCnt() == 0) {
                    if (isCleaned.compareAndSet(false, true)) {
                        cleanable.clean();
                    }
                }
            } catch (IllegalReferenceCountException e) {
                log.error("ref count mismatch! = {}", e.getMessage());
            }
        }
    }

    // Releaser action: releases remaining refCnt if > 0
    private record Releaser(ByteBuf buf, AtomicBoolean cleaned) implements Runnable {

        @Override
        public void run() {
            if (cleaned.compareAndSet(false, true)) {
                try {
                    int rc = buf.refCnt();
                    if (rc > 0) {
                        // release all outstanding references
                        buf.release(rc);
                    }
                } catch (Throwable t) {
                    // swallow errors to avoid exceptions from cleaner thread
                }
            }
        }
    }
}
