package org.traffichunter.titan.core.codec.websocket;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.NetChannel.Internal;
import org.traffichunter.titan.core.channel.websocket.WebSocketContext;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder(WebSocketSide.CLIENT, Protocol.STOMP);
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

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder(WebSocketSide.CLIENT, Protocol.STOMP);
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

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder(WebSocketSide.CLIENT, Protocol.STOMP);
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

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder(WebSocketSide.CLIENT, Protocol.STOMP);
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

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder(WebSocketSide.CLIENT, Protocol.STOMP);
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
    void forward_masked_payload_as_it_arrives() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        int maskingKey = 0x01020304;
        byte[] body = mask(new byte[]{'O', 'K', 'A', 'Y', '!'}, maskingKey);
        Buffer first = Buffer.heap().alloc(new byte[]{
                (byte) 0x81, (byte) 0x85, 0x01, 0x02, 0x03, 0x04, body[0], body[1]
        });
        Buffer second = Buffer.heap().alloc(new byte[]{body[2], body[3], body[4]});

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer firstPayload = decoder.decode(channel, first);
        Buffer secondPayload = decoder.decode(channel, second);

        assertThat(firstPayload).isNotNull();
        assertThat(firstPayload.toString()).isEqualTo("OK");
        assertThat(secondPayload).isNotNull();
        assertThat(secondPayload.toString()).isEqualTo("AY!");
        assertThat(first.isReadable()).isFalse();
        assertThat(second.isReadable()).isFalse();
        assertThat(channel.isClosed()).isFalse();

        firstPayload.release();
        secondPayload.release();
        first.release();
        second.release();
    }

    @Test
    void consume_large_frame_header_without_waiting_for_payload() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        Buffer buffer = Buffer.heap().alloc(new byte[]{
                (byte) 0x81, (byte) 0xFF,
                0x00, 0x00, 0x00, 0x00, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                0x00, 0x00, 0x00, 0x00
        });

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        Buffer payload = decoder.decode(channel, buffer);

        assertThat(payload).isNull();
        assertThat(buffer.isReadable()).isFalse();
        assertThat(channel.isClosed()).isFalse();

        buffer.release();
    }

    @Test
    void close_with_protocol_error_when_rsv_bit_is_set() {
        byte[] payload = {(byte) 0xC1, (byte) 0x80, 0x00, 0x00, 0x00, 0x00};

        assertInvalidFrameClosesChannel(WebSocketSide.SERVER, payload, 1002);
    }

    @Test
    void close_with_protocol_error_when_opcode_is_unknown() {
        byte[] payload = {(byte) 0x83, (byte) 0x80, 0x00, 0x00, 0x00, 0x00};

        assertInvalidFrameClosesChannel(WebSocketSide.SERVER, payload, 1002);
    }

    @Test
    void close_with_protocol_error_when_fragmented_frame_is_received() {
        byte[] payload = {0x01, (byte) 0x80, 0x00, 0x00, 0x00, 0x00};

        assertInvalidFrameClosesChannel(WebSocketSide.SERVER, payload, 1002);
    }

    @Test
    void close_with_protocol_error_when_server_receives_unmasked_frame() {
        byte[] payload = {(byte) 0x81, 0x02, 'O', 'K'};

        assertInvalidFrameClosesChannel(WebSocketSide.SERVER, payload, 1002);
    }

    @Test
    void close_with_protocol_error_when_client_receives_masked_frame() {
        byte[] payload = {(byte) 0x81, (byte) 0x82, 0x00, 0x00, 0x00, 0x00, 'O', 'K'};

        assertInvalidFrameClosesChannel(WebSocketSide.CLIENT, payload, 1002);
    }

    @Test
    void close_with_protocol_error_when_control_frame_payload_is_too_large() {
        byte[] payload = {(byte) 0x88, (byte) 0xFE, 0x00, 0x7E};

        assertInvalidFrameClosesChannel(WebSocketSide.SERVER, payload, 1002);
    }

    @Test
    void close_with_protocol_error_when_126_length_marker_uses_inline_length() {
        byte[] payload = {(byte) 0x81, (byte) 0xFE, 0x00, 0x7D};

        assertInvalidFrameClosesChannel(WebSocketSide.SERVER, payload, 1002);
    }

    @Test
    void close_with_protocol_error_when_127_length_marker_uses_short_extended_length() {
        byte[] payload = {
                (byte) 0x81,
                (byte) 0xFF,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x7E
        };

        assertInvalidFrameClosesChannel(WebSocketSide.SERVER, payload, 1002);
    }

    @Test
    void close_with_message_too_big_when_payload_length_exceeds_supported_size() {
        byte[] payload = {
                (byte) 0x81,
                (byte) 0xFF,
                0x00,
                0x00,
                0x00,
                0x00,
                (byte) 0x80,
                0x00,
                0x00,
                0x00
        };

        assertInvalidFrameClosesChannel(WebSocketSide.SERVER, payload, 1009);
    }

    @Test
    void ignore_input_after_protocol_error() {
        NetChannel channel = writableChannel();
        Buffer invalid = Buffer.heap().alloc(new byte[]{(byte) 0x81, 0x02, 'O', 'K'});
        Buffer valid = Buffer.heap().alloc(new byte[]{(byte) 0x81, (byte) 0x82, 0x00, 0x00, 0x00, 0x00, 'O', 'K'});

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder();
        decoder.decode(channel, invalid);
        Buffer payload = decoder.decode(channel, valid);

        assertThat(payload).isNull();
        assertThat(valid.isReadable()).isFalse();
        verify(channel.internal(), times(1)).write(any(Buffer.class), any(ChannelPromise.class));

        invalid.release();
        valid.release();
    }

    @Test
    void consume_close_frame_without_forwarding_payload() {
        InMemoryNetChannel channel = new InMemoryNetChannel();
        Buffer buffer = Buffer.heap().alloc(new byte[]{(byte) 0x88, (byte) 0x80, 0x00, 0x00, 0x00, 0x00});

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
        Buffer buffer = Buffer.heap().alloc(new byte[]{(byte) 0x89, (byte) 0x82, 0x00, 0x00, 0x00, 0x00, 'O', 'K'});
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

    private static void assertInvalidFrameClosesChannel(WebSocketSide side, byte[] bytes, int closeStatus) {
        NetChannel channel = writableChannel();
        Buffer buffer = Buffer.heap().alloc(bytes);

        WebSocketFrameDecoder decoder = new WebSocketFrameDecoder(side, Protocol.STOMP);
        Buffer payload = decoder.decode(channel, buffer);

        ArgumentCaptor<Buffer> written = ArgumentCaptor.forClass(Buffer.class);
        verify(channel.internal()).write(written.capture(), any(ChannelPromise.class));
        verify(channel).close();
        assertThat(payload).isNull();
        assertThat(buffer.isReadable()).isFalse();
        assertThat(closeStatus(written.getValue())).isEqualTo(closeStatus);

        written.getValue().release();
        buffer.release();
    }

    /**
     * Returns a channel whose writes complete immediately on the calling thread.
     */
    private static NetChannel writableChannel() {
        NetChannel channel = mock(NetChannel.class);
        Internal internal = mock(Internal.class);
        IOEventLoop eventLoop = mock(IOEventLoop.class);
        when(channel.internal()).thenReturn(internal);
        when(channel.eventLoop()).thenReturn(eventLoop);
        when(eventLoop.inEventLoop()).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<ChannelPromise>getArgument(1).success();
            return null;
        }).when(internal).write(any(Buffer.class), any(ChannelPromise.class));
        return channel;
    }

    private static int closeStatus(Buffer frame) {
        byte[] bytes = frame.getBytes();
        assertThat(bytes[0]).isEqualTo((byte) 0x88);
        byte[] status;
        if ((bytes[1] & 0x80) != 0) {
            status = mask(Arrays.copyOfRange(bytes, 6, 8), ByteBuffer.wrap(bytes, 2, 4).getInt());
        } else {
            status = Arrays.copyOfRange(bytes, 2, 4);
        }
        return ((status[0] & 0xFF) << 8) | (status[1] & 0xFF);
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
