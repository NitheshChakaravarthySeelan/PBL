package com.example.loomreactor;

import org.junit.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Sinks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;

import static org.mockito.Mockito.when;

public class ConnectionHandlerTest {

    @Test
    public void testRun() throws IOException {
        Socket socket = Mockito.mock(Socket.class);
        MessageRouter router = Mockito.mock(MessageRouter.class);

        String input = "hello\nquit\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        when(socket.getInputStream()).thenReturn(in);
        when(socket.getOutputStream()).thenReturn(out);

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(router.registerClient(Mockito.anyString())).thenReturn(sink);

        ConnectionHandler handler = new ConnectionHandler(socket, router);
        handler.run();

        Mockito.verify(router).publishInbound(Mockito.anyString(), Mockito.anyString(), Mockito.eq("hello"));
        Mockito.verify(router).unregisterClient(Mockito.anyString());
    }
}
