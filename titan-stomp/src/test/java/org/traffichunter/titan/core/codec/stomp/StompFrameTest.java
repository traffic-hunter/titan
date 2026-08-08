package org.traffichunter.titan.core.codec.stomp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yungwang-o
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class StompFrameTest {

    @Test
    void generate_stomp_frame_test() {
        StompFrame stompFrame = getStompFrame();

        String stompMessage = stompFrame.toString();

        System.out.println(stompMessage);
    }

    @Test
    void parse_stomp_message_test() {
        StompFrame stompFrame = getStompFrame();

        String stompMessage = stompFrame.toString();

        StompFrame parseFrame = StompFrame.doParse(stompMessage, StompHeaders.create());

        Assertions.assertEquals(parseFrame.getCommand(), stompFrame.getCommand());

        StompHeaders headers = stompFrame.getHeaders();
        headers.keySet().forEach(key ->
                Assertions.assertEquals(stompFrame.getHeaders().get(key), parseFrame.getHeaders().get(key))
        );
        Assertions.assertArrayEquals(parseFrame.body(), stompFrame.body());
    }

    @Test
    void consume_buffer_and_keep_copied_body() {
        Buffer body = Buffer.heap().alloc("body");

        StompFrame frame = StompFrame.create(
                StompHeaders.create(),
                StompCommand.SEND,
                body
        );

        assertEquals(0, body.byteBuf().refCnt());
        assertArrayEquals("body".getBytes(StandardCharsets.UTF_8), frame.body());
    }

    private static StompFrame getStompFrame() {
        StompHeaders headers = new StompHeaders(new HashMap<>(), "titan", "v1.1.0");
        headers.put(Elements.HOST, "localhost:8080");
        headers.put(Elements.DESTINATION, "test_destination");

        String body = "hihi";
        return StompFrame.create(headers, StompCommand.SEND, body.getBytes(StandardCharsets.UTF_8));
    }
}
