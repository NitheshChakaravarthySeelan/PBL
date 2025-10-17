package com.example.loomreactor;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class ProtocolTest {

    @Test
    public void testFormatBroadcast() {
        String from = "user1";
        String message = "hello world";
        String formatted = Protocol.formatBroadcast(from, message);
        assertTrue(formatted.contains(from));
        assertTrue(formatted.contains(message));
    }

    @Test
    public void testParseLine() {
        long ts = System.currentTimeMillis();
        String from = "user1";
        String message = "hello world";
        String line = from + "|" + ts + "|" + message;
        Map<String, String> parsed = Protocol.parseLine(line);
        assertEquals(from, parsed.get("from"));
        assertEquals(String.valueOf(ts), parsed.get("timestamp"));
        assertEquals(message, parsed.get("message"));
    }

    @Test
    public void testEscape() {
        String message = "hello|world\n";
        String escaped = Protocol.escape(message);
        assertEquals("hello\\|world\\n", escaped);
    }
}
