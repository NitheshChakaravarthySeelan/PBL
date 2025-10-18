package com.nithesh.raft;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
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
public class RaftNode {
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
	
	public synchronized void start()l {
		scheduler.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
	}

	public synchronized void stop() {
		scheduler.shutdownNow();
	}

	protected synchronized void becomeFollower(int newTerm) {
		// Will be implemented
	}
	
	/**
	 * Change the current node to Candidate
	 */
	protected synchronized void becomeCandidate() {
		this.currentRole = Role.CANDIDATE;
		this.currentTerm++;
	}
	
	/**
	 * Change the current node to leader
	 */
	protected synchronized void becomeLeader() {
		this.currentRole = Role.LEADER;
		onBecomeLeader()
	}

	public synchronized AppendEntriesResponse handleAppendEntries(AppendEntitiesRequest req) {
		// Validate term, update follower state, append entries
		// Will be implemented
	}

	public synchronized RequestVoteResponse handleRequestVote(RequestVoteRequest req) {
		// Validate term and grant/reject vote
		// Will be implemented
	}

	protected abstract void onBecomeLeader();
	protected abstract void onBecomeFollower();
	protected abstract void onBecomeCandidate();
	
	// Must be a tick or heartbeat signal.
	protected abstract void tick();
