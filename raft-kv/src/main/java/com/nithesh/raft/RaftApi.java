package com.nithesh.raft;

import java.util.concurrent.CompletableFuture;

public class RaftApi {

    private RaftNode raftNode;
    private final InMemoryRaftProtocol protocol;

    public RaftApi(RaftNode raftNode, InMemoryRaftProtocol protocol) {
        this.raftNode = raftNode;
        this.protocol = protocol;
    }

    public CompletableFuture<String> propose(String command) {
        CompletableFuture<String> future = raftNode.propose(command);
        return future.exceptionally(ex -> {
            if (ex.getCause() instanceof NotLeaderException) {
                NotLeaderException notLeaderException = (NotLeaderException) ex.getCause();
                String leaderId = notLeaderException.getLeaderId();
                if (leaderId != null) {
                    RaftNode leaderNode = protocol.getNode(leaderId);
                    if (leaderNode != null) {
                        this.raftNode = leaderNode;
                        return propose(command);
                    }
                }
            }
            return CompletableFuture.failedFuture(ex);
        });
    }
}
