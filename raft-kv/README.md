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

The propose Method:
It is the journey a client's command takes to get committed to the distributed log.
1. Client call: The client calls raftApi.propose("some command")
2. Initial Attempt: The RaftApi calls the propose method on the RaftNode it currently holds.
3. Follower Rejection: The Follower's propose method is called. Since it's not the leader, it immediately creates a CompletableFuture that fails with a NotleaderException. This exception contains the ID of the leader it knows about.
4. API Redirection: The RaftAPI catches the NotLeaderException. It gets the leader's ID, gets the actual RaftNode object for the leader from the InMemoryRaftProtocol, and then calls propose again, this time on the leader node.
5. Leader Acceptance: The leader's propose method is called.
    * Creates a new LogEntry containing the command and the current term
    * Adds this entry to its own log.
    * Creates a CompletableFuture<String> and stores it in a map, with the log index as the key.
    * Returns this future to the RaftApi, which in turn returns it to the client.
6. Replication: The leader then sends AppendEntries RPCs to the followers to replicate the new log entry. This happens in the background and is driven by the tick method.
7. Commitment: Once a majority of the followers have acknowledged that they have the new entry, the leader updates its commitIndex.
8. Completing the Future: When the commitIndex is updated, the leader checks its map of pending client request. It sees that the new entry is now committed, so it completes the CompletableFuture that it returned to the client earlier.
9. Client Notification: The client, which has been waiting on the future, unblocks and receives the result.


  Part 1: The High-Level View - What Problem Are We Solving?

  At its core, this project aims to create a fault-tolerant service. Imagine you have a critical piece of
  data (like a user's account balance). If you store it on a single server and that server crashes, the
  data is lost or unavailable.

  The solution is to store the same data on multiple servers (a cluster). However, this introduces a new
  problem: how do you ensure all servers have the exact same data in the exact same order, especially
  when servers can crash or network issues can occur?

  This is the problem of consensus, and Raft is a consensus algorithm designed to solve it.

  The Raft Analogy: A Decision-Making Committee

  Think of your servers as members of a committee that needs to agree on a sequence of decisions.

   1. The Leader: To avoid chaos, the committee elects one member as the Leader. The Leader is the single
      point of contact for all new decisions that need to be made.
   2. The Followers: All other members are Followers. They listen to the Leader and do as they say.
   3. Making a Decision (Log Entry): When someone wants a decision made (e.g., "Set variable X to 5"), they
      tell the Leader. The Leader writes this proposed decision down in its logbook.
   4. Replication: The Leader sends the proposed decision to all the Followers. The Followers write it in
      their own logbooks and tell the Leader, "I've written it down."
   5. Commitment: Once the Leader has heard back from a majority of the committee members (including itself),
      the decision is considered committed. It's now permanent. The Leader then notifies the Followers that
      the decision is officially committed.
   6. Leader Failure: If the Leader crashes or stops communicating, the Followers will notice. After a short
      period of silence, one of them will call for a new election, and the process repeats. This is a
      Candidate.

  This project implements that exact logic in Java.

  ---

  Part 2: The Core Components - Our Project's Building Blocks

  Here are the key files in the project and the role each one plays:

   * `RaftApp.java`: The main application entry point. It's responsible for setting up the cluster of nodes
     and starting them.
   * `RaftApi.java`: The clean, user-facing API for clients. It hides the complexity of finding the leader
     and submitting commands.
   * `RaftNode.java`: An abstract base class containing all the common logic and state for a Raft node
     (Term, Log, etc.).
   * `Follower.java`, `Candidate.java`, `Leader.java`: Concrete implementations of RaftNode that define the
     specific behaviors for each of the three roles.
   * `RaftProtocol.java` (Interface): A contract that defines what communication is needed (e.g.,
     sendAppendEntries). It's the "what," not the "how."
   * `InMemoryRaftProtocol.java` (Implementation): A specific, in-memory implementation of the RaftProtocol
     interface used for testing. It simulates the network.
   * `RaftStorage.java`: Handles the persistence of a node's state (current term, log entries). The current
     version is in-memory.
   * Data Transfer Objects (DTOs):
       * LogEntry.java: Represents a single command in the log.
       * AppendEntriesRequest.java / AppendEntriesResponse.java: The "messages" for log replication.
       * RequestVoteRequest.java / RequestVoteResponse.java: The "messages" for leader election.
   * `NotLeaderException.java`: A custom exception used for the client redirection workflow.

  ---

  Part 3: The Low-Level Deep Dive - Methods and Mappings

  Here is a detailed breakdown of each component, its methods, and their interactions.

  RaftNode.java (The Foundation)

  This is the abstract base class. It holds the state and defines the actions common to all roles.

   * State Variables:
       * currentTerm, votedFor, log: The persistent state of a Raft node.
       * commitIndex, lastApplied: Volatile state indicating what's committed and what's been applied to
         the state machine.
       * nodeId, peers: The node's own ID and the list of all nodes in the cluster.
       * protocol: The communication layer (an object that implements RaftProtocol).
       * scheduler: The engine that drives time-based events like timeouts.

   * `public synchronized void start()`
       * Purpose: To start the node's internal "heartbeat" timer.
       * Input: None.
       * Output: None (side effect: schedules the tick() method).
       * How it's used: Called by RaftApp to bring a node to life. It schedules this::tick to run every
         100ms.

   * `protected abstract void tick()`
       * Purpose: This is the core driver for time-based logic. Each role implements this differently.
       * Input: None.
       * Output: None (side effects depend on the role).
       * How it's used: Called by the scheduler every 100ms. For a Follower, it checks for an election
         timeout. For a Leader, it sends heartbeats.

   * `public abstract AppendEntriesResponse handleAppendEntries(...)` and `public abstract 
     RequestVoteResponse handleRequestVote(...)`
       * Purpose: These define how a node must respond to RPCs from other nodes.
       * Input: A Request object (AppendEntriesRequest or RequestVoteRequest) containing data from the
         sender.
       * Output: A Response object (AppendEntriesResponse or RequestVoteResponse) to be sent back.

   * `public CompletableFuture<String> propose(String command)`
       * Purpose: The entry point for a client to submit a new command to the Raft cluster.
       * Input: command (String) - The command the client wants to execute (e.g., "SET x = 5").
       * Output: CompletableFuture<String> - A "promise" that will eventually contain the result of the
         command after it has been successfully committed.
       * How it's used: This is the method called by the RaftApi. Each role has its own implementation.

  Follower.java (The Passive State)

   * `protected void tick()`
       * Purpose: To check if it's time to start an election.
       * Logic: It checks if the time since lastHeartbeatTime is greater than a random election timeout. If
         it is, it calls becomeCandidate().
       * Input/Output: None.

   * `public synchronized AppendEntriesResponse handleAppendEntries(AppendEntriesRequest req)`
       * Purpose: To handle a heartbeat or a replication request from the leader.
       * Input (Request): req contains the leader's term, the leader's ID, and new log entries (if any).
       * Logic:
           1. If the leader's term is valid, it resets its election timer by setting this.lastHeartbeatTime 
              = System.currentTimeMillis();. This is the most critical step.
           2. It records the leader's ID from req.getLeaderId().
           3. It appends any new log entries to its own log.
       * Output (Response): An AppendEntriesResponse indicating if it successfully processed the request.

   * `public CompletableFuture<String> propose(String command)`
       * Purpose: To reject a client's request because it's not the leader.
       * Input: The client's command.
       * Output: A CompletableFuture that is immediately and exceptionally completed with a
         NotLeaderException, containing the ID of the leader it knows about.

  Candidate.java (The Active Election State)

   * `protected void onBecomeCandidate()`
       * Purpose: This is the first thing that happens when a Follower's timer expires. It starts the
         election.
       * Logic:
           1. Increments currentTerm.
           2. Votes for itself.
           3. Calls requestVotes() to send RPCs to all other nodes.

   * `private void requestVotes()`
       * Purpose: To ask other nodes for their vote.
       * Logic: It loops through all peers and uses the protocol.sendRequestVote() method to send a
         RequestVoteRequest. It provides a callback function to handle the response.

   * `private synchronized void handleVoteResponse(RequestVoteResponse response)`
       * Purpose: To process the vote it receives from another node.
       * Input (Request): The response from a peer, indicating if the vote was granted.
       * Logic: It increments a vote counter. If the counter exceeds the majority (peers.size() / 2), it
         calls becomeLeader().

  Leader.java (The Active Management State)

   * `protected void tick()`
       * Purpose: To maintain authority and replicate logs.
       * Logic: It calls sendHeartbeats(), which sends AppendEntries RPCs to all followers. These RPCs
         serve as both heartbeats (to prevent new elections) and as the vehicle for log replication.

   * `public CompletableFuture<String> propose(String command)`
       * Purpose: To accept a client command, add it to the log, and manage its replication.
       * Input: The client's command.
       * Logic:
           1. Creates a new LogEntry.
           2. Adds it to its own log.
           3. Creates a new CompletableFuture and stores it in a map (clientRequests) where the key is the
              log index of the new entry.
       * Output: The CompletableFuture is returned to the client, who will wait on it.

   * `private void updateCommitIndex()`
       * Purpose: To determine which log entries are now replicated on a majority of servers.
       * Logic: It checks the matchIndex for each follower (which is updated in
         handleAppendEntriesResponse) to find the highest log index that is stored on a majority of nodes.
         It then updates commitIndex.

   * The Final Step of `propose`: Inside updateCommitIndex, after the commitIndex is updated, the leader
     does this:

   1     // ...
   2     if (clientRequests.containsKey(i)) {
   3         clientRequests.get(i).complete("Command processed: " + log.get(i).getCommand());
   4         clientRequests.remove(i);
   5     }
   6     // ...
       * Purpose: To notify the original client that their command is now permanently committed.
       * Logic: It finds the CompletableFuture associated with the now-committed log index i and calls
         .complete(). This "unblocks" the client who was waiting on future.get().

  RaftApi.java (The Smart Client Facade)

   * `public CompletableFuture<String> propose(String command)`
       * Purpose: To provide a single, simple method for clients, hiding all the complexity of leader
         redirection.
       * Input: The client's command.
       * Output: The CompletableFuture that will eventually hold the result.
       * Mapping the `CompletableFuture`: This method is a beautiful example of asynchronous programming.
           1. It calls raftNode.propose(command) and gets a future.
           2. It attaches an .exceptionally() block to that future. This block only runs if the future
              fails.
           3. If it fails with `NotLeaderException`: The block extracts the leader's ID, finds the leader
              node via the protocol, and recursively calls `propose(command)` on itself, but now
              this.raftNode is the correct leader. The result of this new call is returned.
           4. If it succeeds (i.e., it was called on the leader): The original future is returned directly.

  RaftProtocol and InMemoryRaftProtocol (The Communication Layer)

   * `void sendAppendEntries(String peer, AppendEntriesRequest request, Consumer<AppendEntriesResponse> 
     callback)`
       * Purpose: Defines the contract for sending an AppendEntries message.
       * Input: The ID of the peer to send to, the request data, and a callback function to execute when
         the response comes back.
       * Output: None (it's asynchronous).
       * Mapping in `InMemoryRaftProtocol`:

   1         RaftNode peerNode = nodes.get(peer); // Find the destination node in a map
   2         AppendEntriesResponse response = peerNode.handleAppendEntries(request); // Call its 
     method directly
   3         callback.accept(response); // Immediately execute the callback with the response
       * How it would work over a real network: A network implementation would serialize the request object
         (e.g., to JSON), send it over a socket to the peer, wait for a response packet, deserialize it into
          an AppendEntriesResponse, and then execute the callback. The rest of the Raft logic wouldn't need
         to change at all.


  AppendEntriesRequest.java

   * Overall Purpose: This class is a Data Transfer Object (DTO). Its sole purpose is to encapsulate all
     the information a Leader needs to send to a Follower for either a heartbeat or to replicate log
     entries. It's like a standardized form or a message envelope.

   * Detailed Breakdown:

    1     package com.nithesh.raft;
    2 
    3     import java.util.List;
    4 
    5     public class AppendEntriesRequest {
    6         // --- Fields ---
    7         private final int term;
    8         private final String leaderId;
    9         private final int prevLogIndex;
   10         private final int prevLogTerm;
   11         private final List<LogEntry> entries;
   12         private final int leaderCommit;
   13 
   14         // --- Constructor ---
   15         public AppendEntriesRequest(int term, String leaderId, int prevLogIndex, int
      prevLogTerm, List<LogEntry> entries, int leaderCommit) {
   16             this.term = term;
   17             this.leaderId = leaderId;
   18             this.prevLogIndex = prevLogIndex;
   19             this.prevLogTerm = prevLogTerm;
   20             this.entries = entries;
   21             this.leaderCommit = leaderCommit;
   22         }
   23 
   24         // --- Methods (Getters) ---
   25         public int getTerm() { return term; }
   26         public String getLeaderId() { return leaderId; }
   27         public int getPrevLogIndex() { return prevLogIndex; }
   28         public int getPrevLogTerm() { return prevLogTerm; }
   29         public List<LogEntry> getEntries() { return entries; }
   30         public int getLeaderCommit() { return leaderCommit; }
   31     }

   * Fields (The Data):
       * term: The Leader's current term number. A Follower uses this to check if the Leader is legitimate
         or outdated.
       * leaderId: The unique ID of the Leader sending the request. A Follower uses this to know who the
         leader is (important for client redirection).
       * prevLogIndex & prevLogTerm: The index and term of the log entry immediately preceding the new ones
         being sent. The Follower will check its own log at this index. If the entry doesn't exist or the
         term doesn't match, it knows its log is inconsistent with the leader's and will reject the request.
          This is the core consistency check.
       * entries: A List<LogEntry> containing the new log entries the Leader wants the Follower to append.
         This list is empty for simple heartbeats.
       * leaderCommit: The Leader's commitIndex. The Follower uses this to know which entries are now
         considered committed and can be applied to its state machine.

   * Constructor:
       * Signature: public AppendEntriesRequest(int term, String leaderId, ...)
       * Purpose: To create an immutable AppendEntriesRequest object.
       * Input: It takes values for all the fields described above.
       * Output: A new, fully-populated AppendEntriesRequest object.

   * Getters (`getTerm()`, etc.):
       * Purpose: These are simple public methods to allow other parts of the code (specifically, the
         handleAppendEntries method in a Follower) to access the private data within the object.

   * Connections to Other Files:
       * Created by: Leader.java (in the sendAppendEntries method).
       * Consumed by: Follower.java and Candidate.java (in the handleAppendEntries method).
       * Transported by: RaftProtocol.java.

  ---

  AppendEntriesResponse.java

   * Overall Purpose: A DTO that represents a Follower's response back to a Leader after receiving an
     AppendEntriesRequest.

   * Detailed Breakdown:

    1     package com.nithesh.raft;
    2 
    3     public class AppendEntriesResponse {
    4         // --- Fields ---
    5         private final int term;
    6         private final boolean success;
    7 
    8         // --- Constructor ---
    9         public AppendEntriesResponse(int term, boolean success) {
   10             this.term = term;
   11             this.success = success;
   12         }
   13 
   14         // --- Methods (Getters) ---
   15         public int getTerm() { return term; }
   16         public boolean isSuccess() { return success; }
   17     }

   * Fields:
       * term: The Follower's current term. The Leader uses this to discover if there's a new Leader or a
         new election has occurred (i.e., if response.getTerm() > leader.currentTerm).
       * success: A boolean. true if the Follower accepted the entries because its log was consistent.
         false if it rejected them (because the prevLogIndex/prevLogTerm check failed).

   * Constructor & Getters: These serve the same purpose as in the request object—to create the object and
     provide access to its data.

   * Connections to Other Files:
       * Created by: Follower.java and Candidate.java (at the end of handleAppendEntries).
       * Consumed by: Leader.java (in the handleAppendEntriesResponse callback).

  ---

  (This pattern repeats for `RequestVoteRequest.java`, `RequestVoteResponse.java`, and `LogEntry.java`, 
  which are all simple DTOs. I will briefly cover them.)

  ---

  RequestVoteRequest.java

   * Overall Purpose: DTO for a Candidate to request a vote from another node during an election.
   * Fields:
       * term: The Candidate's term.
       * candidateId: The ID of the Candidate requesting the vote.
       * lastLogIndex & lastLogTerm: The index and term of the Candidate's last log entry. A voter uses
         this to ensure it only votes for candidates with logs that are at least as up-to-date as its own.
   * Connections: Created by Candidate.java (requestVotes), consumed by all nodes (handleRequestVote).

  ---

  RequestVoteResponse.java

   * Overall Purpose: DTO for a node to respond to a vote request.
   * Fields:
       * term: The voter's current term, so the candidate can update itself if it's behind.
       * voteGranted: true if the node voted for the candidate, false otherwise.
   * Connections: Created by all nodes (handleRequestVote), consumed by Candidate.java
     (handleVoteResponse).

  ---

  LogEntry.java

   * Overall Purpose: DTO representing a single, numbered command in the distributed log.
   * Fields:
       * term: The leader's term when the entry was created.
       * command: The actual command from the client (e.g., "SET x = 5").
   * Connections: Created by Leader.java (propose), stored in RaftStorage, and replicated to all nodes.

  ---

  RaftStorage.java

   * Overall Purpose: To abstract the storage mechanism for a node's persistent state. This allows you to
     easily swap the storage backend (e.g., from in-memory to a file-based system) without changing the
     Raft logic. The current implementation is for testing and is NOT persistent.

   * Detailed Breakdown:

    1     package com.nithesh.raft;
    2 
    3     import java.util.ArrayList;
    4     import java.util.List;
    5 
    6     public class RaftStorage {
    7         // --- Fields ---
    8         private int currentTerm;
    9         private String votedFor;
   10         private List<LogEntry> log;
   11         private final String nodeId; // For potential file-based storage later
   12 
   13         // --- Constructor ---
   14         public RaftStorage(String nodeId) {
   15             this.nodeId = nodeId;
   16             this.currentTerm = 0;
   17             this.votedFor = null;
   18             this.log = new ArrayList<>();
   19         }
   20 
   21         // --- Methods ---
   22         public synchronized int loadCurrentTerm() { return currentTerm; }
   23         public synchronized void saveCurrentTerm(int currentTerm) { this.currentTerm =
      currentTerm; }
   24 
   25         public synchronized String loadVotedFor() { return votedFor; }
   26         public synchronized void saveVotedFor(String votedFor) { this.votedFor = votedFor; }
   27 
   28         public synchronized List<LogEntry> loadLog() { return new ArrayList<>(log); }
   29         public synchronized void saveLog(List<LogEntry> log) { this.log = new ArrayList
      <>(log); }
   30     }

   * Fields: These are in-memory variables that simulate a persistent store for the three critical pieces
     of Raft state that must survive a crash.
   * Methods:
       * `load...` methods:
           * Input: None.
           * Output: The current value of the corresponding field.
       * `save...` methods:
           * Input: The new value to be "saved."
           * Output: void. The side effect is updating the field.
       * `synchronized` keyword: This is important. It ensures that only one thread can access a method at
         a time, preventing race conditions where one thread tries to read a value while another is in the
         middle of writing it.

   * Connections to Other Files:
       * An instance of RaftStorage is created in RaftApp.java and passed to the constructor of each
         RaftNode (Follower).
       * The save... methods are called whenever persistent state changes (e.g., Leader.propose calls
         saveLog, Candidate.onBecomeCandidate calls saveCurrentTerm).
       * The load... methods are called when a RaftNode is first constructed to restore its state.

  ---

  RaftProtocol.java (The Interface)

   * Overall Purpose: To define a strict contract for network communication. It decouples the Raft logic
     from the network implementation.

   * Detailed Breakdown:

   1     package com.nithesh.raft;
   2 
   3     import java.util.function.Consumer;
   4 
   5     public interface RaftProtocol {
   6         void sendAppendEntries(String peer, AppendEntriesRequest request,
     Consumer<AppendEntriesResponse> callback);
   7         void sendRequestVote(String peer, RequestVoteRequest request,
     Consumer<RequestVoteResponse> callback);
   8         void setRaftNode(RaftNode node);
   9     }

   * Methods:
       * `sendAppendEntries(...)` & `sendRequestVote(...)`:
           * Purpose: To send an RPC message to another node asynchronously.
           * Input:
               * peer: The ID of the destination node.
               * request: The data packet to send.
               * callback: A function (Consumer) that will be executed later, when the response is
                 received. This is the key to non-blocking communication.
           * Output: void.
       * `setRaftNode(...)`: A utility method for implementations that might need a reference to the node
         they belong to.

  ---

  InMemoryRaftProtocol.java (The Implementation)

   * Overall Purpose: A "fake" network for testing. It implements the RaftProtocol interface but doesn't
     use the actual network. It just calls methods on Java objects directly in memory.

   * Detailed Breakdown:

    1     // ...
    2     public class InMemoryRaftProtocol implements RaftProtocol {
    3         private final Map<String, RaftNode> nodes = new ConcurrentHashMap<>();
    4 
    5         public void registerNode(RaftNode node) {
    6             nodes.put(node.nodeId, node);
    7             node.setRaftProtocol(this);
    8         }
    9 
   10         public RaftNode getNode(String nodeId) {
   11             return nodes.get(nodeId);
   12         }
   13 
   14         @Override
   15         public void sendAppendEntries(String peer, AppendEntriesRequest request,
      Consumer<AppendEntriesResponse> callback) {
   16             RaftNode peerNode = nodes.get(peer); // 1. Find the node in the map
   17             if (peerNode != null) {
   18                 AppendEntriesResponse response = peerNode.handleAppendEntries(request); // 2. 
      Call its handler directly
   19                 callback.accept(response); // 3. Immediately run the callback
   20             }
   21         }
   22         // ... sendRequestVote is identical ...
   23     }

   * `nodes` Map: This is the "fake network." It holds a reference to every RaftNode object in the cluster.
   * `registerNode(...)`: Called by RaftApp to add all the created nodes to the map.
   * `getNode(...)`: Used by RaftApi to find the leader object during redirection.
   * `sendAppendEntries(...)` Walkthrough:
       1. It looks up the destination peer in its internal nodes map to get the actual RaftNode object.
       2. Instead of serializing data and sending it over a network, it simply calls the handleAppendEntries
          method directly on the peerNode object, getting the response object back immediately.
       3. It then immediately executes the callback function that the caller provided, passing it the
          response.

   * Connections to Other Files:
       * Created in RaftApp.
       * Used by Leader and Candidate to "send" messages.
       * Used by RaftApi to find the leader node.

  RaftNode.java (The Abstract Foundation)

   * Overall Purpose: This abstract class is the blueprint for any Raft node. It contains all the state and
     behavior that is common to Followers, Candidates, and Leaders. It defines what a Raft node is, while
     the subclasses define how it behaves in a particular role.

   * Detailed Breakdown:

    1     package com.nithesh.raft;
    2 
    3     import java.util.List;
    4     import java.util.concurrent.Executors;
    5     import java.util.concurrent.ScheduledExecutorService;
    6     import java.util.concurrent.TimeUnit;
    7     // import for CompletableFuture is missing but implied
    8     import java.util.concurrent.CompletableFuture;
    9 
   10 
   11     public abstract class RaftNode {
   12         // --- Fields (State) ---
   13         protected int currentTerm;
   14         protected String votedFor;
   15         protected List<LogEntry> log;
   16 
   17         protected int commitIndex;
   18         protected int lastApplied;
   19 
   20         protected final String nodeId;
   21         protected final List<String> peers;
   22 
   23         protected Role currentRole; // This is more for conceptual clarity; the actual 
      object's class defines the role
   24         protected RaftStorage storage;
   25         protected RaftProtocol protocol;
   26         protected ScheduledExecutorService scheduler;
   27 
   28         // --- Constructor ---
   29         public RaftNode(String nodeId, List<String> peers, RaftStorage storage, RaftProtocol
      protocol) {
   30             this.nodeId = nodeId;
   31             this.peers = peers;
   32             this.storage = storage;
   33             this.protocol = protocol;
   34             this.scheduler = Executors.newSingleThreadScheduledExecutor();
   35 
   36             // Load persistent state
   37             this.currentTerm = storage.loadCurrentTerm();
   38             this.votedFor = storage.loadVotedFor();
   39             this.log = storage.loadLog();
   40 
   41             // Initialize volatile state
   42             this.commitIndex = 0;
   43             this.lastApplied = 0;
   44             this.currentRole = Role.FOLLOWER; // All nodes start as followers
   45         }
   46 
   47         // --- Core Methods ---
   48         public void setRaftProtocol(RaftProtocol protocol) {
   49             this.protocol = protocol;
   50         }
   51 
   52         public synchronized void start() {
   53             scheduler.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
   54         }
   55 
   56         public synchronized void stop() {
   57             scheduler.shutdownNow();
   58         }
   59 
   60         // --- Role Transition Methods ---
   61         protected synchronized void becomeFollower(int newTerm) {
   62             // Logic to transition is handled by creating a new object in the actual 
      implementation
   63             // This method signature is a placeholder for what should happen
   64             this.currentRole = Role.FOLLOWER;
   65             this.currentTerm = newTerm;
   66             this.votedFor = null;
   67             onBecomeFollower();
   68         }
   69         // ... becomeCandidate() and becomeLeader() are similar ...
   70 
   71         // --- Abstract Methods (To be implemented by subclasses) ---
   72         public abstract AppendEntriesResponse handleAppendEntries(AppendEntriesRequest req);
   73         public abstract RequestVoteResponse handleRequestVote(RequestVoteRequest req);
   74         protected abstract void onBecomeLeader();
   75         protected abstract void onBecomeFollower();
   76         protected abstract void onBecomeCandidate();
   77         protected abstract void tick();
   78 
   79         // --- Client Interaction ---
   80         public CompletableFuture<String> propose(String command) {
   81             // Default implementation, to be overridden
   82             return CompletableFuture.completedFuture("Command processed");
   83         }
   84 
   85         protected void applyStateMachine(LogEntry logEntry) {
   86             System.out.println("Applying to state machine: " + logEntry.getCommand());
   87         }
   88     }

   * Fields:
       * currentTerm, votedFor, log: The three state variables that Raft requires to be saved to persistent
         storage.
       * commitIndex, lastApplied: Volatile state. commitIndex is the highest log entry index known to be
         committed. lastApplied is the highest index applied to the state machine.
       * nodeId, peers: Configuration data identifying the node and its cluster peers.
       * storage, protocol: The dependency-injected modules for storage and networking.
       * scheduler: A standard Java utility to run tasks at a fixed interval. This is the engine that drives
         the tick() method.

   * Constructor:
       * Purpose: To initialize a new Raft node.
       * Input: The node's configuration (nodeId, peers) and its dependencies (storage, protocol).
       * Line-by-Line:
           1. It saves the configuration and dependencies to its fields.
           2. It creates a ScheduledExecutorService which is a background thread pool of size 1, dedicated
              to running timed tasks for this node.
           3. It calls the load... methods on the storage object to restore its persistent state. This is
              crucial for recovering from a crash.
           4. It initializes volatile state variables like commitIndex to zero.
           5. It sets its initial role to FOLLOWER.

   * `start()` method:
       * Purpose: To "boot up" the node's active logic.
       * Line-by-Line:
           1. scheduler.scheduleAtFixedRate(...): This is the key line. It tells the scheduler to call the
              method referenced by this::tick (which will be the tick() method of the concrete subclass,
              e.g., Follower.tick()).
           2. The timing arguments 100, 100, TimeUnit.MILLISECONDS mean: "Wait 100ms before the first
              execution, and then execute it every 100ms thereafter."

   * `become...()` methods: These methods are conceptual placeholders for role transitions. In a more
     advanced implementation, you might replace the entire node object with a new one (e.g., node = new 
     Candidate(state)). In our current version, the logic is simpler, but these methods centralize the
     state changes required for a transition.

   * `abstract` methods: These are the core of the design. RaftNode says, "I don't know how to handle a
     tick or an RPC, because it depends on my role. I require my concrete subclasses (Follower, Leader,
     Candidate) to provide an implementation for these methods."

   * `propose(...)` method:
       * Purpose: Provides a default implementation for client proposals. This is overridden by each role
         to provide specific behavior. The base implementation here is mostly a placeholder.

   * `applyStateMachine(...)` method:
       * Purpose: A placeholder for applying a committed command to the actual application logic.
       * Input: The LogEntry that has been safely committed.
       * Output: void.
       * Line-by-Line: It simply prints the command to the console. In a real key-value store, this method
         would contain a switch statement or parser to execute commands like "SET" or "DELETE" on an
         internal data structure (like a HashMap).

   * Connections to Other Files:
       * It is the parent class for Follower, Candidate, and Leader.
       * It uses RaftStorage and RaftProtocol.
       * It is instantiated (via its subclasses) in RaftApp.

  ---

  Follower.java

   * Overall Purpose: This is the default state for a node. A Follower is passive. It responds to requests
     from Leaders and Candidates and starts an election if it doesn't hear from a Leader for a specific
     amount of time.

   * Detailed Breakdown:

    1     // ...
    2     public class Follower extends RaftNode {
    3         private long lastHeartbeatTime;
    4         private final int minElectionTimeout = 150;
    5         private final int maxElectionTimeout = 300;
    6         private final Random random = new Random();
    7         private String leaderId;
    8 
    9         public Follower(...) {
   10             super(...); // Calls the RaftNode constructor
   11             this.lastHeartbeatTime = System.currentTimeMillis();
   12         }
   13 
   14         @Override
   15         protected void tick() {
   16             long now = System.currentTimeMillis();
   17             if (now - this.lastHeartbeatTime > getRandomElectionTimeout()) {
   18                 becomeCandidate(); // The election trigger!
   19             }
   20         }
   21 
   22         @Override
   23         public synchronized AppendEntriesResponse handleAppendEntries(AppendEntriesRequest
      req) {
   24             // 1. Basic consistency check
   25             if (req.getTerm() < currentTerm) {
   26                 return new AppendEntriesResponse(currentTerm, false);
   27             }
   28             // 2. Discover a new term or leader
   29             if (req.getTerm() > currentTerm) {
   30                 becomeFollower(req.getTerm());
   31             }
   32             leaderId = req.getLeaderId();
   33             lastHeartbeatTime = System.currentTimeMillis(); // 3. Reset the timer!
   34 
   35             // 4. Log consistency check
   36             if (req.getPrevLogIndex() > 0 && (req.getPrevLogIndex() >= log.size() ||
      log.get(req.getPrevLogIndex()).getTerm() != req.getPrevLogTerm())) {
   37                 return new AppendEntriesResponse(currentTerm, false);
   38             }
   39 
   40             // 5. Append new entries
   41             // ... logic to truncate and add entries ...
   42             storage.saveLog(log);
   43 
   44             // 6. Update commit index
   45             if (req.getLeaderCommit() > commitIndex) {
   46                 commitIndex = Math.min(req.getLeaderCommit(), log.size() - 1);
   47                 // In a real system, you'd apply to state machine here
   48             }
   49             return new AppendEntriesResponse(currentTerm, true);
   50         }
   51 
   52         @Override
   53         public synchronized RequestVoteResponse handleRequestVote(RequestVoteRequest req) {
   54             // ... logic to check if term is valid, if log is up-to-date, and if it hasn't 
      voted yet ...
   55             if (canGrantVote) {
   56                 votedFor = req.getCandidateId();
   57                 storage.saveVotedFor(votedFor);
   58                 return new RequestVoteResponse(currentTerm, true);
   59             } else {
   60                 return new RequestVoteResponse(currentTerm, false);
   61             }
   62         }
   63 
   64         @Override
   65         public CompletableFuture<String> propose(String command) {
   66             CompletableFuture<String> future = new CompletableFuture<>();
   67             future.completeExceptionally(new NotLeaderException(leaderId));
   68             return future;
   69         }
   70         // ... other overridden methods ...
   71     }

   * `tick()` method:
       * Purpose: To check for a leader timeout. This is the most important method in this class.
       * Line-by-Line:
           1. long now = System.currentTimeMillis();: Get the current time.
           2. if (now - this.lastHeartbeatTime > getRandomElectionTimeout()): Calculate the time elapsed
              since the last heartbeat. Compare it to a newly generated random timeout. This randomness is
              critical to prevent split votes where multiple followers become candidates simultaneously.
           3. becomeCandidate();: If the timeout is exceeded, the node initiates a role transition to start
              an election.

   * `handleAppendEntries(...)` method:
       * Purpose: To respond to a Leader's heartbeat/replication RPC.
       * Line-by-Line Walkthrough:
           1. Term Check: If the request is from an old, deposed leader (req.getTerm() < currentTerm), reject
              it.
           2. Discovery: If the request has a higher term, it means a new leader has been elected. The
              follower updates its own term and state.
           3. TIMER RESET: This is the most important side effect. By updating lastHeartbeatTime, the tick()
              method's timeout check will now fail, preventing this follower from starting an election.
           4. Log Consistency: It performs the crucial Raft check on prevLogIndex and prevLogTerm. If the
              follower's log doesn't match the leader's at that point, it rejects the request, forcing the
              leader to send earlier log entries on the next attempt.
           5. Append: If the logs are consistent, it appends the new entries.
           6. Commit: It updates its own commitIndex based on the leader's.
       * Output: A response object indicating success: true or success: false.

   * `handleRequestVote(...)` method:
       * Purpose: To decide whether to grant a vote to a requesting Candidate.
       * Logic: It will grant the vote only if all of the following are true:
           * The candidate's term is at least as high as its own.
           * It has not already voted for someone else in the current term.
           * The candidate's log is at least as "up-to-date" as its own log (this is a specific check
             defined by the Raft paper to ensure leaders have all committed entries).
       * Output: A response with voteGranted: true or voteGranted: false.

   * `propose(...)` method:
       * Purpose: To reject a client request.
       * Line-by-Line:
           1. It creates an empty CompletableFuture.
           2. It immediately fails this future by calling completeExceptionally, passing in a new
              NotLeaderException. It helpfully includes the leaderId it learned from the last AppendEntries
              RPC.
       * Output: A future that is guaranteed to fail, signaling to the RaftApi that it needs to redirect the
         request.

  Candidate.java

   * Overall Purpose: This class represents the temporary state a node enters to run for election. Its
     single goal is to gather enough votes to become the new Leader. It's an active, but short-lived,
     state. If it wins, it becomes a Leader. If it loses (by discovering another Leader or a new term), it
     reverts to being a Follower.

   * Detailed Breakdown:

    1     package com.nithesh.raft;
    2 
    3     import java.util.List;
    4     import java.util.concurrent.atomic.AtomicInteger;
    5     // import for CompletableFuture is missing but implied
    6     import java.util.concurrent.CompletableFuture;
    7 
    8 
    9     public class Candidate extends RaftNode {
   10         // --- Fields ---
   11         private AtomicInteger votesReceived;
   12 
   13         // --- Constructor ---
   14         public Candidate(String nodeId, List<String> peers, RaftStorage storage, RaftProtocol
      protocol) {
   15             super(nodeId, peers, storage, protocol);
   16         }
   17 
   18         // --- Core Logic ---
   19         @Override
   20         protected void onBecomeCandidate() {
   21             System.out.println(nodeId + " is a candidate.");
   22             currentTerm++; // 1. Increment term
   23             votedFor = nodeId; // 2. Vote for self
   24             storage.saveCurrentTerm(currentTerm); // 3. Persist state
   25             storage.saveVotedFor(votedFor);
   26             votesReceived = new AtomicInteger(1); // 4. Initialize vote count
   27             requestVotes(); // 5. Start the election
   28         }
   29 
   30         private void requestVotes() {
   31             for (String peer : peers) {
   32                 if (!peer.equals(nodeId)) {
   33                     int lastLogIndex = log.size() - 1;
   34                     int lastLogTerm = lastLogIndex >= 0 ? log.get(lastLogIndex).getTerm() : 0;
   35                     RequestVoteRequest request = new RequestVoteRequest(currentTerm, nodeId,
      lastLogIndex, lastLogTerm);
   36                     protocol.sendRequestVote(peer, request, (response) -> {
   37                         handleVoteResponse(response); // The callback
   38                     });
   39                 }
   40             }
   41         }
   42 
   43         private synchronized void handleVoteResponse(RequestVoteResponse response) {
   44             if (response.isVoteGranted()) {
   45                 if (votesReceived.incrementAndGet() > peers.size() / 2) {
   46                     becomeLeader(); // Won the election
   47                 }
   48             } else if (response.getTerm() > currentTerm) {
   49                 becomeFollower(response.getTerm()); // Discovered a higher term, must step 
      down
   50             }
   51         }
   52 
   53         @Override
   54         protected void tick() {
   55             // The election timeout is reset when it becomes a candidate.
   56             // It will start another election if this one times out.
   57             // (A more robust implementation might have a separate timeout here)
   58         }
   59 
   60         // --- RPC Handling ---
   61         @Override
   62         public synchronized AppendEntriesResponse handleAppendEntries(AppendEntriesRequest
      req) {
   63             if (req.getTerm() >= currentTerm) {
   64                 // If we receive a valid heartbeat, there's a new leader.
   65                 // We must step down and become a follower.
   66                 becomeFollower(req.getTerm());
   67             }
   68             return new AppendEntriesResponse(currentTerm, false);
   69         }
   70 
   71         @Override
   72         public synchronized RequestVoteResponse handleRequestVote(RequestVoteRequest req) {
   73             // If another candidate has a higher term, we step down and may vote for them.
   74             if (req.getTerm() > currentTerm) {
   75                 becomeFollower(req.getTerm());
   76                 // In a real implementation, you'd then re-process the request as a follower.
   77                 return new RequestVoteResponse(currentTerm, false);
   78             }
   79             // Otherwise, deny the vote because we already voted for ourselves.
   80             return new RequestVoteResponse(currentTerm, false);
   81         }
   82 
   83         @Override
   84         public CompletableFuture<String> propose(String command) {
   85             // A candidate cannot accept client commands.
   86             CompletableFuture<String> future = new CompletableFuture<>();
   87             future.completeExceptionally(new NotLeaderException(null)); // No known leader
   88             return future;
   89         }
   90         // ... other overridden methods ...
   91     }

   * `votesReceived` Field:
       * An AtomicInteger is used here instead of a plain int because the vote responses will arrive
         asynchronously from different background threads (managed by the protocol layer). This class
         ensures that incrementing the count is thread-safe.

   * `onBecomeCandidate()` method:
       * Purpose: This is the entry point for the Candidate state, called by a Follower when its election
         timer expires.
       * Line-by-Line:
           1. currentTerm++: The first action of an election is to advance to a new term.
           2. votedFor = nodeId: The candidate votes for itself.
           3. storage.save...: It immediately persists this new term and its vote to stable storage. This is
              critical. If it crashes and restarts, it won't vote for someone else in the same term.
           4. votesReceived = new AtomicInteger(1): It initializes its vote count to 1 (for its own vote).
           5. requestVotes(): It kicks off the process of asking peers for their votes.

   * `requestVotes()` method:
       * Purpose: To construct and send RequestVoteRequest RPCs to all other nodes in the cluster.
       * Line-by-Line:
           1. It loops through all peers.
           2. It gets the index and term of its last log entry to prove its log is up-to-date.
           3. It creates the RequestVoteRequest DTO.
           4. protocol.sendRequestVote(...): It uses the protocol layer to send the request. The most
              important part is the last argument: (response) -> { handleVoteResponse(response); }. This is
              a lambda function, a compact way of defining the callback that the protocol will execute when
              a response arrives.

   * `handleVoteResponse(...)` method:
       * Purpose: This is the callback function for the sendRequestVote RPC. It processes the outcome of a
         vote request.
       * Input: The RequestVoteResponse from a peer.
       * Line-by-Line:
           1. if (response.isVoteGranted()): If the peer said "yes":
           2. votesReceived.incrementAndGet(): Safely increment the vote count.
           3. if (... > peers.size() / 2): Check if the new count is a strict majority.
           4. becomeLeader(): If it is, the election is won! Transition to the Leader state.
           5. else if (response.getTerm() > currentTerm): If the peer denied the vote and reported a higher
              term number, it means another election is happening or a new leader already exists. The
              candidate is now invalid and must step down by becoming a follower in that new, higher term.

   * `handleAppendEntries(...)` method:
       * Purpose: To handle an RPC from a potential Leader while in the Candidate state.
       * Logic: If an AppendEntriesRequest arrives with a term greater than or equal to the candidate's own
         term, it is definitive proof that another node has already won the election and is the legitimate
         Leader. The candidate's duty is to immediately give up its candidacy and becomeFollower.

   * `propose(...)` method:
       * Purpose: To reject a client request.
       * Logic: Similar to a Follower, but it calls new NotLeaderException(null) because during an
         election, the leader is unknown.

  ---

  Leader.java

   * Overall Purpose: This is the executive of the cluster. It is the only node that can accept client
     commands, and it is responsible for orchestrating log replication and telling followers when entries
     are committed.

   * Detailed Breakdown:

    1     // ...
    2     public class Leader extends RaftNode {
    3         // --- Fields ---
    4         private Map<String, Integer> nextIndex;
    5         private Map<String, Integer> matchIndex;
    6         private Map<Integer, CompletableFuture<String>> clientRequests;
    7 
    8         // ... Constructor ...
    9 
   10         @Override
   11         protected void onBecomeLeader() {
   12             System.out.println(nodeId + " is the leader.");
   13             int lastLogIndex = log.size();
   14             for (String peer : peers) {
   15                 nextIndex.put(peer, lastLogIndex + 1); // Initialize to leader's last log 
      index + 1
   16                 matchIndex.put(peer, 0);
   17             }
   18             sendHeartbeats(); // Immediately assert authority
   19         }
   20 
   21         @Override
   22         protected void tick() {
   23             sendHeartbeats();
   24         }
   25 
   26         private void sendHeartbeats() {
   27             for (String peer : peers) {
   28                 if (!peer.equals(nodeId)) {
   29                     sendAppendEntries(peer);
   30                 }
   31             }
   32         }
   33 
   34         private void sendAppendEntries(String peer) {
   35             int prevLogIndex = nextIndex.get(peer) - 1;
   36             // ... logic to get prevLogTerm and entries to send ...
   37             AppendEntriesRequest request = new AppendEntriesRequest(...);
   38             protocol.sendAppendEntries(peer, request, (response) -> {
   39                 handleAppendEntriesResponse(peer, response);
   40             });
   41         }
   42 
   43         private synchronized void handleAppendEntriesResponse(String peer,
      AppendEntriesResponse response) {
   44             if (response.isSuccess()) {
   45                 // Follower's log is now consistent with leader's up to the sent entries
   46                 nextIndex.put(peer, log.size());
   47                 matchIndex.put(peer, log.size() - 1);
   48                 updateCommitIndex(); // Check if we can now commit something
   49             } else {
   50                 if (response.getTerm() > currentTerm) {
   51                     becomeFollower(response.getTerm()); // Step down
   52                 } else {
   53                     // Follower's log is inconsistent. Decrement nextIndex and retry on the 
      next tick.
   54                     nextIndex.put(peer, nextIndex.get(peer) - 1);
   55                 }
   56             }
   57         }
   58 
   59         private void updateCommitIndex() {
   60             int majority = peers.size() / 2;
   61             for (int N = log.size() - 1; N > commitIndex; N--) { // Check potential new commit
      indexes
   62                 if (log.get(N).getTerm() == currentTerm) { // Only commit entries from own 
      term
   63                     int count = 1; // Count self
   64                     for (String peer : peers) {
   65                         if (matchIndex.getOrDefault(peer, 0) >= N) {
   66                             count++;
   67                         }
   68                     }
   69                     if (count > majority) {
   70                         commitIndex = N; // Found the new commit index!
   71                         // Apply newly committed entries
   72                         for (int i = lastApplied + 1; i <= commitIndex; i++) {
   73                             applyStateMachine(log.get(i));
   74                             if (clientRequests.containsKey(i)) {
   75                                 clientRequests.get(i).complete("Command processed: " +
      log.get(i).getCommand());
   76                                 clientRequests.remove(i);
   77                             }
   78                         }
   79                         lastApplied = commitIndex;
   80                         break; // Exit loop once highest possible index is found
   81                     }
   82                 }
   83             }
   84         }
   85 
   86         @Override
   87         public CompletableFuture<String> propose(String command) {
   88             LogEntry newEntry = new LogEntry(currentTerm, command);
   89             log.add(newEntry);
   90             storage.saveLog(log);
   91             CompletableFuture<String> future = new CompletableFuture<>();
   92             clientRequests.put(log.size() - 1, future);
   93             return future;
   94         }
   95         // ... other RPC handlers simply step down if they see a higher term ...
   96     }

   * Leader-Specific Fields:
       * nextIndex: A map where nextIndex.get("node2") stores the index of the next log entry the leader
         will send to node2. This is the leader's guess of how up-to-date each follower is.
       * matchIndex: A map where matchIndex.get("node2") stores the highest log entry index known to be
         replicated on node2.
       * clientRequests: A map where the key is a log index and the value is the CompletableFuture returned
         to a client. This is how the leader remembers which client is waiting for which entry to be
         committed.

   * `onBecomeLeader()` method:
       * Purpose: To initialize the leader's state immediately after winning an election.
       * Line-by-Line:
           1. It reinitializes nextIndex for every follower to be the index right after the end of its own
              log.
           2. It reinitializes matchIndex to 0 for all followers.
           3. It calls sendHeartbeats() immediately to establish its authority and prevent new elections.

   * `sendAppendEntries(...)` method:
       * Purpose: To construct and send an AppendEntries RPC tailored to a specific follower.
       * Logic: It uses the peer's nextIndex value to figure out which log entries to send. If nextIndex is
         low, it means the follower is far behind, and the entries list will contain many entries. If the
         follower is up-to-date, the entries list will be empty (a pure heartbeat).

   * `handleAppendEntriesResponse(...)` method:
       * Purpose: The callback that processes a follower's reply.
       * Logic:
           * If `success`: The follower accepted the entries. The leader can now confidently update that
             follower's nextIndex and matchIndex to reflect its new, up-to-date state. After updating, it
             calls updateCommitIndex() because this successful replication might have created a new
             majority.
           * If `!success`: The follower rejected the entries because of a log inconsistency. The leader
             decrements that follower's nextIndex by one. The next time sendAppendEntries is called for
             that peer, it will try sending the entry from one position earlier, effectively walking
             backwards through its log until it finds a point of consistency.

   * `updateCommitIndex()` method:
       * Purpose: The core of the commitment logic. To find the highest log index replicated on a majority
         of servers.
       * Line-by-Line:
           1. It loops backwards from the end of its log.
           2. For each index N, it checks if it can be the new commitIndex.
           3. It counts how many followers have matchIndex >= N.
           4. If the count is a majority, it has found the new commitIndex.
           5. It then iterates from the lastApplied index up to the new commitIndex.
           6. For each newly committed entry, it calls applyStateMachine and, crucially, checks the
              clientRequests map.
           7. If a client future exists for that index, it calls .complete(), which notifies the waiting
              client.

   * `propose(...)` method:
       * Purpose: To accept a client command.
       * Line-by-Line:
           1. Creates the LogEntry.
           2. Adds it to its own log.
           3. Creates the CompletableFuture that will be returned to the client.
           4. Stores the future in the `clientRequests` map, creating the link between the log index and the
              client waiting for it.

  ---

  I will now proceed with the final set of files: NotLeaderException, RaftApi, and RaftApp.

  NotLeaderException.java

   * Overall Purpose: A simple, custom RuntimeException used as a signaling mechanism. Its only job is to
     carry the ID of the current leader from a Follower to the RaftApi during the client redirection
     process.

   * Detailed Breakdown:

    1     package com.nithesh.raft;
    2 
    3     public class NotLeaderException extends RuntimeException {
    4         private final String leaderId;
    5 
    6         public NotLeaderException(String leaderId) {
    7             this.leaderId = leaderId;
    8         }
    9 
   10         public String getLeaderId() {
   11             return leaderId;
   12         }
   13     }
   * `leaderId` Field: Stores the ID of the leader. This can be null if the node throwing the exception
     (e.g., a Candidate) doesn't know who the leader is.
   * Constructor: Takes the leaderId as input to create the exception object.
   * `getLeaderId()` method: A standard getter for the RaftApi to retrieve the leader's ID from the
     exception object.
   * Connections: Created by Follower and Candidate in their propose methods. Caught and consumed by
     RaftApi in its propose method.

  ---

  RaftApi.java

   * Overall Purpose: To provide a stable, intelligent, and simple-to-use entry point for any client
     application. It completely hides the complexity of Raft's distributed nature, such as finding the
     leader. The client interacts with this class as if it were talking to a single, reliable server.

   * Detailed Breakdown:

    1     package com.nithesh.raft;
    2 
    3     import java.util.concurrent.CompletableFuture;
    4 
    5     public class RaftApi {
    6         private RaftNode raftNode; // The current node to send requests to
    7         private final InMemoryRaftProtocol protocol;
    8 
    9         public RaftApi(RaftNode raftNode, InMemoryRaftProtocol protocol) {
   10             this.raftNode = raftNode;
   11             this.protocol = protocol;
   12         }
   13 
   14         public CompletableFuture<String> propose(String command) {
   15             // 1. Initial attempt
   16             CompletableFuture<String> future = raftNode.propose(command);
   17 
   18             // 2. Attach the redirection logic
   19             return future.exceptionally(ex -> {
   20                 // 3. Check if the failure was the one we expect
   21                 if (ex.getCause() instanceof NotLeaderException) {
   22                     NotLeaderException notLeaderException = (NotLeaderException)
      ex.getCause();
   23                     String leaderId = notLeaderException.getLeaderId();
   24 
   25                     // 4. If we know who the leader is, redirect.
   26                     if (leaderId != null) {
   27                         RaftNode leaderNode = protocol.getNode(leaderId);
   28                         if (leaderNode != null) {
   29                             this.raftNode = leaderNode; // 5. IMPORTANT: Update internal state
   30                             return propose(command);    // 6. Recursive call to retry
   31                         }
   32                     }
   33                 }
   34                 // 7. For any other exception, or if leader is unknown, fail fast.
   35                 return CompletableFuture.failedFuture(ex);
   36             });
   37         }
   38     }

   * Fields:
       * raftNode: The RaftNode object that the API will send its next request to. This is mutable; it
         starts as the initial node but changes to the leader once discovered.
       * protocol: A reference to the protocol layer, needed to look up other nodes by their ID.

   * `propose(...)` method:
       * Purpose: To reliably submit a command to the cluster's leader.
       * Input: The client's command.
       * Output: A CompletableFuture that will resolve with the command's result once committed.
       * Line-by-Line Asynchronous Walkthrough:
           1. Initial Attempt: It calls propose on its current raftNode. If this node is the leader, the
              returned future will eventually complete successfully. If it's a follower, the future will fail
              almost instantly with a NotLeaderException.
           2. Attaching Logic: .exceptionally() is a powerful method from CompletableFuture. It says: "Do not
              execute this block of code unless the future fails. If it does fail, run this code, and its
              return value will become the new result of the chain."
           3. Checking the Cause: The code inside the block first checks if the failure was indeed a
              NotLeaderException.
           4. Redirecting: If it was, it gets the leaderId. If the ID is known (!= null), it proceeds.
           5. State Update: It updates its own this.raftNode field to point directly to the leader object.
              From now on, any subsequent calls to propose on this RaftApi instance will go directly to the
              leader on the first try.
           6. Retry: It calls propose(command) on itself again. Because this.raftNode has been updated, this
              recursive call now sends the request directly to the leader. The CompletableFuture returned by
              this second call is then returned from the exceptionally block, effectively replacing the
              original failed future with the new, correct one.
           7. Failing Fast: If the exception was not NotLeaderException, or if the leaderId was null (e.g.,
              during an election), the API gives up and returns a failed future to the client.

  ---

  RaftApp.java

   * Overall Purpose: The main executable class that bootstraps and demonstrates the entire Raft cluster.
     It simulates a real-world scenario where you would start up your server processes and then have a
     client connect to them.

   * Detailed Breakdown:

    1     package com.nithesh.app;
    2 
    3     import com.nithesh.raft.*;
    4     import java.util.Arrays;
    5     import java.util.List;
    6     import java.util.concurrent.CompletableFuture;
    7 
    8     public class RaftApp {
    9         public static void main(String[] args) throws Exception {
   10             // 1. Define the cluster configuration
   11             List<String> peerIds = Arrays.asList("node1", "node2", "node3");
   12 
   13             // 2. Instantiate the nodes and their storage
   14             RaftNode node1 = new Follower("node1", peerIds, new RaftStorage("node1"), null);
   15             RaftNode node2 = new Follower("node2", peerIds, new RaftStorage("node2"), null);
   16             RaftNode node3 = new Follower("node3", peerIds, new RaftStorage("node3"), null);
   17 
   18             // 3. Instantiate the communication layer
   19             InMemoryRaftProtocol protocol = new InMemoryRaftProtocol();
   20 
   21             // 4. Register all nodes with the communication layer
   22             protocol.registerNode(node1);
   23             protocol.registerNode(node2);
   24             protocol.registerNode(node3);
   25 
   26             // 5. Start the nodes' internal schedulers
   27             node1.start();
   28             node2.start();
   29             node3.start();
   30 
   31             // 6. Wait for an election to take place
   32             System.out.println("Waiting for leader election...");
   33             Thread.sleep(1000);
   34 
   35             // 7. Create the client API, pointing to an arbitrary node
   36             RaftApi api = new RaftApi(node1, protocol);
   37 
   38             // 8. Propose a command asynchronously
   39             System.out.println("Proposing command: 'Hello Raft!'");
   40             CompletableFuture<String> future = api.propose("Hello Raft!");
   41 
   42             // 9. Block and wait for the result
   43             System.out.println("Result: " + future.get());
   44 
   45             // 10. Cleanly shut down the nodes
   46             node1.stop();
   47             node2.stop();
   48             node3.stop();
   49         }
   50     }

   * `main(...)` method Walkthrough:
       1. Configuration: A simple List of strings defines the IDs of all nodes in our cluster.
       2. Instantiation: Three Follower objects are created. Each gets its own ID, the full list of peers,
          and its own RaftStorage instance. The protocol is initially null because it will be set by the
          protocol object itself.
       3. Network Setup: The InMemoryRaftProtocol is created.
       4. Registration: Each node is registered with the protocol. This is where the protocol gets a
          reference to each node object and, in turn, gives each node a reference to itself (via
          setRaftProtocol).
       5. Start Nodes: node.start() is called for each node. This is the crucial step that starts the tick()
          schedulers, which will inevitably lead to an election timeout and the start of the Raft algorithm.
       6. Wait: Thread.sleep(1000) pauses the main thread for 1 second. This is a simple, brute-force way to
          give the nodes enough time to elect a leader before the client sends a command.
       7. Create API: An instance of RaftApi is created. It's given node1 as its initial contact point and a
          reference to the protocol for leader lookups.
       8. Propose: api.propose() is called. This call is non-blocking; it returns a CompletableFuture
          immediately while the Raft logic happens in the background.
       9. Wait for Result: future.get() is a blocking call. The main thread will pause at this line and wait
          until the Leader completes the future, which only happens after the "Hello Raft!" command has been
          successfully committed by the cluster.
       10. Shutdown: The stop() method is called on each node to terminate the background scheduler threads,
           allowing the Java application to exit cleanly.

