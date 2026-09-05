package paxossim.viz;

import paxossim.events.Event;
import paxossim.events.EventLog;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class ScenarioRunnerTest {

    public void testRunNormalChoosesAValueForBothSlots() {
        EventLog eventLog = ScenarioRunner.runNormal();

        // 3 independent learners each emit their own CHOSEN event once they
        // individually reach quorum, so what matters is that they all agree
        // on the same value per slot, not the raw count of CHOSEN events.
        assertEquals(Set.of("SET x 10"), distinctValuesForSlot(eventLog, 0), "every learner should agree on slot 0's value");
        assertEquals(Set.of("SET y 20"), distinctValuesForSlot(eventLog, 1), "every learner should agree on slot 1's value");

        assertEquals(6, eventsOfType(eventLog, "STATE_CHANGE").size(),
                "3 healthy replicas each applying 2 slots should record 6 STATE_CHANGE events");
    }

    public void testRunNormalAppliesStateSoonAfterEachSlotIsChosenNotBatchedAtTheEnd() {
        EventLog eventLog = ScenarioRunner.runNormal();

        int firstChosenSeq = eventsOfType(eventLog, "CHOSEN").get(0).seq();
        int firstStateChangeSeq = eventsOfType(eventLog, "STATE_CHANGE").get(0).seq();

        assertTrue(firstStateChangeSeq - firstChosenSeq < 20,
                "state should be applied soon after the first slot is chosen, not batched at the very end of the run");
    }

    public void testRunCompetingProposersEndsWithExactlyOneChosenValuePerSlot() {
        EventLog eventLog = ScenarioRunner.runCompetingProposers();

        assertEquals(Set.of("SET x FROM-B"), distinctValuesForSlot(eventLog, 0),
                "every learner should agree B's higher ballot is what got chosen, never A's");

        List<Event> nacks = eventsOfType(eventLog, "NACK");
        assertTrue(!nacks.isEmpty(), "A's preempted accept should have been nacked by at least one acceptor");

        assertHasStateChangeFor(eventLog, "A");
        assertHasStateChangeFor(eventLog, "B");
        assertHasStateChangeFor(eventLog, "C");
    }

    public void testRunNodeFailureNeverRecordsCRespondingToAnything() {
        EventLog eventLog = ScenarioRunner.runNodeFailure();

        boolean cEverResponded = eventLog.events().stream()
                .anyMatch(e -> (e.type().equals("PROMISE") || e.type().equals("ACCEPTED") || e.type().equals("NACK"))
                        && "C".equals(e.toNode()));
        assertTrue(!cEverResponded, "C should never be recorded as promising/accepting/nacking since it's down");

        boolean cWasSentSomething = eventLog.events().stream()
                .anyMatch(e -> e.type().equals("SEND") && "C".equals(e.toNode()));
        assertTrue(cWasSentSomething, "the proposer should still have tried to reach C");

        assertEquals(Set.of("SET x 10"), distinctValuesForSlot(eventLog, 0),
                "A and B alone should still reach quorum and agree on the chosen value");

        assertHasStateChangeFor(eventLog, "A");
        assertHasStateChangeFor(eventLog, "B");
        boolean cEverAppliedState = eventLog.events().stream()
                .anyMatch(e -> e.type().equals("STATE_CHANGE") && "C".equals(e.toNode()));
        assertTrue(!cEverAppliedState, "C should never apply any state since it's down for the whole run");
    }

    public void testRunPartitionChoosesAValueDespiteCBeingCutOff() {
        EventLog eventLog = ScenarioRunner.runPartition();

        assertEquals(Set.of("SET x 10"), distinctValuesForSlot(eventLog, 0),
                "the majority side should still choose a value, and every learner should agree on it");

        boolean somethingToCWasDropped = eventLog.events().stream()
                .anyMatch(e -> e.type().equals("DROPPED") && "C".equals(e.toNode()));
        assertTrue(somethingToCWasDropped, "at least one message to the partitioned-away C should have been dropped");

        assertHasStateChangeFor(eventLog, "A");
        assertHasStateChangeFor(eventLog, "B");
        assertHasStateChangeFor(eventLog, "C");
    }

    private static void assertHasStateChangeFor(EventLog eventLog, String nodeId) {
        boolean applied = eventLog.events().stream()
                .anyMatch(e -> e.type().equals("STATE_CHANGE") && nodeId.equals(e.toNode()));
        assertTrue(applied, "expected at least one STATE_CHANGE event for node " + nodeId);
    }

    private static Set<String> distinctValuesForSlot(EventLog eventLog, int slot) {
        return eventsOfType(eventLog, "CHOSEN").stream()
                .filter(e -> e.slot() != null && e.slot() == slot)
                .map(Event::value)
                .collect(Collectors.toSet());
    }

    public void testRunScenarioDispatchesEveryKnownScenarioWithoutError() {
        for (String scenario : List.of("normal", "competing_proposers", "node_failure", "partition")) {
            EventLog eventLog = ScenarioRunner.runScenario(scenario);
            assertTrue(!eventLog.events().isEmpty(), "scenario " + scenario + " should record at least one event");
        }
    }

    public void testRunScenarioRejectsAnUnknownName() {
        boolean threw = false;
        try {
            ScenarioRunner.runScenario("not_a_real_scenario");
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw, "an unknown scenario name should be rejected");
    }

    private static List<Event> eventsOfType(EventLog eventLog, String type) {
        return eventLog.events().stream().filter(e -> e.type().equals(type)).toList();
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(ScenarioRunnerTest.class);
    }
}
