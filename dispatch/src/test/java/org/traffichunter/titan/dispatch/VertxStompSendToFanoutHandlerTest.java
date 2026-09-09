package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.stomp.Command;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.ServerFrame;
import io.vertx.ext.stomp.StompServer;
import io.vertx.ext.stomp.StompServerConnection;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.message.Message;

/**
 * @author yun
 */
class VertxStompSendToFanoutHandlerTest {

    @Test
    @SuppressWarnings("unchecked")
    void group_header_is_rejected_with_error_frame() {
        DispatchGateway gateway = mock(DispatchGateway.class);
        StompServerConnection connection = mock(StompServerConnection.class);
        StompServer server = mock(StompServer.class);
        Vertx vertx = mock(Vertx.class);
        ServerFrame serverFrame = mock(ServerFrame.class);
        Frame frame = new Frame(
                Command.SEND,
                Map.of("destination", "/topic/price", "group", "market"),
                io.vertx.core.buffer.Buffer.buffer("hello")
        );
        when(serverFrame.frame()).thenReturn(frame);
        when(serverFrame.connection()).thenReturn(connection);
        when(connection.server()).thenReturn(server);
        when(connection.session()).thenReturn("session-1");
        when(server.vertx()).thenReturn(vertx);
        doAnswer(invocation -> {
            invocation.<Handler<Void>>getArgument(0).handle(null);
            return null;
        }).when(vertx).runOnContext(any());

        new VertxStompSendToFanoutHandler(gateway).handle(serverFrame);

        ArgumentCaptor<Frame> written = ArgumentCaptor.forClass(Frame.class);
        verify(connection).write(written.capture());
        assertThat(written.getValue().getCommand()).isEqualTo(Command.ERROR);
        assertThat(written.getValue().getHeader(Frame.MESSAGE)).isEqualTo("Unsupported header.");
        verify(connection).close();
        verify(gateway, never()).sparkDispatch(any(Message.class));
    }
}
