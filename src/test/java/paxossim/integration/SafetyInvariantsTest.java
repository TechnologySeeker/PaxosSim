package paxossim.integration;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.core.Log;
import paxossim.core.StateMachine;
import paxossim.network.SimulatedNetwork;
import paxossim.node.Node;
import paxossim.role.Learner;
import paxossim.role.Proposer;
import paxossim.testing.PaxosInvariants;

import java.util.List;
import java.util.Set;

/**
 * Checks the two core Paxos safety invariants — {@link PaxosInvariants} —
 * against every kind of scenario this project can create: a clean
 * multi-slot run, proposers genuinely racing at the message level, and a
 * partition followed by recovery. None of these scenarios are new; what's
 * new is verifying, explicitly and after the fact, that no matter how a
 * round got resolved, every node's independent log and state machine agree.
 */
public class SafetyInvariantsTest {

    private static final List<String> ACCEPTOR_IDS = List.of("A", "B", "C");
    private static final List<String> LEARNER_IDS = List.of("A-learner", "B-learner", "C-learner");

    public void testInvariantsHoldAfterNormalMultiSlotReplication() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network, LEARNER_IDS);
        new Node("B", network, LEARNER_IDS);
        new Node("C", network, LEARNER_IDS);
        Log logA = new Log();
        Log logB = new Log();
        Log logC = new Log();
        new Learner("A-learner", network, ACCEPTOR_IDS, logA);
        new Learner("B-learner", network, ACCEPTOR_IDS, logB);
        new Learner("C-learner", network, ACCEPTOR_IDS, logC);
        Proposer proposer = new Proposer("P", network, ACCEPTOR_IDS);

        runSlot(proposer, network, new Ballot(1, "P"), 0, Command.set("x", "10"));
        runSlot(proposer, network, new Ballot(1, "P"), 1, Command.set("y", "20"));
        runSlot(proposer, network, new Ballot(1, "P"), 2, Command.delete("x"));

        PaxosInvariants.assertOnlyOneValueChosenPerSlot(logA, logB, logC);

        StateMachine stateMachineA = new StateMachine();
        StateMachine stateMachineB = new StateMachine();
        StateMachine stateMachineC = new StateMachine();
        stateMachineA.applyChosenEntries(logA);
        stateMachineB.applyChosenEntries(logB);
        stateMachineC.applyChosenEntries(logC);

        PaxosInvariants.assertAppliedStateMatchesAcrossNodes(stateMachineA, stateMachineB, stateMachineC);
    }

    public void testInvariantsHoldDespiteProposersRacingAtTheMessageLevel() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network, LEARNER_IDS);
        new Node("B", network, LEARNER_IDS);
        new Node("C", network, LEARNER_IDS);
        Log logA = new Log();
        Log logB = new Log();
        Log logC = new Log();
        new Learner("A-learner", network, ACCEPTOR_IDS, logA);
        new Learner("B-learner", network, ACCEPTOR_IDS, logB);
        new Learner("C-learner", network, ACCEPTOR_IDS, logC);
        Proposer proposer1 = new Proposer("P1", network, ACCEPTOR_IDS);
        Proposer proposer2 = new Proposer("P2", network, ACCEPTOR_IDS);

        // A genuine race: both Prepare, then both Accept, interleaved
        // through one shared FIFO queue rather than staged in sequence.
        proposer1.sendPrepare(new Ballot(1, "P1"), 0);
        proposer2.sendPrepare(new Ballot(2, "P2"), 0);
        network.deliverAll();
        Command chosenForP1 = proposer1.chooseValue(Command.set("x", "V1"));
        Command chosenForP2 = proposer2.chooseValue(Command.set("x", "V2"));
        proposer1.sendAccept(new Ballot(1, "P1"), 0, chosenForP1);
        proposer2.sendAccept(new Ballot(2, "P2"), 0, chosenForP2);
        network.deliverAll();

        // Regardless of who "won", every node's independently-learned log
        // must agree on exactly one chosen value for the slot.
        PaxosInvariants.assertOnlyOneValueChosenPerSlot(logA, logB, logC);
    }

    public void testInvariantsHoldAfterAPartitionAndRecovery() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network, LEARNER_IDS);
        new Node("B", network, LEARNER_IDS);
        new Node("C", network, LEARNER_IDS);
        Log logA = new Log();
        Log logB = new Log();
        Log logC = new Log();
        new Learner("A-learner", network, ACCEPTOR_IDS, logA);
        new Learner("B-learner", network, ACCEPTOR_IDS, logB);
        new Learner("C-learner", network, ACCEPTOR_IDS, logC);
        Proposer proposer = new Proposer("P", network, ACCEPTOR_IDS);

        // The proposer is stuck on the minority side of a partition and can't
        // make progress at all.
        network.partition(Set.of("B", "C"));
        proposer.sendPrepare(new Ballot(1, "P"), 0);
        network.deliverAll();

        // Once the partition heals, a fresh, higher-ballot retry succeeds
        // (the stalled attempt already used ballot 1 at A, so the retry
        // needs a strictly higher round to be accepted there too).
        network.healPartition();
        runSlot(proposer, network, new Ballot(2, "P"), 0, Command.set("x", "10"));

        PaxosInvariants.assertOnlyOneValueChosenPerSlot(logA, logB, logC);

        StateMachine stateMachineA = new StateMachine();
        StateMachine stateMachineB = new StateMachine();
        StateMachine stateMachineC = new StateMachine();
        stateMachineA.applyChosenEntries(logA);
        stateMachineB.applyChosenEntries(logB);
        stateMachineC.applyChosenEntries(logC);

        PaxosInvariants.assertAppliedStateMatchesAcrossNodes(stateMachineA, stateMachineB, stateMachineC);
    }

    private void runSlot(Proposer proposer, SimulatedNetwork network, Ballot ballot, int slot, Command value) {
        proposer.sendPrepare(ballot, slot);
        network.deliverAll();
        Command chosen = proposer.chooseValue(value);
        proposer.sendAccept(ballot, slot, chosen);
        network.deliverAll();
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(SafetyInvariantsTest.class);
    }
}
