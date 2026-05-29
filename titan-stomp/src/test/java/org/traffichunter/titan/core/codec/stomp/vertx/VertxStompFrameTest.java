package org.traffichunter.titan.core.codec.stomp.vertx;

import io.vertx.ext.stomp.Command;
import io.vertx.ext.stomp.Frame;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VertxStompFrameTest {

    @Test
    void wraps_vertx_frame_as_stomp_frames() {
        Frame frame = new Frame(
                Command.MESSAGE,
                Map.of(
                        Frame.DESTINATION, "/topic/test",
                        Frame.MESSAGE_ID, "msg-1",
                        "custom-header", "ignored"
                ),
                io.vertx.core.buffer.Buffer.buffer("hello")
        );

        VertxStompFrame wrapped = VertxStompFrame.wrap(frame);

        assertThat(wrapped.command()).isEqualTo(StompCommand.MESSAGE);
        assertThat(wrapped.getHeader(StompHeaders.Elements.DESTINATION)).isEqualTo("/topic/test");
        assertThat(wrapped.headers())
                .containsEntry(StompHeaders.Elements.DESTINATION, "/topic/test")
                .containsEntry(StompHeaders.Elements.MESSAGE_ID, "msg-1")
                .doesNotContainKey(StompHeaders.Elements.CONTENT_TYPE);
        assertThat(new String(wrapped.body(), StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(wrapped.unwrap()).isSameAs(frame);
    }

    @Test
    void returns_empty_body_when_vertx_frame_body_is_null() {
        VertxStompFrame wrapped = VertxStompFrame.wrap(new Frame().setCommand(Command.RECEIPT));

        assertThat(wrapped.command()).isEqualTo(StompCommand.RECEIPT);
        assertThat(wrapped.body()).isEmpty();
    }

    @Test
    void rejects_unknown_vertx_command() {
        VertxStompFrame wrapped = VertxStompFrame.wrap(new Frame().setCommand(Command.UNKNOWN));

        assertThatThrownBy(wrapped::command)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Vert.x STOMP command");
    }
}
