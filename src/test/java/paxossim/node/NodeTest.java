package paxossim.node;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.events.Event;
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

    public void testPromisingRecordsAPromiseEvent() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("acceptor1", network);

        network.send("proposer1", "acceptor1", new Prepare(new Ballot(1, "A"), 0));
        network.deliverAll();

        Event event = findFirst(network, "PROMISE");
        assertEquals("acceptor1", event.toNode(), "the promising node's id should be recorded");
        assertEquals("(1,A)", event.ballot(), "the promised ballot should be recorded");
        assertEquals(Integer.valueOf(0), event.slot(), "the slot should be recorded");
    }

    public void testAcceptingRecordsAnAcceptedEvent() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("acceptor1", network);
        Command value = Command.set("x", "10");

        network.send("proposer1", "acceptor1", new AcceptRequest(new Ballot(1, "A"), 0, value));
        network.deliverAll();

        Event event = findFirst(network, "ACCEPTED");
        assertEquals("acceptor1", event.toNode(), "the accepting node's id should be recorded");
        assertEquals(value.toString(), event.value(), "the accepted value should be recorded");
    }

    public void testRejectingRecordsANackEvent() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("acceptor1", network);
        network.send("proposer1", "acceptor1", new Prepare(new Ballot(2, "A"), 0));
        network.deliverAll();

        network.send("proposer1", "acceptor1", new Prepare(new Ballot(1, "B"), 0));
        network.deliverAll();

        Event event = findFirst(network, "NACK");
        assertEquals("acceptor1", event.toNode(), "the rejecting node's id should be recorded");
    }

    private static Event findFirst(SimulatedNetwork network, String type) {
        return network.eventLog().events().stream()
                .filter(e -> e.type().equals(type))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " event was recorded"));
    }

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

    public void testNodeBroadcastsAcceptedToRegisteredLearners() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("acceptor1", network, List.of("learner1", "learner2"));
        List<Message> proposerInbox = new ArrayList<>();
        List<Message> learner1Inbox = new ArrayList<>();
        List<Message> learner2Inbox = new ArrayList<>();
        network.register("proposer1", (from, message) -> proposerInbox.add(message));
        network.register("learner1", (from, message) -> learner1Inbox.add(message));
        network.register("learner2", (from, message) -> learner2Inbox.add(message));
        Command value = Command.set("x", "10");

        network.send("proposer1", "acceptor1", new AcceptRequest(new Ballot(1, "A"), 0, value));
        network.deliverAll();

        assertTrue(proposerInbox.get(0) instanceof Accepted, "the proposer should still get the direct reply");
        assertEquals(1, learner1Inbox.size(), "learner1 should also be notified of the accepted value");
        assertEquals(1, learner2Inbox.size(), "learner2 should also be notified of the accepted value");
        assertTrue(learner1Inbox.get(0) instanceof Accepted, "learners should receive an Accepted message");
        assertEquals(value, ((Accepted) learner1Inbox.get(0)).value(), "the broadcast should carry the accepted value");
    }

    public void testNodeDoesNotBroadcastToLearnersOnAPromiseOrNack() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("acceptor1", network, List.of("learner1"));
        List<Message> learner1Inbox = new ArrayList<>();
        network.register("learner1", (from, message) -> learner1Inbox.add(message));

        network.send("proposer1", "acceptor1", new Prepare(new Ballot(1, "A"), 0));
        network.deliverAll();

        assertEquals(0, learner1Inbox.size(), "a Promise reply should not be broadcast to learners");
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
