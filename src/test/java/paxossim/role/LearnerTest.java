package paxossim.role;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.core.Log;
import paxossim.message.Accepted;
import paxossim.network.SimulatedNetwork;

import java.util.List;
import java.util.Optional;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class LearnerTest {

    private static final List<String> ACCEPTORS = List.of("A", "B", "C");

    public void testMarksSlotChosenOnceMajorityAcceptsTheSameBallot() {
        SimulatedNetwork network = new SimulatedNetwork();
        Log log = new Log();
        new Learner("L", network, ACCEPTORS, log);
        Ballot ballot = new Ballot(1, "P");
        Command value = Command.set("x", "10");

        network.send("A", "L", new Accepted(ballot, 0, value));
        network.send("B", "L", new Accepted(ballot, 0, value));
        network.deliverAll();

        assertTrue(log.isChosen(0), "a majority (2 of 3) accepting the same ballot should mark the slot chosen");
        assertEquals(Optional.of(value), log.chosenValue(0), "the chosen value should be the one that reached majority");
    }

    public void testDoesNotMarkChosenWithOnlyOneAccepted() {
        SimulatedNetwork network = new SimulatedNetwork();
        Log log = new Log();
        new Learner("L", network, ACCEPTORS, log);

        network.send("A", "L", new Accepted(new Ballot(1, "P"), 0, Command.set("x", "10")));
        network.deliverAll();

        assertTrue(!log.isChosen(0), "a single accepted reply out of three should not be a quorum");
    }

    public void testDoesNotCountDifferentBallotsTowardTheSameQuorum() {
        SimulatedNetwork network = new SimulatedNetwork();
        Log log = new Log();
        new Learner("L", network, ACCEPTORS, log);
        Ballot staleBallot = new Ballot(1, "P1");
        Ballot winningBallot = new Ballot(2, "P2");
        Command staleValue = Command.set("x", "STALE");
        Command winningValue = Command.set("x", "WINNER");

        // One acceptor accepted an older, stale ballot before a newer proposer won.
        network.send("A", "L", new Accepted(staleBallot, 0, staleValue));
        // The other two accepted the newer, winning ballot.
        network.send("B", "L", new Accepted(winningBallot, 0, winningValue));
        network.send("C", "L", new Accepted(winningBallot, 0, winningValue));
        network.deliverAll();

        assertTrue(log.isChosen(0), "the two matching votes for the winning ballot should reach quorum");
        assertEquals(Optional.of(winningValue), log.chosenValue(0),
                "the chosen value must be the winning ballot's value, not the lone stale vote's");
    }

    public void testTracksSlotsIndependently() {
        SimulatedNetwork network = new SimulatedNetwork();
        Log log = new Log();
        new Learner("L", network, ACCEPTORS, log);
        Ballot ballot = new Ballot(1, "P");
        Command valueForSlot0 = Command.set("x", "10");
        Command valueForSlot1 = Command.set("y", "20");

        network.send("A", "L", new Accepted(ballot, 0, valueForSlot0));
        network.send("B", "L", new Accepted(ballot, 0, valueForSlot0));
        network.send("A", "L", new Accepted(ballot, 1, valueForSlot1));
        network.deliverAll();

        assertTrue(log.isChosen(0), "slot 0 reached quorum and should be chosen");
        assertTrue(!log.isChosen(1), "slot 1 only has one vote and should not be chosen");
    }

    public void testIgnoresARepeatedVoteFromTheSameAcceptor() {
        SimulatedNetwork network = new SimulatedNetwork();
        Log log = new Log();
        new Learner("L", network, ACCEPTORS, log);
        Ballot ballot = new Ballot(1, "P");
        Command value = Command.set("x", "10");

        network.send("A", "L", new Accepted(ballot, 0, value));
        network.send("A", "L", new Accepted(ballot, 0, value));
        network.deliverAll();

        assertTrue(!log.isChosen(0), "two votes from the same acceptor should not count as two distinct acceptors");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(LearnerTest.class);
    }
}
