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
package org.traffichunter.titan.springframework.stomp.messaging.converter;

import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeTypeUtils;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.springframework.stomp.messaging.TitanSpringMessageAdapter;

/**
 * @author yun
 */
public final class StompFrameMessageConverter extends AbstractMessageConverter {

    public StompFrameMessageConverter() {
        super(MimeTypeUtils.ALL);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return StompFrame.class.isAssignableFrom(clazz);
    }

    @Override
    protected @Nullable Object convertFromInternal(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
        if (!StompFrame.class.isAssignableFrom(targetClass)) {
            return null;
        }

        return message.getHeaders().get(TitanSpringMessageAdapter.HDR_STOMP_FRAME, StompFrame.class);
    }
}
