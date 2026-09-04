package paxossim.integration;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.network.SimulatedNetwork;
import paxossim.node.Node;
import paxossim.role.Proposer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

/**
 * The failure modes this project is meant to defend against: a proposer
 * dying partway through a round, two proposers genuinely racing at the
 * message level rather than in a manually staged sequence, and a
 * partitioned cluster. Built on the failure-injection tools added to
 * {@link SimulatedNetwork} (dropWhere, partition/healPartition).
 */
public class FailureScenarioTest {

    private static final List<String> ACCEPTORS = List.of("A", "B", "C");

    /**
     * P1 sends Prepare and then "crashes" before it ever sees a reply — its
     * Promise replies are lost with it. A second proposer must still be able
     * to complete the round; P1's abandoned attempt must never come back to
     * life.
     */
    public void testProposerCrashMidPrepareDoesNotBlockRecovery() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer1 = new Proposer("P1", network, ACCEPTORS);

        proposer1.sendPrepare(new Ballot(1, "P1"), 0);
        for (int i = 0; i < 3; i++) {
            network.deliverNext(); // deliver the 3 Prepares, queuing 3 Promise replies to P1
        }
        // P1 crashes right here: its replies are lost with it, and nothing
        // ever calls a method on proposer1 again.
        int dropped = network.dropWhere(envelope -> envelope.to().equals("P1"));
        assertEquals(3, dropped, "all three of P1's queued promise replies should be lost with it");
        assertTrue(!proposer1.hasQuorum(), "P1 should never observe a quorum — it crashed before seeing any replies");

        Proposer proposer2 = new Proposer("P2", network, ACCEPTORS);
        Command value = Command.set("x", "10");
        proposer2.sendPrepare(new Ballot(2, "P2"), 0);
        network.deliverAll();
        assertTrue(proposer2.hasQuorum(), "a fresh proposer should still be able to win phase 1 after P1's crash");

        Command chosen = proposer2.chooseValue(value);
        proposer2.sendAccept(new Ballot(2, "P2"), 0, chosen);
        network.deliverAll();

        assertTrue(proposer2.hasAcceptedQuorum(), "the recovering proposer should complete the round successfully");
        assertEquals(Optional.of(value), proposer2.chosenValue(), "P2's own value should be chosen since nothing was ever accepted");
        assertTrue(!proposer1.hasQuorum(), "P1's crashed attempt should never recover on its own");
    }

    /**
     * P1 wins phase 1, starts broadcasting Accept, but crashes mid-broadcast
     * — only one acceptor (A) ever receives and accepts its value before it
     * dies, so P1 itself never reaches a quorum. A recovering proposer must
     * discover A's partially-accepted value through Phase 1 and safely adopt
     * it, rather than silently overwriting it with a value of its own.
     */
    public void testProposerCrashMidAcceptStillPreservesSafety() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer1 = new Proposer("P1", network, ACCEPTORS);
        Command v1 = Command.set("x", "V1");

        proposer1.sendPrepare(new Ballot(1, "P1"), 0);
        network.deliverAll();
        assertTrue(proposer1.hasQuorum(), "P1 should win phase 1 cleanly");
        Command chosenForP1 = proposer1.chooseValue(v1);

        proposer1.sendAccept(new Ballot(1, "P1"), 0, chosenForP1);
        // P1 crashes mid-broadcast: only A's request ever goes out.
        network.dropWhere(envelope -> envelope.to().equals("B") || envelope.to().equals("C"));
        network.deliverAll();

        assertEquals(1, proposer1.acceptedCount(), "only A should have accepted before P1 crashed");
        assertTrue(!proposer1.hasAcceptedQuorum(), "P1 should never have reached an accept quorum before crashing");

        Proposer proposer2 = new Proposer("P2", network, ACCEPTORS);
        Command v2 = Command.set("x", "V2");
        proposer2.sendPrepare(new Ballot(2, "P2"), 0);
        network.deliverAll();
        assertTrue(proposer2.hasQuorum(), "P2 should be able to recover and win phase 1");

        Command chosenForP2 = proposer2.chooseValue(v2);
        assertEquals(chosenForP1, chosenForP2,
                "P2 must adopt A's already-accepted value from the crashed P1's round, not propose its own");

        proposer2.sendAccept(new Ballot(2, "P2"), 0, chosenForP2);
        network.deliverAll();

        assertTrue(proposer2.hasAcceptedQuorum(), "P2's accept phase should now reach quorum");
        assertEquals(Optional.of(chosenForP1), proposer2.chosenValue(),
                "the value ultimately chosen must be P1's partially-accepted value, safely recovered");
    }

    /**
     * Two proposers race for real at the message level (both Prepare, then
     * both Accept, delivered through one shared FIFO queue rather than
     * manually staged in sequence) rather than one completing before the
     * other starts. Both can legitimately win Phase 1 — an acceptor's
     * promise is only a snapshot of what it knew at the time — but only the
     * higher ballot can win Phase 2, since by then every acceptor has moved
     * on to it.
     */
    public void testCompetingProposersRaceAtTheMessageLevel() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        Proposer proposer1 = new Proposer("P1", network, ACCEPTORS);
        Proposer proposer2 = new Proposer("P2", network, ACCEPTORS);
        Command v1 = Command.set("x", "V1");
        Command v2 = Command.set("x", "V2");

        proposer1.sendPrepare(new Ballot(1, "P1"), 0);
        proposer2.sendPrepare(new Ballot(2, "P2"), 0);
        network.deliverAll();

        assertTrue(proposer1.hasQuorum(), "P1's prepare was seen by every acceptor before P2's and should still win phase 1");
        assertTrue(proposer2.hasQuorum(), "P2's higher-ballot prepare should also win phase 1");

        proposer1.sendAccept(new Ballot(1, "P1"), 0, v1);
        proposer2.sendAccept(new Ballot(2, "P2"), 0, v2);
        network.deliverAll();

        assertEquals(3, proposer1.nackCount(), "every acceptor has moved on to P2's higher ballot, so P1's accept is nacked everywhere");
        assertTrue(!proposer1.hasAcceptedQuorum(), "P1 should never reach an accept quorum");
        assertEquals(Optional.empty(), proposer1.chosenValue(), "P1 should never observe a value chosen");

        assertTrue(proposer2.hasAcceptedQuorum(), "P2's higher ballot should win the accept phase");
        assertEquals(Optional.of(v2), proposer2.chosenValue(), "P2's value should be the one ultimately chosen");
    }

    /** A proposer on the majority side of a partition can still make progress. */
    public void testMajoritySideOfAPartitionCanStillReachQuorum() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        network.partition(Set.of("C")); // C is cut off from everyone else, including the proposer
        Proposer proposer = new Proposer("P", network, ACCEPTORS);
        Command value = Command.set("x", "10");

        proposer.sendPrepare(new Ballot(1, "P"), 0);
        network.deliverAll();
        assertTrue(proposer.hasQuorum(), "A and B alone are still a majority even with C partitioned away");

        Command chosen = proposer.chooseValue(value);
        proposer.sendAccept(new Ballot(1, "P"), 0, chosen);
        network.deliverAll();

        assertTrue(proposer.hasAcceptedQuorum(), "the accept phase should also succeed on the majority side");
        assertEquals(Optional.of(value), proposer.chosenValue(), "the round should complete successfully despite the partition");
    }

    /** A proposer stuck on the minority side of a partition cannot force progress alone. */
    public void testMinoritySideOfAPartitionCannotReachQuorumAlone() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        network.partition(Set.of("B", "C")); // the proposer can only reach A
        Proposer proposer = new Proposer("P", network, ACCEPTORS);

        proposer.sendPrepare(new Ballot(1, "P"), 0);
        network.deliverAll();

        assertEquals(1, proposer.promiseCount(), "the proposer should only be able to reach A");
        assertTrue(!proposer.hasQuorum(), "one acceptor alone is not a majority of three, so no progress should be possible");
    }

    /** Once a partition heals, a previously stalled round can succeed on retry. */
    public void testHealingThePartitionLetsAStalledRoundSucceedOnRetry() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network);
        new Node("B", network);
        new Node("C", network);
        network.partition(Set.of("B", "C"));
        Proposer proposer = new Proposer("P", network, ACCEPTORS);
        Command value = Command.set("x", "10");
        proposer.sendPrepare(new Ballot(1, "P"), 0);
        network.deliverAll();
        assertTrue(!proposer.hasQuorum(), "the round should stall while the proposer can only reach a minority");

        network.healPartition();
        proposer.sendPrepare(new Ballot(2, "P"), 0);
        network.deliverAll();
        assertTrue(proposer.hasQuorum(), "the retry should now reach quorum once the partition has healed");

        Command chosen = proposer.chooseValue(value);
        proposer.sendAccept(new Ballot(2, "P"), 0, chosen);
        network.deliverAll();

        assertTrue(proposer.hasAcceptedQuorum(), "the retried round should complete successfully");
        assertEquals(Optional.of(value), proposer.chosenValue(), "the value should be chosen once the cluster has healed");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(FailureScenarioTest.class);
    }
}
