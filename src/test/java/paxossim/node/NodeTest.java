package paxossim.node;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.message.AcceptRequest;
import paxossim.message.Accepted;
import paxossim.message.Message;
import paxossim.message.Nack;
import paxossim.message.Prepare;
import paxossim.message.Promise;
import paxossim.network.Envelope;
import paxossim.network.SimulatedNetwork;

import java.util.ArrayList;
import java.util.List;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class NodeTest {

    public void testNodeRegistersAndRespondsToPrepare() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("acceptor1", network);
        List<Message> proposerInbox = new ArrayList<>();
        network.register("proposer1", (from, message) -> proposerInbox.add(message));

        network.send("proposer1", "acceptor1", new Prepare(new Ballot(1, "A"), 0));
        network.deliverAll();

        assertEquals(1, proposerInbox.size(), "the proposer should receive exactly one reply");
        assertTrue(proposerInbox.get(0) instanceof Promise, "a fresh node should promise the prepare");
    }

    public void testNodeReplyIsAddressedBackToTheSender() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("acceptor1", network);
        network.send("proposer1", "acceptor1", new Prepare(new Ballot(1, "A"), 0));

        Envelope request = network.deliverNext();
        Envelope reply = network.deliverNext();

        assertEquals("acceptor1", request.to(), "the prepare should have been addressed to the node");
        assertEquals("acceptor1", reply.from(), "the reply should come from the node");
        assertEquals("proposer1", reply.to(), "the reply should be addressed back to the original sender");
    }

    public void testNodeRejectsLowerBallotWithNack() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("acceptor1", network);
        List<Message> proposerInbox = new ArrayList<>();
        network.register("proposer1", (from, message) -> proposerInbox.add(message));
        network.send("proposer1", "acceptor1", new Prepare(new Ballot(2, "A"), 0));
        network.deliverAll();

        network.send("proposer1", "acceptor1", new Prepare(new Ballot(1, "B"), 0));
        network.deliverAll();

        assertTrue(proposerInbox.get(1) instanceof Nack, "a lower ballot prepare should be nacked");
    }

    public void testNodeHandlesAcceptRequestAndRepliesAccepted() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("acceptor1", network);
        List<Message> proposerInbox = new ArrayList<>();
        network.register("proposer1", (from, message) -> proposerInbox.add(message));
        Command value = Command.set("x", "10");

        network.send("proposer1", "acceptor1", new AcceptRequest(new Ballot(1, "A"), 0, value));
        network.deliverAll();

        assertTrue(proposerInbox.get(0) instanceof Accepted, "a higher ballot accept request should be accepted");
        assertEquals(value, ((Accepted) proposerInbox.get(0)).value(), "the accepted reply should echo the value");
    }

    public void testEachNodeKeepsIndependentAcceptorState() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("acceptor1", network);
        new Node("acceptor2", network);
        List<Message> proposerInbox = new ArrayList<>();
        network.register("proposer1", (from, message) -> proposerInbox.add(message));

        network.send("proposer1", "acceptor1", new Prepare(new Ballot(5, "A"), 0));
        network.deliverAll();
        network.send("proposer1", "acceptor2", new Prepare(new Ballot(1, "B"), 0));
        network.deliverAll();

        assertTrue(proposerInbox.get(1) instanceof Promise, "a different node should not see acceptor1's promised ballot");
    }

    public void testNodeRejectsMessageTypesWithoutProposerOrLearnerLogicYet() {
        SimulatedNetwork network = new SimulatedNetwork();
        Node node = new Node("acceptor1", network);
        boolean threw = false;

        try {
            node.handle(new Promise(new Ballot(1, "A"), 0, Ballot.NONE, null));
        } catch (IllegalArgumentException expected) {
            threw = true;
        }

        assertTrue(threw, "a node with no proposer/learner logic yet should reject a Promise message");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(NodeTest.class);
    }
}
