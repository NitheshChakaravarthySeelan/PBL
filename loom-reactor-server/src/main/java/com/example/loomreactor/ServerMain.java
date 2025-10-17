package com.example.loomreactor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ServerMain {
	public static void main(String args[]) throws IOException {
		int port = 9090;
		MessageRouter router = new MessageRouter();

		// Create virtual thread per task 
		try (ExecutorService vThread = Executors.newVirtualThreadPerTaskExecutor()) {
			try (ServerSocket serverSocket = new ServerSocket(port)) {
				System.out.println("Server Started on the port " + port);

				while (true) {
					Socket socket = serverSocket.accept();

					// Each accepted socket handled by a virtual thread
					ConnectionHandler handler = new ConnectionHandler(socket,router);
					vThread.submit(handler);
				}
			}
		}
	}
}
