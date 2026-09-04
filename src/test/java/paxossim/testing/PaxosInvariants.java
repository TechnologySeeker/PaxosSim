package paxossim.testing;

import paxossim.core.Command;
import paxossim.core.Log;
import paxossim.core.LogEntry;
import paxossim.core.StateMachine;

import java.util.HashMap;
import java.util.Map;

/**
 * Reusable checks for the two core Paxos safety invariants this project
 * must never violate, whatever combination of failures a scenario injected:
 * exactly one value chosen per slot, and every node's applied state
 * agreeing with every other node's. {@link Log#recordChosen} already
 * refuses a conflicting value for its own slot, but that only protects one
 * log; the real distributed-safety question is whether several independent
 * nodes' logs (and the state machines built from them) ever disagree with
 * each other, which is what these checks are for.
 */
public final class PaxosInvariants {

    private PaxosInvariants() {}

    /**
     * Asserts that across every given log — each representing a different
     * node's independent view — no slot is ever chosen with two different
     * values.
     *
     * @throws AssertionError if any slot is chosen with different commands in different logs
     */
    public static void assertOnlyOneValueChosenPerSlot(Log... logs) {
        Map<Integer, Command> chosenBySlot = new HashMap<>();
        for (Log log : logs) {
            for (LogEntry entry : log.entries()) {
                if (!entry.isChosen()) {
                    continue;
                }
                Command previouslySeen = chosenBySlot.putIfAbsent(entry.slot(), entry.command());
                if (previouslySeen != null && !previouslySeen.equals(entry.command())) {
                    throw new AssertionError("Paxos safety violated: slot " + entry.slot() + " has both "
                            + previouslySeen + " and " + entry.command() + " chosen across different logs");
                }
            }
        }
    }

    /**
     * Asserts that every given state machine has applied to the identical
     * key-value state — i.e. that replaying each node's own log independently
     * produced the same result everywhere.
     *
     * @throws AssertionError if any two state machines' applied state differs
     */
    public static void assertAppliedStateMatchesAcrossNodes(StateMachine... stateMachines) {
        if (stateMachines.length == 0) {
            return;
        }
        Map<String, String> reference = stateMachines[0].snapshot();
        for (int i = 1; i < stateMachines.length; i++) {
            Map<String, String> other = stateMachines[i].snapshot();
            if (!reference.equals(other)) {
                throw new AssertionError("Applied state diverged across nodes: node 0 has " + reference
                        + " but node " + i + " has " + other);
            }
        }
    }
}
