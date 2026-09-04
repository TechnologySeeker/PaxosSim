package paxossim.role;

import paxossim.core.Ballot;
import paxossim.network.SimulatedNetwork;
import paxossim.node.Node;

import java.util.List;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class ProposerTest {

    public void testSendPrepareBroadcastsToEveryAcceptor() {
        SimulatedNetwork network = new SimulatedNetwork();
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));

        proposer.sendPrepare(new Ballot(1, "P"), 0);

        assertEquals(3, network.pendingCount(), "a prepare should be sent to every acceptor in the cluster");
    }

    public void testQuorumReachedWithTwoOfThreeNodes() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));

        proposer.sendPrepare(new Ballot(1, "P"), 0);
        // FIFO order: the 3 Prepares are delivered first (each immediately
        // queues its Promise reply behind the remaining Prepares), then the
        // first 2 of those queued Promises — leaving the 3rd Promise
        // undelivered, so quorum is observed before every acceptor has replied.
        for (int i = 0; i < 5; i++) {
            network.deliverNext();
        }

        assertEquals(2, proposer.promiseCount(), "two of three acceptors should have promised so far");
        assertTrue(proposer.hasQuorum(), "a majority (2 of 3) should already be a quorum, before the third replies");
    }

    public void testQuorumNotReachedWithOnlyOnePromise() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));

        proposer.sendPrepare(new Ballot(1, "P"), 0);
        // FIFO order: the 3 Prepares are delivered first (queuing their
        // Promise replies behind the remaining Prepares), then just the
        // first of those replies.
        for (int i = 0; i < 4; i++) {
            network.deliverNext();
        }

        assertEquals(1, proposer.promiseCount(), "only one acceptor should have promised so far");
        assertTrue(!proposer.hasQuorum(), "a single promise out of three should not be a quorum");
    }

    public void testWorksWithOneNodeDown() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        // "C" is down: no node is registered under that id, so its Prepare is
        // dropped silently (SimulatedNetwork's no-handler no-op) and it never replies.
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));

        proposer.sendPrepare(new Ballot(1, "P"), 0);
        network.deliverAll();

        assertEquals(2, proposer.promiseCount(), "the two live acceptors should have promised");
        assertTrue(proposer.hasQuorum(), "a majority should still be reachable with one acceptor down");
    }

    public void testSendPrepareResetsPreviouslyCollectedReplies() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));
        proposer.sendPrepare(new Ballot(1, "P"), 0);
        network.deliverAll();
        assertTrue(proposer.hasQuorum(), "the first round should reach quorum");

        proposer.sendPrepare(new Ballot(2, "P"), 0);

        assertEquals(0, proposer.promiseCount(), "starting a new round should reset previously collected promises");
        assertTrue(!proposer.hasQuorum(), "quorum should not carry over into a fresh round");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(ProposerTest.class);
    }
}
