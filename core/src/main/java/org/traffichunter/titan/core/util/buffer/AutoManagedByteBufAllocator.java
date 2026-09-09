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
package org.traffichunter.titan.core.util.buffer;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.WrappedByteBuf;
import io.netty.util.IllegalReferenceCountException;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.util.Assert;

/**
 * <strong>Deprecated.</strong> ByteBuf allocator that wraps allocated buffers with a
 * cleaner-backed release guard.
 *
 * <p>The wrapper does not replace normal reference-count ownership. Callers should still
 * release buffers explicitly. The cleaner is a last-resort safety net for buffers that become
 * unreachable with outstanding references.</p>
 *
 * @author yungwang-o
 */
@Deprecated(forRemoval = true)
public class AutoManagedByteBufAllocator extends AbstractByteBufAllocator {

    private final ByteBufAllocator delegate;

    public AutoManagedByteBufAllocator() {
        this(ByteBufAllocator.DEFAULT);
    }

    public AutoManagedByteBufAllocator(final ByteBufAllocator delegate) {
        this.delegate = delegate;
    }

    @Override
    protected ByteBuf newHeapBuffer(final int initialCapacity, final int maxCapacity) {
        return AutoReleaseByteBuf.byteBuf(delegate.heapBuffer(initialCapacity, maxCapacity));
    }

    @Override
    protected ByteBuf newDirectBuffer(final int initialCapacity, final int maxCapacity) {
        return AutoReleaseByteBuf.byteBuf(delegate.directBuffer(initialCapacity, maxCapacity));
    }

    @Override
    public boolean isDirectBufferPooled() {
        return delegate.isDirectBufferPooled();
    }

    /**
     * Wrapped buffer that marks the cleaner state when Netty reference count reaches zero.
     */
    static class AutoReleaseByteBuf extends WrappedByteBuf {

        private static final Logger log = LoggerFactory.getLogger(AutoReleaseByteBuf.class);

        private static final Cleaner CLEANER = Cleaner.create();

        private final Cleaner.Cleanable cleanable;

        private final AtomicBoolean isCleaned = new AtomicBoolean(false);

        private AutoReleaseByteBuf(final ByteBuf buf) {
            super(buf);
            this.cleanable = CLEANER.register(this, new Releaser(buf, isCleaned));
        }

        public static ByteBuf byteBuf(final ByteBuf buffer) {
            Assert.checkNotNull(buffer, "buffer is null!");

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

    /**
     * Cleaner action that releases any remaining references when a wrapped buffer is abandoned.
     */
    private record Releaser(ByteBuf buf, AtomicBoolean cleaned) implements Runnable {

        @Override
        public void run() {
            if (cleaned.compareAndSet(false, true)) {
                try {
                    if (buf.refCnt() > 0) {
                        // Release only the reference owned by this wrapper. Retained derived
                        // buffers own their references independently.
                        buf.release();
                    }
                } catch (Throwable t) {
                    // swallow errors to avoid exceptions from cleaner thread
                }
            }
        }
    }
}
