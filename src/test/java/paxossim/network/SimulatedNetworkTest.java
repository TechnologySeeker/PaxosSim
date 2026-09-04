package paxossim.network;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.message.AcceptRequest;
import paxossim.message.Message;
import paxossim.message.Prepare;
import paxossim.message.Promise;
import paxossim.role.Acceptor;

import java.util.ArrayList;
import java.util.List;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class SimulatedNetworkTest {

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
