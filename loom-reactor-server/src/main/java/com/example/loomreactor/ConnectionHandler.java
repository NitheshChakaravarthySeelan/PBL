package com.example.loomreactor;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// It is where Project Loom (virtual threads) meets Project Reactor (reactive stream)
/**
 * Each Connection Handler is one client session
 * Accept a Socket from server
 * Register that client in the global MessageRouter
 * Read incoming lines from the socket
 * Push those messages into the MessageRouter's reactive pipeline
 * Subscribe to that client's outbound sink to receive messages to send back
 * Clean up on disconnect or errors
 */
public class ConnectionHandler implements Runnable {
	
	private final Socket socket;
	private final MessageRouter router;
	private final String clientId;
	private final String username;

	public ConnectionHandler(Socket socket, MessageRouter router) {
		this.socket = socket;
		this.router = router;
		this.clientId = UUID.randomUUID().toString();
		this.username = "user-" + clientId;
	}
	
	@Override
	public void run() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
			// Register outbound sink 
			// This creates a private message queue(sink) for this specific client
			Sinks.Many<String> outboundSink = router.registerClient(clientId);
			
			// Subscribe to outbound Sink
			Disposable outboundSub = outboundSink.asFlux()
							     .onBackpressureBuffer(256, dropped -> {
								     System.out.println("Drop the rem cause overflowing 256");
							     })
							     .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
							     .subscribe(payload -> {
								     try{
									     writer.write(payload);
									     writer.flush();
								     } catch (IOException e) {
									     e.printStackTrace();
									     closeQuiet();
								     }
							     }, err -> {
								     err.printStackTrace();
								     closeQuiet();
							     }, () -> {
								     closeQuiet();
							     });
			writer.write("WELCOME " + username + "\n");
			writer.flush();

			// Main read loop (blocing) WE WILL DO IT IN VIRTUAL THREAD
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}

				if ("quit".equalsIgnoreCase(line.trim())) {
					break;
				}

				// publish inbound router
				router.publishInbound(clientId, username, line);
			}

			outboundSub.dispose();
			router.unregisterClient(clientId);
			closeQuiet();
			
			} catch(IOException e) {
				e.printStackTrace();
				router.unregisterClient(clientId);
				closeQuiet();
			}
	}

	private void closeQuiet() {
		try {
			socket.close();
		} catch (IOException ignored) {}
	}
}
