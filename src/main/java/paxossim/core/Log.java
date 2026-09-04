package paxossim.core;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A node's replicated log: one independent Paxos instance per slot (its own
 * Phase 1/2, its own {@link AcceptorState} on every acceptor — no
 * leader-reuse optimization), each remembered here as its own
 * {@link LogEntry} once a quorum has chosen a value for that slot.
 */
public final class Log {

    private final Map<Integer, LogEntry> entriesBySlot = new TreeMap<>();

    /** Records that {@code command} was chosen for {@code slot} under {@code ballot}. */
    public void recordChosen(int slot, Ballot ballot, Command command) {
        LogEntry entry = new LogEntry(slot, ballot, command);
        entry.markChosen();
        entriesBySlot.put(slot, entry);
    }

    /** The entry recorded for {@code slot}, if any. */
    public Optional<LogEntry> get(int slot) {
        return Optional.ofNullable(entriesBySlot.get(slot));
    }

    /** Whether a value has been chosen for {@code slot}. */
    public boolean isChosen(int slot) {
        return get(slot).map(LogEntry::isChosen).orElse(false);
    }

    /** The command chosen for {@code slot}, if any. */
    public Optional<Command> chosenValue(int slot) {
        return get(slot).filter(LogEntry::isChosen).map(LogEntry::command);
    }

    /** Every recorded entry, in ascending slot order. */
    public Collection<LogEntry> entries() {
        return entriesBySlot.values();
    }

    /** How many slots have a recorded entry. */
    public int size() {
        return entriesBySlot.size();
    }
}
