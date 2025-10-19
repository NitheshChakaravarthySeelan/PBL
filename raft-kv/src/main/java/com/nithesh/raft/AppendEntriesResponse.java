package com.nithesh.raft;

public class AppendEntriesResponse {
    private final int term;
    private final boolean success;

    public AppendEntriesResponse(int term, boolean success) {
        this.term = term;
        this.success = success;
    }

    public int getTerm() {
        return term;
    }

    public boolean isSuccess() {
        return success;
    }
}