1. Challenge
When you move from a single-node database to a distributed one, your biggest problem is agreement:
How do multiple nodes agree on what the “true” state of the system is?
This is what consensus algorithms like Raft (and Paxos before it) solve.

The challenge:
Machines crash, restart, lose messages, or lag behind.
Still, your cluster must act as one coherent system — not three disagreeing servers.
Raft guarantees consistency, fault tolerance, and safety even when some nodes fail or messages are lost.

2. Raft 
It is a structured for understandability and practical implementation. It breaks consensus into three major responsibilities.
    Leader Election: Which elects one node as leader
    Log Replication: Leader replicates client commands(log entries) to others.
    Safety and Commitment: Ensure every node's log eventually converges on the same committed sequence.

Leader - Handlers client req, manages log replication, sends periodic heartbeats.
Follower - Passive, responds to the leader/ candidate RPCs, becomes a candidate if not hearbeat(election timeout)
Candidate - Runs for election to become leader. If wins by majority vote or becomes follower if another leader is discovered.

Raft Terms and Leaders Diagram
Time → 

Term 1: Leader = Node A
----------------------------
Node A: [ 1:set x=5 ]  --> AppendEntries to followers
Node B: [ 1:set x=5 ]
Node C: [ 1:set x=5 ]

Term 2: Leader = Node B (Node A failed)
----------------------------------------
Node B: [ 1:set x=5, 2:set y=10 ]  --> AppendEntries
Node A: [ 1:set x=5 ]              (missed Term 2, will catch up)
Node C: [ 1:set x=5 ]

Node A recovers:
Node A: receives AppendEntries term=2 → appends missing log entries

* Term increments only when a new election happens.
* Each log entry carries the term of the leader that created it.
* Followers reject entries from a term < their currentTerm
* Heartbeat messages carry term info which allows followers to know if leader is current.
* Election safety:
    Only one leader per term
    Stale leaders cannot overwrite logs.
