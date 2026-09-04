package paxossim.integration;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.core.Log;
import paxossim.network.SimulatedNetwork;
import paxossim.node.Node;
import paxossim.role.Proposer;

import java.util.List;
import java.util.Optional;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

/**
 * Multi-decree Paxos: every log slot runs its own independent Paxos
 * instance — its own {@link Proposer}, its own Phase 1/2 — against the same
 * 3-node acceptor cluster. This already works because every acceptor keeps
 * a separate {@link paxossim.core.AcceptorState} per slot (since the very
 * first Acceptor commit); these tests prove that end to end and record the
 * results in a {@link Log}. There's no leader-reuse optimization, so each
 * slot pays for its own Prepare round.
 */
public class MultiSlotPaxosTest {

    private static final List<String> ACCEPTORS = List.of("A", "B", "C");

    public void testTwoSlotsChooseIndependentValuesConcurrently() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposerForSlot0 = new Proposer("P-slot0", network, ACCEPTORS);
        Proposer proposerForSlot1 = new Proposer("P-slot1", network, ACCEPTORS);
        Command valueForSlot0 = Command.set("x", "10");
        Command valueForSlot1 = Command.set("y", "20");

        // Interleave the two slots' Prepare rounds before either moves to Accept.
        proposerForSlot0.sendPrepare(new Ballot(1, "P-slot0"), 0);
        proposerForSlot1.sendPrepare(new Ballot(1, "P-slot1"), 1);
        network.deliverAll();

        assertTrue(proposerForSlot0.hasQuorum(), "slot 0's prepare phase should reach quorum");
        assertTrue(proposerForSlot1.hasQuorum(), "slot 1's prepare phase should reach quorum, independent of slot 0");

        Command chosenForSlot0 = proposerForSlot0.chooseValue(valueForSlot0);
        Command chosenForSlot1 = proposerForSlot1.chooseValue(valueForSlot1);
        proposerForSlot0.sendAccept(new Ballot(1, "P-slot0"), 0, chosenForSlot0);
        proposerForSlot1.sendAccept(new Ballot(1, "P-slot1"), 1, chosenForSlot1);
        network.deliverAll();

        assertTrue(proposerForSlot0.hasAcceptedQuorum(), "slot 0's accept phase should reach quorum");
        assertTrue(proposerForSlot1.hasAcceptedQuorum(), "slot 1's accept phase should reach quorum");
        assertEquals(Optional.of(valueForSlot0), proposerForSlot0.chosenValue(), "slot 0 should choose its own value");
        assertEquals(Optional.of(valueForSlot1), proposerForSlot1.chosenValue(),
                "slot 1 should choose its own value, unaffected by slot 0");

        Log log = new Log();
        log.recordChosen(0, new Ballot(1, "P-slot0"), chosenForSlot0);
        log.recordChosen(1, new Ballot(1, "P-slot1"), chosenForSlot1);

        assertEquals(Optional.of(valueForSlot0), log.chosenValue(0), "the log should remember slot 0's chosen value");
        assertEquals(Optional.of(valueForSlot1), log.chosenValue(1), "the log should remember slot 1's chosen value");
        assertEquals(2, log.size(), "the log should have one entry per chosen slot");
    }

    public void testAHighBallotOnOneSlotDoesNotAffectAFreshBallotOnAnotherSlot() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposerForSlot5 = new Proposer("P-slot5", network, ACCEPTORS);
        proposerForSlot5.sendPrepare(new Ballot(9, "P-slot5"), 5);
        network.deliverAll();
        assertTrue(proposerForSlot5.hasQuorum(), "slot 5 should reach quorum at a high ballot");

        Proposer proposerForSlot6 = new Proposer("P-slot6", network, ACCEPTORS);
        proposerForSlot6.sendPrepare(new Ballot(1, "P-slot6"), 6);
        network.deliverAll();

        assertTrue(proposerForSlot6.hasQuorum(),
                "slot 6's low ballot should still reach quorum, since it's a fresh, independent Paxos instance");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(MultiSlotPaxosTest.class);
    }
}
