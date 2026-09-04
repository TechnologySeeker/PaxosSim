package paxossim.testing;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.core.Log;
import paxossim.core.StateMachine;

import static paxossim.testing.Assertions.assertTrue;

public class PaxosInvariantsTest {

    public void testAssertOnlyOneValueChosenPerSlotPassesWhenLogsAgree() {
        Log logA = new Log();
        Log logB = new Log();
        Command value = Command.set("x", "10");
        logA.recordChosen(0, new Ballot(1, "P"), value);
        logB.recordChosen(0, new Ballot(1, "P"), value);

        PaxosInvariants.assertOnlyOneValueChosenPerSlot(logA, logB); // must not throw
    }

    public void testAssertOnlyOneValueChosenPerSlotCatchesARealConflictAcrossLogs() {
        Log logA = new Log();
        Log logB = new Log();
        // Simulate a hypothetical bug: two independent nodes' logs disagree
        // about what was chosen for the same slot.
        logA.recordChosen(0, new Ballot(1, "P1"), Command.set("x", "V1"));
        logB.recordChosen(0, new Ballot(2, "P2"), Command.set("x", "V2"));
        boolean threw = false;

        try {
            PaxosInvariants.assertOnlyOneValueChosenPerSlot(logA, logB);
        } catch (AssertionError expected) {
            threw = true;
        }

        assertTrue(threw, "the invariant check must catch two logs disagreeing about the same slot");
    }

    public void testAssertOnlyOneValueChosenPerSlotIgnoresSlotsNotYetChosenInSomeLogs() {
        Log logA = new Log();
        Log logB = new Log();
        logA.recordChosen(0, new Ballot(1, "P"), Command.set("x", "10"));
        // logB hasn't learned slot 0 yet at all — that's fine, not a conflict.

        PaxosInvariants.assertOnlyOneValueChosenPerSlot(logA, logB); // must not throw
    }

    public void testAssertAppliedStateMatchesAcrossNodesPassesWhenIdentical() {
        Log log = new Log();
        log.recordChosen(0, new Ballot(1, "P"), Command.set("x", "10"));
        StateMachine stateMachineA = new StateMachine();
        StateMachine stateMachineB = new StateMachine();
        stateMachineA.applyChosenEntries(log);
        stateMachineB.applyChosenEntries(log);

        PaxosInvariants.assertAppliedStateMatchesAcrossNodes(stateMachineA, stateMachineB); // must not throw
    }

    public void testAssertAppliedStateMatchesAcrossNodesCatchesARealDivergence() {
        Log logA = new Log();
        Log logB = new Log();
        // Simulate a hypothetical bug: the two nodes applied different
        // commands for the same slot.
        logA.recordChosen(0, new Ballot(1, "P1"), Command.set("x", "V1"));
        logB.recordChosen(0, new Ballot(2, "P2"), Command.set("x", "V2"));
        StateMachine stateMachineA = new StateMachine();
        StateMachine stateMachineB = new StateMachine();
        stateMachineA.applyChosenEntries(logA);
        stateMachineB.applyChosenEntries(logB);
        boolean threw = false;

        try {
            PaxosInvariants.assertAppliedStateMatchesAcrossNodes(stateMachineA, stateMachineB);
        } catch (AssertionError expected) {
            threw = true;
        }

        assertTrue(threw, "the invariant check must catch two state machines that applied different values");
    }

    public static void main(String[] args) {
        TestRunner.run(PaxosInvariantsTest.class);
    }
}
