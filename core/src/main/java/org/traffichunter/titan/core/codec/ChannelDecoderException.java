package org.traffichunter.titan.core.codec;

/**
 * Exception raised while decoding inbound channel data.
 */
public class ChannelDecoderException extends CodecException {
        
    /**
     * Creates an exception with the failure message.
     */
    public ChannelDecoderException(String message) {
        super(message);
    }
}
