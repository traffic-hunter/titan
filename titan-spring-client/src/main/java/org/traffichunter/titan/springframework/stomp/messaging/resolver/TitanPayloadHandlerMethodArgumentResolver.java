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
package org.traffichunter.titan.springframework.stomp.messaging.resolver;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.traffichunter.titan.core.codec.stomp.StompFrames;

/**
 * Resolves listener method arguments from the STOMP message payload.
 * Applies Spring message conversion for non-message and non-frame parameters.
 * Used after the STOMP-specific resolver in the listener resolver chain.
 *
 * @author yun
 */
public class TitanPayloadHandlerMethodArgumentResolver implements HandlerMethodArgumentResolver {

    private final SmartMessageConverter messageConverter;

    public TitanPayloadHandlerMethodArgumentResolver(SmartMessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();

        return !Message.class.isAssignableFrom(type) && !StompFrames.class.isAssignableFrom(type);
    }

    @Override
    public @Nullable Object resolveArgument(MethodParameter parameter, Message<?> message) {
        Class<?> targetType = parameter.getParameterType();

        Object payload = message.getPayload();
        if(targetType.isInstance(payload)) {
            return payload;
        }

        Object convertedMessage = messageConverter.fromMessage(message, targetType);
        if(convertedMessage == null) {
            throw new MessageConversionException("Cannot convert payload to " + targetType.getName());
        }

        return convertedMessage;
    }
}
