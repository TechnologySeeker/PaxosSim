package paxossim.integration;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.network.SimulatedNetwork;
import paxossim.node.Node;
import paxossim.role.Proposer;

import java.util.List;
import java.util.Optional;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

/**
 * End-to-end tests driving a full single-decree Paxos round — Prepare,
 * quorum, {@link Proposer#chooseValue}, Accept, quorum — across a 3-node
 * cluster, through nothing but the public {@link Proposer}/{@link Node}/
 * {@link SimulatedNetwork} APIs already built up commit by commit. This is
 * the first point the whole protocol is exercised together rather than one
 * phase at a time.
 */
public class SingleDecreePaxosTest {

    private static final List<String> ACCEPTORS = List.of("A", "B", "C");

    public void testNormalSuccessWithAllNodesHealthy() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer = new Proposer("P", network, ACCEPTORS);
        Command value = Command.set("x", "10");

        proposer.sendPrepare(new Ballot(1, "P"), 0);
        network.deliverAll();
        assertTrue(proposer.hasQuorum(), "prepare phase should reach quorum with all nodes healthy");

        Command chosenForRound = proposer.chooseValue(value);
        proposer.sendAccept(new Ballot(1, "P"), 0, chosenForRound);
        network.deliverAll();

        assertTrue(proposer.hasAcceptedQuorum(), "accept phase should reach quorum with all nodes healthy");
        assertEquals(Optional.of(value), proposer.chosenValue(), "the proposer's own value should be chosen");
    }

    public void testSucceedsWithOneNodeDown() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        // "C" is down for the whole round: nothing is registered under that id.
        Proposer proposer = new Proposer("P", network, ACCEPTORS);
        Command value = Command.set("x", "10");

        proposer.sendPrepare(new Ballot(1, "P"), 0);
        network.deliverAll();
        assertTrue(proposer.hasQuorum(), "2 of 3 acceptors is still a quorum for the prepare phase");

        Command chosenForRound = proposer.chooseValue(value);
        proposer.sendAccept(new Ballot(1, "P"), 0, chosenForRound);
        network.deliverAll();

        assertTrue(proposer.hasAcceptedQuorum(), "2 of 3 acceptors is still a quorum for the accept phase");
        assertEquals(Optional.of(value), proposer.chosenValue(), "the round should still succeed with one node down");
    }

    public void testSucceedsDespiteADroppedPromise() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer = new Proposer("P", network, ACCEPTORS);
        Command value = Command.set("x", "10");

        proposer.sendPrepare(new Ballot(1, "P"), 0);
        // Deliver the 3 Prepares first (FIFO: each queues its Promise behind
        // the remaining Prepares), leaving [Promise(A), Promise(B), Promise(C)].
        for (int i = 0; i < 3; i++) {
            network.deliverNext();
        }
        network.deliverNext(); // Promise(A) reaches the proposer.
        network.dropNext();    // Promise(B) is lost in transit.
        network.deliverNext(); // Promise(C) reaches the proposer.

        assertEquals(2, proposer.promiseCount(), "only A and C's promises should have arrived");
        assertTrue(proposer.hasQuorum(), "2 surviving promises out of 3 is still a quorum despite the drop");

        Command chosenForRound = proposer.chooseValue(value);
        proposer.sendAccept(new Ballot(1, "P"), 0, chosenForRound);
        network.deliverAll();

        assertTrue(proposer.hasAcceptedQuorum(), "accept phase should still reach quorum");
        assertEquals(Optional.of(value), proposer.chosenValue(), "the round should still succeed despite the dropped message");
    }

    public void testCompetingProposersOnlyOneValueEndsUpChosen() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer p1 = new Proposer("P1", network, ACCEPTORS);
        Proposer p2 = new Proposer("P2", network, ACCEPTORS);
        Command v1 = Command.set("x", "V1");
        Command v2 = Command.set("x", "V2");

        // P1 completes Phase 1 first, at the lower ballot.
        p1.sendPrepare(new Ballot(1, "P1"), 0);
        network.deliverAll();
        assertTrue(p1.hasQuorum(), "P1 should win phase 1 first");
        Command chosenForP1 = p1.chooseValue(v1);

        // P2 then completes Phase 1 at a higher ballot, before P1 gets to accept.
        p2.sendPrepare(new Ballot(2, "P2"), 0);
        network.deliverAll();
        assertTrue(p2.hasQuorum(), "P2 should also win phase 1, at a higher ballot");
        Command chosenForP2 = p2.chooseValue(v2);

        // P2 gets its value accepted first.
        p2.sendAccept(new Ballot(2, "P2"), 0, chosenForP2);
        network.deliverAll();
        assertTrue(p2.hasAcceptedQuorum(), "P2's accept phase should reach quorum");
        assertEquals(Optional.of(v2), p2.chosenValue(), "P2's value should be chosen");

        // P1 now tries to accept under its now-stale, lower ballot.
        p1.sendAccept(new Ballot(1, "P1"), 0, chosenForP1);
        network.deliverAll();

        assertEquals(3, p1.nackCount(), "every acceptor should have moved on to P2's higher ballot and nack P1");
        assertTrue(!p1.hasAcceptedQuorum(), "P1's stale accept should not reach quorum");
        assertEquals(Optional.empty(), p1.chosenValue(), "P1 should never observe a value chosen under its own stale ballot");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(SingleDecreePaxosTest.class);
    }
}
