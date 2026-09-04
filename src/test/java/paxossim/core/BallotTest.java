package paxossim.core;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class BallotTest {

    public void testLowerRoundIsLessThanHigherRound() {
        Ballot oneA = new Ballot(1, "A");
        Ballot twoA = new Ballot(2, "A");
        assertTrue(oneA.compareTo(twoA) < 0, "(1,A) should be < (2,A)");
    }

    public void testSameRoundOrdersByProposerId() {
        Ballot twoA = new Ballot(2, "A");
        Ballot twoB = new Ballot(2, "B");
        assertTrue(twoA.compareTo(twoB) < 0, "(2,A) should be < (2,B)");
    }

    public void testFullOrderingAcrossRoundAndProposer() {
        Ballot oneA = new Ballot(1, "A");
        Ballot twoA = new Ballot(2, "A");
        Ballot twoB = new Ballot(2, "B");
        assertTrue(oneA.compareTo(twoA) < 0, "(1,A) < (2,A)");
        assertTrue(twoA.compareTo(twoB) < 0, "(2,A) < (2,B)");
        assertTrue(oneA.compareTo(twoB) < 0, "(1,A) < (2,B)");
    }

    public void testEqualBallotsCompareToZeroAndAreEqual() {
        Ballot a = new Ballot(2, "A");
        Ballot b = new Ballot(2, "A");
        assertEquals(0, a.compareTo(b), "equal ballots should compare to 0");
        assertEquals(a, b, "equal ballots should be .equals()");
    }

    public void testCompareToIsAntiSymmetric() {
        Ballot oneA = new Ballot(1, "A");
        Ballot twoB = new Ballot(2, "B");
        assertTrue(oneA.compareTo(twoB) < 0, "(1,A) < (2,B)");
        assertTrue(twoB.compareTo(oneA) > 0, "(2,B) > (1,A)");
    }

    public void testNextRoundIncrementsRoundAndKeepsProposer() {
        Ballot oneA = new Ballot(1, "A");
        Ballot next = oneA.nextRound();
        assertEquals(2, next.round(), "nextRound should increment round");
        assertEquals("A", next.proposerId(), "nextRound should keep the proposerId");
    }

    public void testNoneIsLowerThanAnyRealBallot() {
        Ballot oneA = new Ballot(1, "A");
        assertTrue(Ballot.NONE.compareTo(oneA) < 0, "NONE should be < any real ballot");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(BallotTest.class);
    }
}
