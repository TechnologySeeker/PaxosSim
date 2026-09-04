package paxossim.network;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.events.Event;
import paxossim.message.AcceptRequest;
import paxossim.message.Message;
import paxossim.message.Prepare;
import paxossim.message.Promise;
import paxossim.role.Acceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class SimulatedNetworkTest {

    public void testSendRecordsASendEvent() {
        SimulatedNetwork network = new SimulatedNetwork();

        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));

        Event event = network.eventLog().events().get(0);
        assertEquals("SEND", event.type(), "sending should record a SEND event");
        assertEquals("A", event.fromNode(), "fromNode should be recorded");
        assertEquals("B", event.toNode(), "toNode should be recorded");
        assertEquals("Prepare", event.message(), "message type should be recorded");
    }

    public void testDeliverNextToARegisteredHandlerRecordsAReceiveEvent() {
        SimulatedNetwork network = new SimulatedNetwork();
        network.register("B", (from, message) -> {});
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));

        network.deliverNext();

        Event event = network.eventLog().events().get(1);
        assertEquals("RECEIVE", event.type(), "delivering to a registered handler should record RECEIVE");
    }

    public void testDeliverNextToAnUnregisteredRecipientRecordsADroppedEvent() {
        SimulatedNetwork network = new SimulatedNetwork();
        network.send("A", "ghost", new Prepare(new Ballot(1, "A"), 0));

        network.deliverNext();

        Event event = network.eventLog().events().get(1);
        assertEquals("DROPPED", event.type(), "delivering to an unregistered recipient should record DROPPED");
    }

    public void testPartitionBlockedDeliveryRecordsADroppedEvent() {
        SimulatedNetwork network = new SimulatedNetwork();
        network.register("B", (from, message) -> {});
        network.partition(Set.of("B"));
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));

        network.deliverNext();

        Event event = network.eventLog().events().get(1);
        assertEquals("DROPPED", event.type(), "delivery blocked by a partition should record DROPPED");
    }

    public void testDropNextRecordsADroppedEvent() {
        SimulatedNetwork network = new SimulatedNetwork();
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));

        network.dropNext();

        Event event = network.eventLog().events().get(1);
        assertEquals("DROPPED", event.type(), "dropNext should record a DROPPED event");
    }

    public void testDropWhereRecordsADroppedEventForEachMatch() {
        SimulatedNetwork network = new SimulatedNetwork();
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 1));

        network.dropWhere(envelope -> true);

        long droppedCount = network.eventLog().events().stream().filter(e -> e.type().equals("DROPPED")).count();
        assertEquals(2L, droppedCount, "dropWhere should record one DROPPED event per matched envelope");
    }

    public void testDeliverNextReturnsNullWhenQueueIsEmpty() {
        SimulatedNetwork network = new SimulatedNetwork();

        assertTrue(network.deliverNext() == null, "an empty network should have nothing to deliver");
        assertTrue(!network.hasPending(), "an empty network should report no pending envelopes");
    }

    public void testSendQueuesAnEnvelopeForDelivery() {
        SimulatedNetwork network = new SimulatedNetwork();
        Message message = new Prepare(new Ballot(1, "A"), 0);

        network.send("A", "B", message);

        assertTrue(network.hasPending(), "a sent message should be pending until delivered");
        assertEquals(1, network.pendingCount(), "one send should leave one envelope pending");
    }

    public void testDeliverNextReturnsEnvelopesInFifoOrder() {
        SimulatedNetwork network = new SimulatedNetwork();
        Message first = new Prepare(new Ballot(1, "A"), 0);
        Message second = new Prepare(new Ballot(1, "A"), 1);
        network.send("A", "B", first);
        network.send("A", "C", second);

        Envelope firstDelivered = network.deliverNext();
        Envelope secondDelivered = network.deliverNext();

        assertEquals(first, firstDelivered.message(), "messages should be delivered in send order");
        assertEquals(second, secondDelivered.message(), "messages should be delivered in send order");
    }

    public void testDeliverNextDrainsTheQueueByOne() {
        SimulatedNetwork network = new SimulatedNetwork();
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 1));

        network.deliverNext();

        assertEquals(1, network.pendingCount(), "delivering one envelope should leave the rest pending");
    }

    public void testDropNextDiscardsTheOldestEnvelopeWithoutDelivering() {
        SimulatedNetwork network = new SimulatedNetwork();
        List<Message> received = new ArrayList<>();
        network.register("B", (from, message) -> received.add(message));
        Message dropped = new Prepare(new Ballot(1, "A"), 0);
        Message kept = new Prepare(new Ballot(1, "A"), 1);
        network.send("A", "B", dropped);
        network.send("A", "B", kept);

        Envelope droppedEnvelope = network.dropNext();
        network.deliverNext();

        assertEquals(dropped, droppedEnvelope.message(), "dropNext should discard the oldest envelope");
        assertEquals(1, received.size(), "the handler should only see the message that wasn't dropped");
        assertEquals(kept, received.get(0), "the surviving message should still be delivered normally");
    }

    public void testDropNextReturnsNullWhenQueueIsEmpty() {
        SimulatedNetwork network = new SimulatedNetwork();

        assertTrue(network.dropNext() == null, "dropping from an empty network should be a no-op");
    }

    public void testDropWhereRemovesEveryMatchingEnvelopeWherever() {
        SimulatedNetwork network = new SimulatedNetwork();
        List<Message> receivedAtB = new ArrayList<>();
        List<Message> receivedAtC = new ArrayList<>();
        network.register("B", (from, message) -> receivedAtB.add(message));
        network.register("C", (from, message) -> receivedAtC.add(message));
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));
        network.send("A", "C", new Prepare(new Ballot(1, "A"), 1));
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 2));

        int dropped = network.dropWhere(envelope -> envelope.to().equals("B"));
        network.deliverAll();

        assertEquals(2, dropped, "both envelopes addressed to B should have been dropped");
        assertEquals(0, receivedAtB.size(), "B should receive nothing");
        assertEquals(1, receivedAtC.size(), "C should be unaffected");
    }

    public void testReorderPendingChangesDeliveryOrder() {
        SimulatedNetwork network = new SimulatedNetwork();
        List<Message> received = new ArrayList<>();
        network.register("B", (from, message) -> received.add(message));
        Message first = new Prepare(new Ballot(1, "A"), 0);
        Message second = new Prepare(new Ballot(1, "A"), 1);
        network.send("A", "B", first);
        network.send("A", "B", second);

        network.reorderPending(pending -> {
            List<Envelope> reversed = new ArrayList<>(pending);
            Collections.reverse(reversed);
            return reversed;
        });
        network.deliverAll();

        assertEquals(second, received.get(0), "the reordered queue should deliver the reversed order");
        assertEquals(first, received.get(1), "the reordered queue should deliver the reversed order");
    }

    public void testPartitionBlocksMessagesAcrossTheBoundary() {
        SimulatedNetwork network = new SimulatedNetwork();
        List<Message> receivedAtB = new ArrayList<>();
        network.register("B", (from, message) -> receivedAtB.add(message));
        network.partition(Set.of("B"));

        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));
        network.deliverAll();

        assertEquals(0, receivedAtB.size(), "a message crossing the partition boundary should be dropped");
    }

    public void testPartitionAllowsMessagesWithinTheSameSide() {
        SimulatedNetwork network = new SimulatedNetwork();
        List<Message> receivedAtB = new ArrayList<>();
        List<Message> receivedAtC = new ArrayList<>();
        network.register("B", (from, message) -> receivedAtB.add(message));
        network.register("C", (from, message) -> receivedAtC.add(message));
        // Isolate B and C together, away from A: they can still reach each other.
        network.partition(Set.of("B", "C"));

        network.send("B", "C", new Prepare(new Ballot(1, "B"), 0));
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));
        network.deliverAll();

        assertEquals(1, receivedAtC.size(), "B and C are on the same side of the partition and should still connect");
        assertEquals(0, receivedAtB.size(), "A is on the other side of the partition and should be blocked");
    }

    public void testHealPartitionRestoresDelivery() {
        SimulatedNetwork network = new SimulatedNetwork();
        List<Message> receivedAtB = new ArrayList<>();
        network.register("B", (from, message) -> receivedAtB.add(message));
        network.partition(Set.of("B"));
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));
        network.deliverAll();
        assertEquals(0, receivedAtB.size(), "the first message should be blocked while partitioned");

        network.healPartition();
        network.send("A", "B", new Prepare(new Ballot(2, "A"), 0));
        network.deliverAll();

        assertEquals(1, receivedAtB.size(), "delivery should resume normally once the partition heals");
    }

    public void testDeliverNextDispatchesToRegisteredHandler() {
        SimulatedNetwork network = new SimulatedNetwork();
        List<Message> received = new ArrayList<>();
        network.register("B", (from, message) -> received.add(message));
        Message message = new Prepare(new Ballot(1, "A"), 0);

        network.send("A", "B", message);
        network.deliverNext();

        assertEquals(1, received.size(), "the registered handler should have been called once");
        assertEquals(message, received.get(0), "the handler should receive the delivered message");
    }

    public void testDeliverNextIsANoOpForAnUnregisteredRecipient() {
        SimulatedNetwork network = new SimulatedNetwork();
        network.send("A", "ghost", new Prepare(new Ballot(1, "A"), 0));

        Envelope delivered = network.deliverNext();

        assertTrue(delivered != null, "the envelope should still be dequeued and returned");
        assertTrue(!network.hasPending(), "delivery should not leave the envelope stuck in the queue");
    }

    public void testDeliverAllDrainsEveryPendingEnvelope() {
        SimulatedNetwork network = new SimulatedNetwork();
        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));
        network.send("A", "C", new Prepare(new Ballot(1, "A"), 1));
        network.send("A", "D", new Prepare(new Ballot(1, "A"), 2));

        int delivered = network.deliverAll();

        assertEquals(3, delivered, "deliverAll should report how many envelopes it delivered");
        assertTrue(!network.hasPending(), "deliverAll should leave nothing pending");
    }

    public void testDeliverAllAlsoDrainsEnvelopesEnqueuedByHandlers() {
        SimulatedNetwork network = new SimulatedNetwork();
        List<Message> receivedAtC = new ArrayList<>();
        network.register("B", (from, message) -> network.send("B", "C", message));
        network.register("C", (from, message) -> receivedAtC.add(message));

        network.send("A", "B", new Prepare(new Ballot(1, "A"), 0));
        int delivered = network.deliverAll();

        assertEquals(2, delivered, "deliverAll should also deliver messages sent by handlers mid-drain");
        assertEquals(1, receivedAtC.size(), "the relayed message should reach its final recipient");
    }

    public void testAcceptorRoundTripThroughTheNetwork() {
        SimulatedNetwork network = new SimulatedNetwork();
        Acceptor acceptor = new Acceptor();
        List<Message> proposerInbox = new ArrayList<>();
        network.register("acceptor1", (from, message) -> {
            Message reply = switch (message) {
                case Prepare prepare -> acceptor.onPrepare(prepare);
                case AcceptRequest request -> acceptor.onAccept(request);
                default -> throw new IllegalArgumentException("unexpected message: " + message);
            };
            network.send("acceptor1", from, reply);
        });
        network.register("proposer1", (from, message) -> proposerInbox.add(message));

        network.send("proposer1", "acceptor1", new Prepare(new Ballot(1, "A"), 0));
        network.deliverAll();

        assertEquals(1, proposerInbox.size(), "the proposer should receive exactly one reply");
        assertTrue(proposerInbox.get(0) instanceof Promise, "a fresh acceptor should promise the prepare");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(SimulatedNetworkTest.class);
    }
}
