package paxossim;

import paxossim.core.BallotTest;
import paxossim.network.SimulatedNetworkTest;
import paxossim.role.AcceptorTest;
import paxossim.testing.TestRunner;

/** Entry point that runs every test class until a build tool takes over. */
public final class RunAllTests {

    private RunAllTests() {}

    public static void main(String[] args) {
        TestRunner.run(BallotTest.class, AcceptorTest.class, SimulatedNetworkTest.class);
    }
}
