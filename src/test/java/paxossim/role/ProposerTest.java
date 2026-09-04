package paxossim.role;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.message.AcceptRequest;
import paxossim.network.SimulatedNetwork;
import paxossim.node.Node;

import java.util.List;
import java.util.Optional;

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

    /**
     * The core Paxos safety property: acceptor A already accepted Y (from an
     * earlier round), acceptor B never accepted anything. A new proposer
     * with its own original value X must adopt Y, not propose X, or the
     * safety guarantee that a value already accepted by (potentially) a
     * quorum is never overwritten would break.
     */
    public void testChoosesHighestAcceptedValueOverOwnOriginalValue() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Command y = Command.set("x", "Y");
        // An earlier round already got Y accepted at A only; B and C never saw it.
        network.send("P0", "A", new AcceptRequest(new Ballot(1, "P0"), 0, y));
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));
        Command x = Command.set("x", "X");

        proposer.sendPrepare(new Ballot(2, "P"), 0);
        network.deliverAll();

        assertTrue(proposer.hasQuorum(), "quorum should be reached before choosing a value");
        Command chosen = proposer.chooseValue(x);
        assertEquals(y, chosen, "the proposer must adopt A's already-accepted value instead of its own");
    }

    public void testChoosesOwnValueWhenNoPromiseCarriesAnAcceptedValue() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));
        Command x = Command.set("x", "X");

        proposer.sendPrepare(new Ballot(1, "P"), 0);
        network.deliverAll();

        Command chosen = proposer.chooseValue(x);
        assertEquals(x, chosen, "with nothing previously accepted, the proposer is free to use its own value");
    }

    public void testChoosesValueFromTheHighestAcceptedBallotAmongSeveral() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Command y = Command.set("x", "Y");
        Command z = Command.set("x", "Z");
        // A accepted Y at ballot (1,P0); B later accepted Z at the higher ballot (2,P1).
        network.send("P0", "A", new AcceptRequest(new Ballot(1, "P0"), 0, y));
        network.send("P1", "B", new AcceptRequest(new Ballot(2, "P1"), 0, z));
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));
        Command x = Command.set("x", "X");

        proposer.sendPrepare(new Ballot(3, "P"), 0);
        network.deliverAll();

        Command chosen = proposer.chooseValue(x);
        assertEquals(z, chosen, "the proposer must adopt the value from the highest accepted ballot (B's Z), not A's older Y or its own X");
    }

    public void testSendAcceptBroadcastsToEveryAcceptor() {
        SimulatedNetwork network = new SimulatedNetwork();
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));

        proposer.sendAccept(new Ballot(1, "P"), 0, Command.set("x", "10"));

        assertEquals(3, network.pendingCount(), "an accept request should be sent to every acceptor in the cluster");
    }

    public void testValueChosenOnceAcceptedQuorumReached() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));
        Command value = Command.set("x", "10");

        proposer.sendAccept(new Ballot(1, "P"), 0, value);
        network.deliverAll();

        assertTrue(proposer.hasAcceptedQuorum(), "a majority of acceptors accepting should be a quorum");
        assertEquals(Optional.of(value), proposer.chosenValue(), "the value should be declared chosen once quorum accepts");
    }

    public void testValueNotChosenWithOnlyOneAccepted() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));

        proposer.sendAccept(new Ballot(1, "P"), 0, Command.set("x", "10"));
        // FIFO order: the 3 AcceptRequests are delivered first (queuing their
        // Accepted replies behind the remaining requests), then just the first reply.
        for (int i = 0; i < 4; i++) {
            network.deliverNext();
        }

        assertEquals(1, proposer.acceptedCount(), "only one acceptor should have accepted so far");
        assertTrue(!proposer.hasAcceptedQuorum(), "a single accepted reply out of three should not be a quorum");
        assertEquals(Optional.empty(), proposer.chosenValue(), "the value should not be chosen before quorum");
    }

    public void testAcceptPhaseStillReachesQuorumWithOneNodeDown() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        // "C" is down: no node registered, so its AcceptRequest is dropped silently.
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));
        Command value = Command.set("x", "10");

        proposer.sendAccept(new Ballot(1, "P"), 0, value);
        network.deliverAll();

        assertEquals(2, proposer.acceptedCount(), "the two live acceptors should have accepted");
        assertTrue(proposer.hasAcceptedQuorum(), "a majority should still be reachable with one acceptor down");
        assertEquals(Optional.of(value), proposer.chosenValue(), "the value should still be chosen with one node down");
    }

    public void testFullPrepareThenAcceptFlowChoosesTheSafeValue() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Command y = Command.set("x", "Y");
        // An earlier round already got Y accepted at A only.
        network.send("P0", "A", new AcceptRequest(new Ballot(1, "P0"), 0, y));
        Proposer proposer = new Proposer("P", network, List.of("A", "B", "C"));
        Command x = Command.set("x", "X");

        proposer.sendPrepare(new Ballot(2, "P"), 0);
        network.deliverAll();
        Command chosenForRound = proposer.chooseValue(x);
        proposer.sendAccept(new Ballot(2, "P"), 0, chosenForRound);
        network.deliverAll();

        assertTrue(proposer.hasAcceptedQuorum(), "the accept phase should reach quorum");
        assertEquals(Optional.of(y), proposer.chosenValue(), "the value ultimately chosen must be A's already-accepted Y, not the proposer's X");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(ProposerTest.class);
    }
}
