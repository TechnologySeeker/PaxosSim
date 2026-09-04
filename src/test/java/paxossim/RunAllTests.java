package paxossim;

import paxossim.core.BallotTest;
import paxossim.core.LogTest;
import paxossim.core.StateMachineTest;
import paxossim.events.EventLogTest;
import paxossim.events.MessageEventsTest;
import paxossim.integration.FailureScenarioTest;
import paxossim.integration.MultiSlotPaxosTest;
import paxossim.integration.ReplicationConvergenceTest;
import paxossim.integration.SafetyInvariantsTest;
import paxossim.integration.SingleDecreePaxosTest;
import paxossim.network.SimulatedNetworkTest;
import paxossim.node.NodeTest;
import paxossim.role.AcceptorTest;
import paxossim.role.LearnerTest;
import paxossim.role.ProposerTest;
import paxossim.testing.PaxosInvariantsTest;
import paxossim.testing.TestRunner;
import paxossim.viz.ScenarioRunnerTest;

/** Entry point that runs every test class until a build tool takes over. */
public final class RunAllTests {

    private RunAllTests() {}

    public static void main(String[] args) {
        TestRunner.run(BallotTest.class, LogTest.class, StateMachineTest.class, AcceptorTest.class,
                ProposerTest.class, LearnerTest.class, SimulatedNetworkTest.class, NodeTest.class,
                PaxosInvariantsTest.class, EventLogTest.class, MessageEventsTest.class,
                SingleDecreePaxosTest.class, MultiSlotPaxosTest.class, ReplicationConvergenceTest.class,
                FailureScenarioTest.class, SafetyInvariantsTest.class, ScenarioRunnerTest.class);
    }
}
