package org.traffichunter.titan.core.codec;

/**
 * Exception raised while encoding outbound channel data.
 */
public class ChannelEncoderException extends CodecException {
        
    /**
     * Creates an exception with the failure message.
     */
    public ChannelEncoderException(String message) {
        super(message);
    }
}
