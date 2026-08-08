package org.traffichunter.titan.core.codec.websocket;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketContext;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * @author yun
 */
class WebSocketFrameDecoderTest {

    private static final int SHORT_EXTENDED_PAYLOAD_LENGTH = 126;
    private static final int LONG_EXTENDED_PAYLOAD_LENGTH = 127;

    @Test
    void decode_unmasked_text_frame_with_two_byte_payload() {
        InMemoryNetChannel channel = new InMemoryNetChannel();

        byte[] bytes = {(byte) 0x81, (byte) 0x02, 'O', 'K'};
        Buffer buffer = Buffer.heap().alloc(bytes);

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNotNull();
        assertThat(payload.toString()).isEqualTo("OK");
        assertThat(buffer.isReadable()).isFalse();

        payload.release();
        buffer.release();
    }

    @Test
    void decode_unmasked_text_frame_with_four_byte_payload() {
        InMemoryNetChannel channel = new InMemoryNetChannel();

        byte[] bytes = {(byte) 0x81, (byte) 0x04, 'O', 'K', 'O', 'K'};
        Buffer buffer = Buffer.heap().alloc(bytes);

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNotNull();
        assertThat(payload.toString()).isEqualTo("OKOK");
        assertThat(buffer.isReadable()).isFalse();

        payload.release();
        buffer.release();
    }

    @Test
    void decode_unmasked_binary_frame_with_four_byte_payload() {
        InMemoryNetChannel channel = new InMemoryNetChannel();

        // ASCII payloads = 'A', 'B', 'A', 'B'
        byte[] bytes = {(byte) 0x82, (byte) 0x04, (byte) 0x41, (byte) 0x42, (byte) 0x41, (byte) 0x42};
        Buffer buffer = Buffer.heap().alloc(bytes);

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNotNull();
        assertThat(payload.toString()).isEqualTo("ABAB");
        assertThat(buffer.isReadable()).isFalse();

        payload.release();
        buffer.release();
    }

    @Test
    void decode_masked_text_frame_with_two_byte_payload() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        int maskingKey = 0x01020304;
        byte[] body = mask(new byte[]{'O', 'K'}, maskingKey);
        byte[] bytes = {
                (byte) 0x81,
                (byte) 0x82,
                0x01,
                0x02,
                0x03,
                0x04,
                body[0],
                body[1]
        };
        Buffer buffer = Buffer.heap().alloc(bytes);

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNotNull();
        assertThat(payload.toString()).isEqualTo("OK");
        assertThat(buffer.isReadable()).isFalse();

        payload.release();
        buffer.release();
    }

    @Test
    void decode_masked_text_frame_with_zero_masking_key() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        byte[] bytes = {
                (byte) 0x81,
                (byte) 0x82,
                0x00,
                0x00,
                0x00,
                0x00,
                'O',
                'K'
        };
        Buffer buffer = Buffer.heap().alloc(bytes);

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNotNull();
        assertThat(payload.toString()).isEqualTo("OK");
        assertThat(buffer.isReadable()).isFalse();

        payload.release();
        buffer.release();
    }

    @Test
    void decode_unmasked_text_frame_with_126_marker_payload() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        byte[] body = repeated('A', 126);
        byte[] bytes = new byte[4 + body.length];
        bytes[0] = (byte) 0x81;
        bytes[1] = (byte) SHORT_EXTENDED_PAYLOAD_LENGTH;
        bytes[2] = 0x00;
        bytes[3] = 0x7E;
        System.arraycopy(body, 0, bytes, 4, body.length);
        Buffer buffer = Buffer.heap().alloc(bytes);

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNotNull();
        assertThat(payload.length()).isEqualTo(126);
        assertThat(payload.toString()).isEqualTo("A".repeat(126));
        assertThat(buffer.isReadable()).isFalse();

        payload.release();
        buffer.release();
    }

    @Test
    void decode_unmasked_text_frame_with_127_marker_payload() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        byte[] body = repeated('B', 65_536);
        byte[] bytes = new byte[10 + body.length];
        bytes[0] = (byte) 0x81;
        bytes[1] = (byte) LONG_EXTENDED_PAYLOAD_LENGTH;
        bytes[2] = 0x00;
        bytes[3] = 0x00;
        bytes[4] = 0x00;
        bytes[5] = 0x00;
        bytes[6] = 0x00;
        bytes[7] = 0x01;
        bytes[8] = 0x00;
        bytes[9] = 0x00;
        System.arraycopy(body, 0, bytes, 10, body.length);
        Buffer buffer = Buffer.heap().alloc(bytes);

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNotNull();
        assertThat(payload.length()).isEqualTo(65_536);
        assertThat(payload.toString()).isEqualTo("B".repeat(65_536));
        assertThat(buffer.isReadable()).isFalse();

        payload.release();
        buffer.release();
    }

    @Test
    void return_null_when_frame_header_is_incomplete() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        Buffer buffer = Buffer.heap().alloc(new byte[]{(byte) 0x81});

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNull();
        assertThat(channel.isClosed()).isFalse();
        assertThat(buffer.isReadable()).isTrue();

        buffer.release();
    }

    @Test
    void preserve_buffer_when_extended_length_is_incomplete() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        Buffer buffer = Buffer.heap().alloc(new byte[]{(byte) 0x81, 0x7E, 0x00});

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNull();
        assertThat(channel.isClosed()).isFalse();
        assertThat(buffer.byteBuf().readerIndex()).isZero();
        assertThat(buffer.isReadable()).isTrue();

        buffer.release();
    }

    @Test
    void preserve_buffer_when_payload_is_incomplete() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        Buffer buffer = Buffer.heap().alloc(new byte[]{(byte) 0x81, 0x04, 'O', 'K'});

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNull();
        assertThat(channel.isClosed()).isFalse();
        assertThat(buffer.byteBuf().readerIndex()).isZero();
        assertThat(buffer.isReadable()).isTrue();

        buffer.release();
    }

    @Test
    void close_channel_when_rsv_bit_is_set() {
        byte[] payload = {(byte) 0xC1, 0x00};

        assertInvalidFrameClosesChannel(payload);
    }

    @Test
    void close_channel_when_opcode_is_unknown() {
        byte[] payload = {(byte) 0x83, 0x00};

        assertInvalidFrameClosesChannel(payload);
    }

    @Test
    void close_channel_when_fragmented_frame_is_received() {
        byte[] payload = {0x01, 0x00};

        assertInvalidFrameClosesChannel(payload);
    }

    @Test
    void close_channel_when_control_frame_payload_is_too_large() {
        byte[] payload = {(byte) 0x88, 0x7E, 0x00, 0x7E};

        assertInvalidFrameClosesChannel(payload);
    }

    @Test
    void close_channel_when_126_length_marker_uses_inline_length() {
        byte[] payload = {(byte) 0x81, 0x7E, 0x00, 0x7D};

        assertInvalidFrameClosesChannel(payload);
    }

    @Test
    void close_channel_when_127_length_marker_uses_short_extended_length() {
        byte[] payload = {
                (byte) 0x81,
                0x7F,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x7E
        };

        assertInvalidFrameClosesChannel(payload);
    }

    @Test
    void consume_close_frame_without_forwarding_payload() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        Buffer buffer = Buffer.heap().alloc(new byte[]{(byte) 0x88, 0x00});

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNull();
        assertThat(channel.isClosed()).isTrue();
        assertThat(buffer.isReadable()).isFalse();

        buffer.release();
    }

    @Test
    void consume_ping_frame_without_forwarding_payload() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        Buffer buffer = Buffer.heap().alloc(new byte[]{(byte) 0x89, 0x02, 'O', 'K'});
        AtomicReference<WebSocketContext> received = new AtomicReference<>();

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder(
                WebSocketSide.SERVER,
                Protocol.STOMP,
                received::set
        );
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNull();
        assertThat(channel.isClosed()).isFalse();
        assertThat(buffer.isReadable()).isFalse();
        assertThat(received.get().side()).isEqualTo(WebSocketSide.SERVER);
        assertThat(received.get().frame().header().getOpCode()).isEqualTo(WebSocketFrameHeader.OpCode.PING);
        assertThat(received.get().frame().payload().toString()).isEqualTo("OK");

        received.get().frame().payload().release();
        buffer.release();
    }

    @Test
    void close_channel_when_long_payload_length_exceeds_supported_size() {
        byte[] payload = {
                (byte) 0x81,
                0x7F,
                0x00,
                0x00,
                0x00,
                0x00,
                (byte) 0x80,
                0x00,
                0x00,
                0x00
        };
        assertInvalidFrameClosesChannel(payload);
    }

    private static void assertInvalidFrameClosesChannel(byte[] bytes) {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        Buffer buffer = Buffer.heap().alloc(bytes);

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNull();
        assertThat(channel.isClosed()).isTrue();

        buffer.release();
    }

    private static byte[] repeated(char value, int count) {
        byte[] bytes = new byte[count];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static byte[] mask(byte[] payload, int maskingKey) {
        byte[] masked = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            masked[i] = (byte) (payload[i] ^ maskByte(maskingKey, i));
        }
        return masked;
    }

    private static byte maskByte(int maskingKey, int index) {
        int shift = 24 - ((index % 4) * 8);
        return (byte) ((maskingKey >> shift) & 0xFF);
    }
}
