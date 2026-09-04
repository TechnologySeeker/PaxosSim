package paxossim.role;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.message.AcceptRequest;
import paxossim.message.Accepted;
import paxossim.message.Message;
import paxossim.message.Nack;
import paxossim.message.Prepare;
import paxossim.message.Promise;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class AcceptorTest {

    public void testPromisesHigherBallot() {
        Acceptor acceptor = new Acceptor();
        Ballot ballot = new Ballot(1, "A");

        Message reply = acceptor.onPrepare(new Prepare(ballot, 0));

        assertTrue(reply instanceof Promise, "higher ballot should be promised");
        assertEquals(ballot, ((Promise) reply).ballot(), "promise should echo the requested ballot");
    }

    public void testRejectsLowerPrepareWithNack() {
        Acceptor acceptor = new Acceptor();
        Ballot high = new Ballot(2, "A");
        Ballot low = new Ballot(1, "B");
        acceptor.onPrepare(new Prepare(high, 0));

        Message reply = acceptor.onPrepare(new Prepare(low, 0));

        assertTrue(reply instanceof Nack, "lower ballot prepare should be rejected");
        assertEquals(high, ((Nack) reply).promisedBallot(), "nack should report the higher promised ballot");
    }

    public void testAcceptsHigherBallot() {
        Acceptor acceptor = new Acceptor();
        Ballot ballot = new Ballot(1, "A");
        Command value = Command.set("x", "10");

        Message reply = acceptor.onAccept(new AcceptRequest(ballot, 0, value));

        assertTrue(reply instanceof Accepted, "higher ballot should be accepted");
        assertEquals(value, ((Accepted) reply).value(), "accepted reply should echo the accepted value");
    }

    public void testRejectsLowerAcceptRequestWithNack() {
        Acceptor acceptor = new Acceptor();
        Ballot high = new Ballot(2, "A");
        Ballot low = new Ballot(1, "B");
        acceptor.onPrepare(new Prepare(high, 0));

        Message reply = acceptor.onAccept(new AcceptRequest(low, 0, Command.set("x", "10")));

        assertTrue(reply instanceof Nack, "accept request below the promised ballot should be rejected");
        assertEquals(high, ((Nack) reply).promisedBallot(), "nack should report the higher promised ballot");
    }

    public void testPreservesAcceptedValueAcrossHigherPrepare() {
        Acceptor acceptor = new Acceptor();
        Ballot firstBallot = new Ballot(1, "A");
        Command firstValue = Command.set("x", "10");
        acceptor.onAccept(new AcceptRequest(firstBallot, 0, firstValue));

        Ballot secondBallot = new Ballot(2, "B");
        Message reply = acceptor.onPrepare(new Prepare(secondBallot, 0));

        assertTrue(reply instanceof Promise, "higher-round prepare should still be promised");
        Promise promise = (Promise) reply;
        assertEquals(firstBallot, promise.acceptedBallot(), "promise should carry the previously accepted ballot");
        assertEquals(firstValue, promise.acceptedValue(), "promise should carry the previously accepted value");
    }

    public void testSlotsAreIndependent() {
        Acceptor acceptor = new Acceptor();
        Ballot ballot = new Ballot(1, "A");
        acceptor.onAccept(new AcceptRequest(ballot, 0, Command.set("x", "10")));

        Message reply = acceptor.onPrepare(new Prepare(ballot, 1));

        assertTrue(reply instanceof Promise, "a fresh slot should not be affected by another slot's state");
        assertTrue(((Promise) reply).acceptedValue() == null, "a fresh slot should have no accepted value yet");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(AcceptorTest.class);
    }
}
