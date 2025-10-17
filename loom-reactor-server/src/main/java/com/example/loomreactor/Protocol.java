package com.example.loomreactor;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public final class Protocol {
	
	// Serialize Java data -> wire format (txt string)
	public static String formatBroadcast(String from, String message) {
		long ts = Instant.now().toEpochMilli();
		String safeFrom = escape(from);
		String safeMessage = escape(message);

		return safeFrom + "|" + ts + "|" + safeMessage + "\n";
	}

	/** 
	 * parseLine is a safe, defensive parsing
	 * Validate input has exactly the expected parts.
	 * Properly unescape seq (\\, \|, \n, \r).
	 * Validate timestamp is numeric and in reasonable bounds.
	 * Return a structure or typed object from the transferred data.
	 */
	public static Map<String, String> parseLine(String line) {
		if (line == null) {
			throw new IllegalArgumentException("line is null");
		}

		if (line.endsWith("\n")) line = line.substring(0, line.length() - 1);
		if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);

		// Split but need to consider escaped pipes
		List<String> parts = splitRespectingEscape(line, '|');
		if (parts.size() < 3) {
			return Map.of("raw", line, "error", "malformed");
		}

		String fromEsc = parts.get(0);
		String tsStr = parts.get(1);

		// Join remainder as message in case message contains unescaped pipes
		String messageEsc = String.join("|", parts.subList(2, parts.size()));
		String from = unescape(fromEsc);
		String message = unescape(messageEsc);

		return Map.of("from", from, "timestamp", tsStr, "message", message);
	}
	
	/* 
	 * We escape \ first to avoid double escaping issue
	 * Use \n and \r sequence not actual control char inside fields that keeps the message single line.
	 */
	public static String escape(String s) {
		if (s == null) {
			return "";
		}
		
		// escape backslash first
		s = s.replace("\\", "\\\\");

		// escape delimiter
		s = s.replace("|", "\\|");

		// convert literal newline to visible sequence
		s = s.replace("\n", "\\n");
		s = s.replace("\r", "\\r");
		return s;
	}

	private static String unescape(String s) {
    		StringBuilder out = new StringBuilder();
    		boolean esc = false;
    		for (int i = 0; i < s.length(); i++) {
        		char c = s.charAt(i);
        		if (esc) {
            		switch (c) {
                		case '\\' -> out.append('\\');
                		case '|'  -> out.append('|');
                		case 'n'  -> out.append('\n');
                		case 'r'  -> out.append('\r');
                		default   -> out.append(c); // unknown escape -> keep literal
            		}
            		esc = false;
        		} else {
            			if (c == '\\') {
                			esc = true;
            			} else {
                			out.append(c);
            			}
        		}
    		}
    		if (esc) out.append('\\'); // trailing escape => literal backslash
    		return out.toString();
	}

	private static List<String> splitRespectingEscape(String s, char sep) {
		List<String> res = new ArrayList<>();
		StringBuilder cur = new StringBuilder();

		boolean esc = false;
		for (int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			if (esc) {
				cur.append(c);
				esc = false;
			} else {
				if (c == '\\') {
					esc = true;
				} else if (c == sep) {
					res.add(cur.toString());
					cur.setLength(0);
				} else {
					cur.append(c);
				}
			}
		}
		if (esc) {
			cur.append('\\');
		}
		res.add(cur.toString());
		return res;
	}
}
