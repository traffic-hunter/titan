package org.traffichunter.titan.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.channel.stomp.StompServerChannel;
import org.traffichunter.titan.core.channel.stomp.StompServerEvent;
import org.traffichunter.titan.core.channel.stomp.StompServerHandlerContext;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.DestinationGroups;

/**
 * @author yun
 */
class StompSendToFanoutHandlerTest {

    private DispatchGateway gateway;
    private StompServerHandlerContext context;
    private StompClientChannel connection;
    private StompSendToFanoutHandler handler;

    @BeforeEach
    void setUp() {
        gateway = mock(DispatchGateway.class);
        when(gateway.sparkDispatch(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));
        StompServerChannel server = mock(StompServerChannel.class);
        when(server.option()).thenReturn(StompServerOption.builder().build());
        context = new StompServerHandlerContext(server);
        connection = mock(StompClientChannel.class);
        when(connection.session()).thenReturn("producer");
        handler = new StompSendToFanoutHandler(gateway);
    }

    @Test
    void group_header_sets_message_group() {
        handler.handle(new StompServerEvent(send("market"), connection), context);

        ArgumentCaptor<Message> dispatched = ArgumentCaptor.forClass(Message.class);
        verify(gateway).sparkDispatch(dispatched.capture());
        assertThat(dispatched.getValue().getGroup()).isEqualTo("market");
        assertThat(dispatched.getValue().getDestination().path()).isEqualTo("/topic/price");
    }

    @Test
    void missing_group_header_defaults() {
        handler.handle(new StompServerEvent(send(null), connection), context);

        ArgumentCaptor<Message> dispatched = ArgumentCaptor.forClass(Message.class);
        verify(gateway).sparkDispatch(dispatched.capture());
        assertThat(dispatched.getValue().getGroup()).isEqualTo(DestinationGroups.DEFAULT);
    }

    @Test
    void invalid_group_sends_error_and_never_dispatches() {
        handler.handle(new StompServerEvent(send("bad/name"), connection), context);

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(connection).send(sent.capture());
        assertThat(sent.getValue().getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(sent.getValue().getHeader(Elements.MESSAGE)).isEqualTo("Wrong send.");
        verify(connection).close();
        verify(gateway, never()).sparkDispatch(any(Message.class));
    }

    private static StompFrame send(String group) {
        StompFrame frame = StompFrame.create(StompHeaders.create(), StompCommand.SEND, "hello".getBytes());
        frame.addHeader(Elements.DESTINATION, "/topic/price");
        if (group != null) {
            frame.addHeader(Elements.GROUP, group);
        }
        return frame;
    }
}
