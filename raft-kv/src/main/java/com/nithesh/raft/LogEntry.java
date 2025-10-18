package com.nithesh.raft;

/**
 * index:int Position in the log
 * term:int Term number when entry was received by leader
 * command:String The actual operation
 */
public class LogEntry {
	private static int index;
	private static int term;
	private static String command;

	public LogEntry(int index, int term, String command) {
		this.index = index;
		this.term = term;
		this.command = command;
	}

	public int getIndex() {
		return this.index;
	}

	public int getTerm() {
		return this.term;
	}

	public String getCommand() {
		return this.command;
	}

	public String toString() {
		String idxString = Integer.toString(this.index);
		String termString = Integer.toString(this.term);
		String result = "["+idxString+", "+termString+", "+this.command+"]";
		return result;
	}
}
