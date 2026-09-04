package paxossim.integration;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.core.Log;
import paxossim.core.StateMachine;
import paxossim.network.SimulatedNetwork;
import paxossim.node.Node;
import paxossim.role.Learner;
import paxossim.role.Proposer;

import java.util.List;
import java.util.Map;

import static paxossim.testing.Assertions.assertEquals;

/**
 * Full replication, end to end: a {@link Proposer} drives several slots of
 * Paxos across a 3-node acceptor cluster; every {@link Node}'s Accepted
 * replies are broadcast to three independent {@link Learner}s, each backed
 * by its own {@link Log}. Every replica then replays its own log through
 * its own {@link StateMachine}. This is the point the whole pipeline —
 * propose, accept, learn, apply — actually replicates state, so every
 * replica's state machine must converge to the identical key-value store.
 */
public class ReplicationConvergenceTest {

    private static final List<String> ACCEPTOR_IDS = List.of("A", "B", "C");
    private static final List<String> LEARNER_IDS = List.of("A-learner", "B-learner", "C-learner");

    public void testAllNodesConvergeToTheSameState() {
        SimulatedNetwork network = new SimulatedNetwork();
        new Node("A", network, LEARNER_IDS);
        new Node("B", network, LEARNER_IDS);
        new Node("C", network, LEARNER_IDS);

        Log logA = new Log();
        Log logB = new Log();
        Log logC = new Log();
        new Learner("A-learner", network, ACCEPTOR_IDS, logA);
        new Learner("B-learner", network, ACCEPTOR_IDS, logB);
        new Learner("C-learner", network, ACCEPTOR_IDS, logC);

        Proposer proposer = new Proposer("P", network, ACCEPTOR_IDS);
        runSlot(proposer, network, 0, Command.set("x", "10"));
        runSlot(proposer, network, 1, Command.set("y", "20"));
        runSlot(proposer, network, 2, Command.delete("x"));

        StateMachine stateMachineA = new StateMachine();
        StateMachine stateMachineB = new StateMachine();
        StateMachine stateMachineC = new StateMachine();
        stateMachineA.applyChosenEntries(logA);
        stateMachineB.applyChosenEntries(logB);
        stateMachineC.applyChosenEntries(logC);

        Map<String, String> expected = Map.of("y", "20");
        assertEquals(expected, stateMachineA.snapshot(), "replica A should converge to the expected state");
        assertEquals(expected, stateMachineB.snapshot(), "replica B should converge to the expected state");
        assertEquals(expected, stateMachineC.snapshot(), "replica C should converge to the expected state");
    }

    private void runSlot(Proposer proposer, SimulatedNetwork network, int slot, Command value) {
        proposer.sendPrepare(new Ballot(slot + 1, "P"), slot);
        network.deliverAll();
        Command chosen = proposer.chooseValue(value);
        proposer.sendAccept(new Ballot(slot + 1, "P"), slot, chosen);
        network.deliverAll();
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(ReplicationConvergenceTest.class);
    }
}
