package paxossim.core;

import java.util.HashMap;
import java.util.Map;

/**
 * The deterministic key-value store every node's replicated {@link Log} is
 * applied to. Commands are applied strictly in slot order: even if a later
 * slot is chosen before an earlier one, {@link #applyChosenEntries} stops at
 * the first slot not yet chosen rather than skipping ahead, so replaying a
 * log — however out of order its slots were learned — always produces the
 * same sequence of applied commands, and every node ends up in the same
 * state.
 */
public final class StateMachine {

    private final Map<String, String> store = new HashMap<>();
    private int nextSlotToApply = 0;

    /**
     * Applies every contiguous chosen entry starting at the next
     * not-yet-applied slot, stopping at the first slot in {@code log} that
     * isn't chosen yet. Safe to call repeatedly as the log fills in.
     */
    public void applyChosenEntries(Log log) {
        while (log.isChosen(nextSlotToApply)) {
            Command command = log.chosenValue(nextSlotToApply).orElseThrow();
            apply(command);
            nextSlotToApply++;
        }
    }

    private void apply(Command command) {
        switch (command.type()) {
            case SET -> store.put(command.key(), command.value());
            case DELETE -> store.remove(command.key());
        }
    }

    /** The current value for {@code key}, or {@code null} if unset. */
    public String get(String key) {
        return store.get(key);
    }

    /** How many slots (0..n-1, contiguously) have been applied so far. */
    public int appliedSlotCount() {
        return nextSlotToApply;
    }

    /** A read-only snapshot of the current key-value state. */
    public Map<String, String> snapshot() {
        return Map.copyOf(store);
    }
}
