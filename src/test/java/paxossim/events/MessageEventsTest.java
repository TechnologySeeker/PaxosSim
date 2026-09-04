package paxossim.events;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.message.AcceptRequest;
import paxossim.message.Accepted;
import paxossim.message.Nack;
import paxossim.message.Prepare;
import paxossim.message.Promise;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class MessageEventsTest {

    public void testPrepare() {
        Prepare prepare = new Prepare(new Ballot(1, "A"), 3);

        assertEquals("Prepare", MessageEvents.typeName(prepare), "type name");
        assertEquals("(1,A)", MessageEvents.ballotOf(prepare), "ballot");
        assertEquals(3, MessageEvents.slotOf(prepare), "slot");
        assertTrue(MessageEvents.valueOf(prepare) == null, "a Prepare carries no value");
    }

    public void testPromiseWithNoPriorAcceptedValue() {
        Promise promise = new Promise(new Ballot(1, "A"), 0, Ballot.NONE, null);

        assertEquals("Promise", MessageEvents.typeName(promise), "type name");
        assertTrue(MessageEvents.valueOf(promise) == null, "no accepted value should surface as null");
    }

    public void testPromiseCarryingAPreviouslyAcceptedValue() {
        Command value = Command.set("x", "10");
        Promise promise = new Promise(new Ballot(2, "B"), 0, new Ballot(1, "A"), value);

        assertEquals(value.toString(), MessageEvents.valueOf(promise), "the accepted value should surface as its toString");
    }

    public void testAcceptRequest() {
        Command value = Command.set("x", "10");
        AcceptRequest request = new AcceptRequest(new Ballot(1, "A"), 2, value);

        assertEquals("AcceptRequest", MessageEvents.typeName(request), "type name");
        assertEquals(2, MessageEvents.slotOf(request), "slot");
        assertEquals(value.toString(), MessageEvents.valueOf(request), "value");
    }

    public void testAccepted() {
        Command value = Command.delete("x");
        Accepted accepted = new Accepted(new Ballot(1, "A"), 1, value);

        assertEquals("Accepted", MessageEvents.typeName(accepted), "type name");
        assertEquals(value.toString(), MessageEvents.valueOf(accepted), "value");
    }

    public void testNack() {
        Nack nack = new Nack(new Ballot(1, "A"), 0, new Ballot(2, "B"));

        assertEquals("Nack", MessageEvents.typeName(nack), "type name");
        assertEquals("(1,A)", MessageEvents.ballotOf(nack), "ballot should be the rejected ballot, not the promised one");
        assertTrue(MessageEvents.valueOf(nack) == null, "a Nack carries no value");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(MessageEventsTest.class);
    }
}
