package com.nithesh.raft;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Common state over the raft
/**
 * Persistent state - term, votedFor, log
 * Volatile state - commitIndex, lastApplied
 * Cluster configuration - peers, self
 * RPC endpoints - AppendEntries, RequestVote
 * Transition logic between roles
 */
// Will be implemented -> Implemented by another class
public abstract class RaftNode {
	protected int currentTerm;
	protected String votedFor;
	protected List<LogEntry> log;		// idx + time + task

	protected int commitIndex;
	protected int lastApplied;

	protected final String nodeId;
	protected final List<String> peers;

	protected Role currentRole;
	protected RaftStorage storage;
	protected RaftProtocol protocol;
	protected ScheduledExecutorService scheduler;

	public RaftNode(String nodeId, List<String> peers, RaftStorage storage, RaftProtocol protocol) {
    		this.nodeId = nodeId;
    		this.peers = peers;
    		this.storage = storage;
    		this.protocol = protocol;
    		this.scheduler = Executors.newSingleThreadScheduledExecutor();

    		this.currentTerm = storage.loadCurrentTerm();
    		this.votedFor = storage.loadVotedFor();
    		this.log = storage.loadLog();

    		this.commitIndex = 0;
    		this.lastApplied = 0;
    		this.currentRole = Role.FOLLOWER;
	}
	
	public void setRaftProtocol(RaftProtocol protocol) {
		this.protocol = protocol;
	}

	public synchronized void start() {
		scheduler.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
	}

	public synchronized void stop() {
		scheduler.shutdownNow();
	}

	protected synchronized void becomeFollower(int newTerm) {
		this.currentRole = Role.FOLLOWER;
		this.currentTerm = newTerm;
		this.votedFor = null;
		onBecomeFollower();
	}
	
	/**
	 * Change the current node to Candidate
	 */
	protected synchronized void becomeCandidate() {
		this.currentRole = Role.CANDIDATE;
		this.currentTerm++;
		this.votedFor = nodeId;
		onBecomeCandidate();
	}
	
	/**
	 * Change the current node to leader
	 */
	protected synchronized void becomeLeader() {
		this.currentRole = Role.LEADER;
		onBecomeLeader();
	}

	public abstract AppendEntriesResponse handleAppendEntries(AppendEntriesRequest req);

	public abstract RequestVoteResponse handleRequestVote(RequestVoteRequest req);

	protected abstract void onBecomeLeader();
	protected abstract void onBecomeFollower();
	protected abstract void onBecomeCandidate();
	
	// Must be a tick or heartbeat signal.
	protected abstract void tick();

	public CompletableFuture<String> propose(String command) {
		// This method should be implemented in the Leader class
		// For now, we will just return a completed future
		return CompletableFuture.completedFuture("Command processed");
	}

	protected void applyStateMachine(LogEntry logEntry) {
		// Simple state machine that prints the command
		System.out.println("Applying to state machine: " + logEntry.getCommand());
	}
}
