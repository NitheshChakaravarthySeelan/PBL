package com.example.loomreactor;

import org.junit.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.junit.Assert.*;

public class MessageRouterTest {

    @Test
    public void testRegisterClient() {
        MessageRouter router = new MessageRouter();
        String clientId = "client1";
        Sinks.Many<String> sink = router.registerClient(clientId);
        assertNotNull(sink);
    }

    @Test
    public void testUnregisterClient() {
        MessageRouter router = new MessageRouter();
        String clientId = "client1";
        router.registerClient(clientId);
        router.unregisterClient(clientId);
    }

    @Test
    public void testPublishInbound() {
        MessageRouter router = new MessageRouter();
        String clientId = "client1";
        String from = "user1";
        String message = "hello world";
        Sinks.Many<String> sink = router.registerClient(clientId);

        StepVerifier.create(sink.asFlux())
                .expectNextCount(0)
                .then(() -> router.publishInbound(clientId, from, message))
                .expectNextCount(1)
                .thenCancel()
                .verify();
    }
}
