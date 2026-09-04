package paxossim;

import paxossim.core.BallotTest;
import paxossim.core.LogTest;
import paxossim.core.StateMachineTest;
import paxossim.integration.MultiSlotPaxosTest;
import paxossim.integration.ReplicationConvergenceTest;
import paxossim.integration.SingleDecreePaxosTest;
import paxossim.network.SimulatedNetworkTest;
import paxossim.node.NodeTest;
import paxossim.role.AcceptorTest;
import paxossim.role.LearnerTest;
import paxossim.role.ProposerTest;
import paxossim.testing.TestRunner;

/** Entry point that runs every test class until a build tool takes over. */
public final class RunAllTests {

    private RunAllTests() {}

    public static void main(String[] args) {
        TestRunner.run(BallotTest.class, LogTest.class, StateMachineTest.class, AcceptorTest.class,
                ProposerTest.class, LearnerTest.class, SimulatedNetworkTest.class, NodeTest.class,
                SingleDecreePaxosTest.class, MultiSlotPaxosTest.class, ReplicationConvergenceTest.class);
    }
}
