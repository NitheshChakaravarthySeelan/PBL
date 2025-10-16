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
These 
