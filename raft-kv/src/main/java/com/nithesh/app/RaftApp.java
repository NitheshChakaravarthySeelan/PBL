package com.nithesh.app;

import com.nithesh.raft.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RaftApp {

    public static void main(String[] args) throws Exception {
        List<String> peerIds = Arrays.asList("node1", "node2", "node3");

        RaftNode node1 = new Follower("node1", peerIds, new RaftStorage("node1"), null);
        RaftNode node2 = new Follower("node2", peerIds, new RaftStorage("node2"), null);
        RaftNode node3 = new Follower("node3", peerIds, new RaftStorage("node3"), null);

        InMemoryRaftProtocol protocol = new InMemoryRaftProtocol();
        protocol.registerNode(node1);
        protocol.registerNode(node2);
        protocol.registerNode(node3);

        node1.start();
        node2.start();
        node3.start();

        // Wait for a leader to be elected
        Thread.sleep(1000);

        RaftApi api = new RaftApi(node1, protocol);
        CompletableFuture<String> future = api.propose("Hello Raft!");

        System.out.println("Proposed command: Hello Raft!");
        System.out.println("Result: " + future.get());

        node1.stop();
        node2.stop();
        node3.stop();
    }
}
