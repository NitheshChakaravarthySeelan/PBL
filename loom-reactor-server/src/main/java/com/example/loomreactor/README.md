Reactor Core Concepts You Must Understand

Before diving into method syntax, let’s clarify the core Reactor abstractions:

Flux<T>
A reactive stream of many items over time.
Think of it as a sequence that emits asynchronously.
It supports operators like .map(), .filter(), .publishOn(), etc.
Works lazily: nothing happens until someone subscribes.

Mono<T>
A reactive stream of a single item or none.
Often used for request-response operations.

Sinks.Many<T>
Think of it as a bridge between imperative and reactive worlds.
You tryEmitNext() to push data into a Flux pipeline.
Downstream subscribers receive those events reactively.
Variants:
    unicast() → one subscriber.
    multicast() → many subscribers.
    replay() → replays old events for new subscribers.

Schedulers
Control where the reactive work executes (thread model).
Common ones:
    Schedulers.parallel() – CPU-bound work.
    Schedulers.boundedElastic() – blocking I/O tasks.
    Schedulers.single() – single-threaded tasks.

What is happening in the MessageRouter:
Actually a sink is a message queue for the client where the data is transfered in the form of binary to the socket.

High- Level Architecture
At its core, this application is a multi-client chat server. Its primary function is to accept connections from multiple clients, receive messages from them, and broadcast those messages to every other connected client.

It resolves:
    1 .The Connection Problem (I/O Bound): How do you handle thousands, or even thens of thousands, of simultaneous client connections? Most of the time, these connections are idle, waiting for a message. The traditional "one-thread-per-client" model is incredibly wasteful.
    Solution: Project Loom(Virtual Thread) The server assigns a virtual thread to each connection. Virtual threads are extremely lightweight, managed by the JVM, and do not map 1:1 to precious oeprating system thread. When a virtual thread executes a blocking I/O operation like reading from the socket, it "unmounts" from the OS thread, freeing it up to do other work. This allows the server to handle a massive number of concurrent connections with very few OS threads, combining the scalability of async code with simplicity of writing simple, blocking, sync style code.

    2. The Message Processing Problem (CPU -Bound and Fan Out): Once message arrive, how do you efficiently process them (eg,. format them, add a timestamp) and then distribute them to all the connected clients? This is a data flow and transformation problem.
    Solution: Project Reactor (Reactive Stream). The server uses a central, reactive pipeline to process messages. Incoming messages are pushed into a "sink", which acts as an entry point to a Flux (a stream of data) This stream declaratively defines a series of processing steps and, crucially, handles the "fan-out" logic of broadcasting the final, processed message to all subscribers (the client). It provides sophisticated features like backpressure and thread management out of the box.

Deep Dive

1. ServerMain.java
   * `MessageRouter router = new MessageRouter();`: A single, shared instance of the
     MessageRouter is created. This will be the central hub for all messages.
   * `Executors.newVirtualThreadPerTaskExecutor()`: This is the heart of the Loom integration.
      It creates an ExecutorService that does not use a traditional, heavy thread pool.
     Instead, every time you submit() a task, it spins up a new, lightweight virtual thread to
      run it. This is the key to the server's scalability.
   * `ServerSocket serverSocket = new ServerSocket(port)`: This is standard Java networking.
     It opens a socket on port 9090 and listens for incoming client connections.
   * `while (true)`: The main server loop, which runs forever.
   * (1) `Socket socket = serverSocket.accept();`: This is a blocking call. The loop will
     pause here, consuming no CPU, until a client connects.
   * (2) `ConnectionHandler handler = new ConnectionHandler(socket, router);`: Once a client
     connects, a Socket object is created. A new ConnectionHandler is instantiated to manage
     this specific client's entire session. The shared router is passed to it.
   * (3) `vThread.submit(handler);`: The ConnectionHandler (which is a Runnable) is submitted
     to our virtual thread executor. A new virtual thread is created instantly to run the
     handler.run() method. The main loop immediately continues to the serverSocket.accept()
     call, ready to handle the next client without waiting for the previous one to finish.

2. ConnectionHandler.java
This class is where Loom and Reactor meet. Each instance manages one client session, running on its own dedicated virtual thread
   * `run()`: This method is executed by a virtual thread. The code inside is written in a
     simple, blocking style, but it's highly scalable thanks to Loom.
   * (1) `router.registerClient(clientId)`: The first thing the handler does is tell the
     MessageRouter, "A new client has joined!" The router creates a personal, private message
     queue (a Sinks.Many<String>) for this client and returns it. This sink is the channel
     through which this client will receive messages.
   * (2) `outboundSink.asFlux()...subscribe(...)`: This is the Reactor part. The handler takes
     its personal sink, converts it to a Flux (a stream of messages), and subscribes to it.

   * (3) The `subscribe` Lambda: This is the callback that executes whenever a message is
     pushed into this client's personal sink. The code inside simply writes the message payload
      to the client's socket. publishOn(Schedulers.boundedElastic()) is a crucial detail: it
     ensures that this writing operation happens on a separate thread pool suitable for
     blocking I/O, preventing it from blocking the main reactive pipeline.
   * (4) `while ((line = reader.readLine()) != null)`: This is the main read loop.
     reader.readLine() is a blocking I/O call. The virtual thread will "park" here, consuming
     no OS thread, until a line of text arrives from the client. This is the magic of Loom in
     action.
   * (5) `router.publishInbound(...)`: When a line is received, the handler doesn't process it
     directly. Instead, it publishes the raw message to the central MessageRouter's inbound
     sink. It's saying, "Hey router, I got a message from my client, do your thing!"
   * (6) Cleanup: When the loop exits (client disconnects), the handler gracefully
     unsubscribes from its outbound sink, tells the router to remove it, and closes the
     socket.

3. MessageRouter.java
This is the central nervous system of the application. It's a singleton that manages the flow of all messages.
   * `inboundSink`: A multicast sink. "Multicast" means it can have multiple subscribers
     (though in this design, it only has one: the main pipeline). All ConnectionHandlers push
     messages into this single sink.
   * `clients` Map: A ConcurrentHashMap that stores the mapping from a clientId to its personal
      outbound Sinks.Many<String>. This is how the router knows where to send messages for each
      client.
   * (1) The Pipeline: The constructor defines the entire message processing logic as a
     reactive stream. Nothing happens until .subscribe() is called.
   * (2) `publishOn(Schedulers.boundedElastic())`: As soon as a message enters the pipeline,
     the processing is moved to a different thread pool. This is critical for performance, as
     it frees up the caller's thread (the virtual thread in ConnectionHandler) immediately.
   * (3) `.map(...)`: This is the transformation step. For each ClientMessage that comes
     through, it calls Protocol.formatBroadcast to create the final, formatted string that
     will be sent to clients.
   * (4) `.limitRate(256)`: A backpressure operator. It ensures that the pipeline doesn't
     process more than 256 messages per second, preventing the system from being overloaded by
     a flood of incoming messages.
   * (5) `pipeline.subscribe(...)`: This is what "turns on" the stream. The lambda provided
     here is the final consumer of the processed messages.
   * (6) Fan-Out Logic: This is the most important part. When a fully processed message arrives
      at the subscribe block, the code iterates over the clients map and pushes the formatted
     payload into every single client's personal sink. This is the broadcast mechanism. Each
     ConnectionHandler is subscribed to its own sink and will now receive this message and
     write it to its socket.

4. Protocol.java
This class is a simple, utility style class that defines the "wire format" of the messages. It ensures that data is serialized and deserialized in a consistent and safe way.
   * `formatBroadcast`: Defines the message structure: username|timestamp|message_text.
   * `escape` and `unescape`: These are critical for correctness. What if a user's message
     contains a | character? It would break the parsing logic. The escape method replaces
     special characters (like |, \, \n) with escape sequences (like \|, \\, \n). The unescape
     method does the reverse. This ensures that the message content can be anything without
     corrupting the protocol itself.
   * `parseLine` and `splitRespectingEscape`: These methods perform defensive parsing of
     incoming data, correctly splitting the message by the | delimiter while respecting any
     escaped \| sequences. This robust parsing prevents errors and potential injection
     attacks.
