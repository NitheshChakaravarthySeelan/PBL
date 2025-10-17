package com.example.loomreactor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Its a in memory message bus like broker inside the Netty server
 * It receives inbound events like message from client
 * It applies transformations or throddling through Reactor operators
 * It fans those out to all connected clients via sinks
 * Clients -> inboundSink -> Flux pipeline -> broadcast loop -> each client's sink
 */ 
public class MessageRouter {
	// Central sink where ConnectionHandlers publish incoming raw lines
	private final Sinks.Many<ClientMessage> inboundSink;
	
	// Current connected client: clientId -> per-client sink
	private final Map<String, Sinks.Many<String>> clients = new ConcurrentHashMap<>();

	public MessageRouter() {
		this.inboundSink = Sinks.many().multicast().onBackpressureBuffer();

		// Build Processing Pipeline
		Flux<ClientMessage> pipeline = inboundSink.asFlux()
		.onBackpressureBuffer(1024, drop -> {
			System.out.println("Drow the rem cause overflowing 1024");
		})
		// run processing on parallel scheduler
		.publishOn(Schedulers.boundedElastic())
		// transform message
		.map(cm -> {
			// Bulding outgoing String
			String out = Protocol.formatBroadcast(cm.from(), cm.message());
			return new ClientMessage(cm.from(), cm.message(), out);
		})
		.limitRate(256);

		// Subscribe pipeline: for each message broadcast to all this activates the stream
		/**
		 * Each ClientMessage coming out of the pipeline triggers this callback
		 * You get the payload which is already formatted
		 * Then iterate over all the client from the Map
		 * for each, you push the message into that client's personal outbound sink 
		 * The client's ConnectionHandler reads from that sink and writes to its socket
		 */
		pipeline.subscribe(cm -> {
			String payload = cm.outgoing();
			clients.forEach((id, sink) -> {
				sink.tryEmitNext(payload);
			});
		}, err -> {
			err.printStackTrace();
		});
	}
	
	// Register a client and return its outbound sink for that client
	public Sinks.Many<String> registerClient(String clientId) {
		Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
		clients.put(clientId, sink);
		return sink;
	}

	public void unregisterClient(String clientId) {
		Sinks.Many<String> s = clients.remove(clientId);
		if (s != null) {
			s.tryEmitComplete();
		}
	}
	
	// Called by Connection Handler when line is read.
	public void publishInbound(String clientId, String from, String message) {
		inboundSink.tryEmitNext(new ClientMessage(from, message, null));
	}

	public static record ClientMessage(String from, String message, String outgoing) {}
}


