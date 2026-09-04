package paxossim.events;

import java.util.List;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class EventLogTest {

    public void testSendRecordsASendEventWithGivenFields() {
        EventLog log = new EventLog();

        log.send("A", "B", "Prepare", "(1,A)", 0);

        Event event = log.events().get(0);
        assertEquals("SEND", event.type(), "type should be SEND");
        assertEquals("A", event.fromNode(), "fromNode should be recorded");
        assertEquals("B", event.toNode(), "toNode should be recorded");
        assertEquals("Prepare", event.message(), "message type should be recorded");
        assertEquals("(1,A)", event.ballot(), "ballot should be recorded");
        assertEquals(Integer.valueOf(0), event.slot(), "slot should be recorded");
    }

    public void testEventsAreAssignedIncreasingSequenceNumbers() {
        EventLog log = new EventLog();

        log.send("A", "B", "Prepare", "(1,A)", 0);
        log.receive("A", "B", "Prepare", "(1,A)", 0);
        log.chosen(0, "SET x=10", "SLOT 0 CHOSEN");

        List<Event> events = log.events();
        assertEquals(0, events.get(0).seq(), "first event should have seq 0");
        assertEquals(1, events.get(1).seq(), "second event should have seq 1");
        assertEquals(2, events.get(2).seq(), "third event should have seq 2");
    }

    public void testChosenRecordsASlotLevelEventWithNoNodes() {
        EventLog log = new EventLog();

        log.chosen(3, "SET x=10", "SLOT 3 CHOSEN: SET x=10");

        Event event = log.events().get(0);
        assertEquals("CHOSEN", event.type(), "type should be CHOSEN");
        assertTrue(event.fromNode() == null, "a CHOSEN event has no fromNode");
        assertTrue(event.toNode() == null, "a CHOSEN event has no toNode");
        assertEquals(Integer.valueOf(3), event.slot(), "slot should be recorded");
        assertEquals("SET x=10", event.value(), "the chosen value should be recorded");
        assertEquals("SLOT 3 CHOSEN: SET x=10", event.note(), "the note should be recorded");
    }

    public void testPromiseAcceptedNackRecordTheNodeInToNode() {
        EventLog log = new EventLog();

        log.promise("A", "(1,A)", 0, "promised");
        log.accepted("A", "(1,A)", 0, "SET x=10", "accepted");
        log.nack("A", "(1,A)", 0, "rejected");

        for (Event event : log.events()) {
            assertEquals("A", event.toNode(), "the acceptor's id should be carried in toNode");
        }
    }

    public void testToJsonIncludesNodesAndEvents() {
        EventLog log = new EventLog();
        log.send("A", "B", "Prepare", "(1,A)", 0);
        log.chosen(0, "SET x=10", "SLOT 0 CHOSEN: SET x=10");

        String json = log.toJson(List.of("A", "B", "C"));

        assertTrue(json.contains("\"nodes\": [\"A\", \"B\", \"C\"]"), "json should list the node topology");
        assertTrue(json.contains("\"type\":\"SEND\""), "json should include the SEND event");
        assertTrue(json.contains("\"type\":\"CHOSEN\""), "json should include the CHOSEN event");
        assertTrue(json.contains("\"note\":\"SLOT 0 CHOSEN: SET x=10\""), "json should include the note text");
    }

    public void testToJsonEscapesQuotesAndBackslashesInStrings() {
        EventLog log = new EventLog();
        log.note("a \"quoted\" value with a \\backslash");

        String json = log.toJson(List.of());

        assertTrue(json.contains("a \\\"quoted\\\" value with a \\\\backslash"), "quotes and backslashes should be escaped");
    }

    public void testToJsonOmitsNullFields() {
        EventLog log = new EventLog();
        log.chosen(0, "SET x=10", "note");

        String json = log.toJson(List.of());

        assertTrue(!json.contains("fromNode"), "a null field should be omitted rather than emitted as null");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(EventLogTest.class);
    }
}
