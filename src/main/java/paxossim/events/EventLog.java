package paxossim.events;

import java.util.ArrayList;
import java.util.List;

/**
 * An append-only, ordered record of structured {@link Event}s emitted while
 * a Paxos scenario runs — every message {@link paxossim.network.SimulatedNetwork}
 * sends, receives, or drops, and the semantic moments those messages cause
 * (a promise granted or refused, a value accepted, a slot chosen, a state
 * machine applying a command). Exists purely for visualization/replay by
 * the static UI in {@code viz/}; nothing in the Paxos logic ever reads it
 * back.
 */
public final class EventLog {

    private final List<Event> events = new ArrayList<>();

    public void send(String from, String to, String messageType, String ballot, int slot) {
        add("SEND", from, to, messageType, ballot, slot, null, null);
    }

    public void receive(String from, String to, String messageType, String ballot, int slot) {
        add("RECEIVE", from, to, messageType, ballot, slot, null, null);
    }

    public void dropped(String from, String to, String messageType, String ballot, int slot) {
        add("DROPPED", from, to, messageType, ballot, slot, null, null);
    }

    public void promise(String node, String ballot, int slot, String note) {
        add("PROMISE", null, node, null, ballot, slot, null, note);
    }

    public void nack(String node, String ballot, int slot, String note) {
        add("NACK", null, node, null, ballot, slot, null, note);
    }

    public void accepted(String node, String ballot, int slot, String value, String note) {
        add("ACCEPTED", null, node, null, ballot, slot, value, note);
    }

    public void chosen(int slot, String value, String note) {
        add("CHOSEN", null, null, null, null, slot, value, note);
    }

    public void stateChange(String node, int slot, String value, String note) {
        add("STATE_CHANGE", null, node, null, null, slot, value, note);
    }

    /** A free-text narration not tied to any single message (e.g. "quorum reached"). */
    public void note(String text) {
        add("NOTE", null, null, null, null, null, null, text);
    }

    private void add(String type, String from, String to, String message, String ballot,
                      Integer slot, String value, String note) {
        events.add(new Event(events.size(), type, from, to, message, ballot, slot, value, note));
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    /**
     * Serializes {@code nodeIds} (the cluster topology the UI should render
     * circles for) and every recorded event, in the order they happened, as
     * one JSON object: {@code {"nodes": [...], "events": [...]}}.
     */
    public String toJson(List<String> nodeIds) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"nodes\": [");
        for (int i = 0; i < nodeIds.size(); i++) {
            json.append('"').append(escape(nodeIds.get(i))).append('"');
            if (i < nodeIds.size() - 1) {
                json.append(", ");
            }
        }
        json.append("],\n  \"events\": [\n");
        for (int i = 0; i < events.size(); i++) {
            json.append("    ");
            appendEvent(json, events.get(i));
            json.append(i < events.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n}\n");
        return json.toString();
    }

    private static void appendEvent(StringBuilder json, Event event) {
        json.append('{');
        int startLength = json.length();
        appendField(json, "seq", event.seq());
        appendField(json, "type", event.type());
        appendField(json, "fromNode", event.fromNode());
        appendField(json, "toNode", event.toNode());
        appendField(json, "message", event.message());
        appendField(json, "ballot", event.ballot());
        appendField(json, "slot", event.slot());
        appendField(json, "value", event.value());
        appendField(json, "note", event.note());
        if (json.length() > startLength) {
            json.setLength(json.length() - 1); // trim the trailing comma
        }
        json.append('}');
    }

    private static void appendField(StringBuilder json, String key, int value) {
        json.append('"').append(key).append("\":").append(value).append(',');
    }

    private static void appendField(StringBuilder json, String key, Integer value) {
        if (value != null) {
            json.append('"').append(key).append("\":").append(value).append(',');
        }
    }

    private static void appendField(StringBuilder json, String key, String value) {
        if (value != null) {
            json.append('"').append(key).append("\":\"").append(escape(value)).append("\",");
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
