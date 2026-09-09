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
package org.traffichunter.titan.springframework.stomp.messaging.converter;

import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeTypeUtils;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.springframework.stomp.messaging.TitanSpringMessageAdapter;

/**
 * Message converter that resolves the original {@link StompFrames}.
 * The frame is read from the internal Spring message header.
 * This supports listener method parameters of type {@code StompFrames}.
 *
 * @author yun
 */
public final class StompFrameMessageConverter extends AbstractMessageConverter {

    public StompFrameMessageConverter() {
        super(MimeTypeUtils.ALL);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return StompFrames.class.isAssignableFrom(clazz);
    }

    @Override
    protected @Nullable Object convertFromInternal(Message<?> message, Class<?> targetClass, @Nullable Object conversionHint) {
        if (!StompFrames.class.isAssignableFrom(targetClass)) {
            return null;
        }

        return message.getHeaders().get(TitanSpringMessageAdapter.HDR_STOMP_FRAME, StompFrames.class);
    }
}
