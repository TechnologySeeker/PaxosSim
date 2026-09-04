package paxossim.core;

import paxossim.events.Event;
import paxossim.events.EventLog;

import java.util.Map;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class StateMachineTest {

    public void testNoArgConstructorRecordsNoEventsEvenWhenApplying() {
        Log log = new Log();
        log.recordChosen(0, new Ballot(1, "P"), Command.set("x", "1"));
        StateMachine stateMachine = new StateMachine();

        stateMachine.applyChosenEntries(log); // must not throw despite no event log attached

        assertEquals("1", stateMachine.get("x"), "applying should still work with no event log attached");
    }

    public void testEventLogConstructorRecordsAStateChangeEventPerAppliedCommand() {
        Log log = new Log();
        Command x = Command.set("x", "1");
        Command y = Command.set("y", "2");
        log.recordChosen(0, new Ballot(1, "P"), x);
        log.recordChosen(1, new Ballot(1, "P"), y);
        EventLog eventLog = new EventLog();
        StateMachine stateMachine = new StateMachine("A", eventLog);

        stateMachine.applyChosenEntries(log);

        assertEquals(2, eventLog.events().size(), "one STATE_CHANGE event should be recorded per applied command");
        Event first = eventLog.events().get(0);
        assertEquals("STATE_CHANGE", first.type(), "type should be STATE_CHANGE");
        assertEquals("A", first.toNode(), "the node label should be recorded");
        assertEquals(Integer.valueOf(0), first.slot(), "the slot should be recorded");
        assertEquals(x.toString(), first.value(), "the applied command should be recorded");
    }

    public void testAppliesChosenEntriesInSlotOrder() {
        Log log = new Log();
        log.recordChosen(0, new Ballot(1, "P"), Command.set("x", "1"));
        log.recordChosen(1, new Ballot(1, "P"), Command.set("y", "2"));
        StateMachine stateMachine = new StateMachine();

        stateMachine.applyChosenEntries(log);

        assertEquals("1", stateMachine.get("x"), "x should be set from slot 0");
        assertEquals("2", stateMachine.get("y"), "y should be set from slot 1");
        assertEquals(2, stateMachine.appliedSlotCount(), "both slots should have been applied");
    }

    public void testDeleteRemovesTheKey() {
        Log log = new Log();
        log.recordChosen(0, new Ballot(1, "P"), Command.set("x", "1"));
        log.recordChosen(1, new Ballot(1, "P"), Command.delete("x"));
        StateMachine stateMachine = new StateMachine();

        stateMachine.applyChosenEntries(log);

        assertTrue(stateMachine.get("x") == null, "x should have been deleted by slot 1");
    }

    public void testHoldsBackApplyingWhenThereIsAGapBeforeTheNextSlot() {
        Log log = new Log();
        // Slot 1 is chosen but slot 0 is not yet — out-of-order arrival.
        log.recordChosen(1, new Ballot(1, "P"), Command.set("y", "2"));
        StateMachine stateMachine = new StateMachine();

        stateMachine.applyChosenEntries(log);

        assertEquals(0, stateMachine.appliedSlotCount(), "nothing should apply while slot 0 is still missing");
        assertTrue(stateMachine.get("y") == null, "slot 1's command must not be applied ahead of slot 0");
    }

    public void testCatchesUpInOrderOnceTheGapIsFilled() {
        Log log = new Log();
        log.recordChosen(1, new Ballot(1, "P"), Command.set("y", "2"));
        StateMachine stateMachine = new StateMachine();
        stateMachine.applyChosenEntries(log);
        assertEquals(0, stateMachine.appliedSlotCount(), "slot 1 should still be held back before slot 0 arrives");

        log.recordChosen(0, new Ballot(1, "P"), Command.set("x", "1"));
        stateMachine.applyChosenEntries(log);

        assertEquals(2, stateMachine.appliedSlotCount(), "both slots should now be applied");
        assertEquals("1", stateMachine.get("x"), "slot 0's command should now be applied");
        assertEquals("2", stateMachine.get("y"), "slot 1's command should now be applied too, in order");
    }

    public void testOutOfOrderArrivalStillAppliesInSlotOrderNotArrivalOrder() {
        Log log = new Log();
        StateMachine stateMachine = new StateMachine();

        // Arrival order: slot 2, then slot 0, then slot 1 — same key, so the
        // final value would differ depending on application order.
        log.recordChosen(2, new Ballot(1, "P"), Command.set("x", "from-slot-2"));
        stateMachine.applyChosenEntries(log);
        assertEquals(0, stateMachine.appliedSlotCount(), "slot 2 should be held back with slots 0 and 1 missing");

        log.recordChosen(0, new Ballot(1, "P"), Command.set("x", "from-slot-0"));
        stateMachine.applyChosenEntries(log);
        assertEquals(1, stateMachine.appliedSlotCount(), "only slot 0 should apply while slot 1 is still missing");

        log.recordChosen(1, new Ballot(1, "P"), Command.set("x", "from-slot-1"));
        stateMachine.applyChosenEntries(log);

        assertEquals(3, stateMachine.appliedSlotCount(), "all three slots should now be applied");
        assertEquals("from-slot-2", stateMachine.get("x"), "the final value must be slot 2's, applied last in slot order");
    }

    public void testApplyChosenEntriesIsSafeToCallAgainWithNothingNew() {
        Log log = new Log();
        log.recordChosen(0, new Ballot(1, "P"), Command.set("x", "1"));
        StateMachine stateMachine = new StateMachine();
        stateMachine.applyChosenEntries(log);

        stateMachine.applyChosenEntries(log);

        assertEquals(1, stateMachine.appliedSlotCount(), "calling again with nothing new chosen should be a no-op");
    }

    public void testSnapshotReflectsCurrentState() {
        Log log = new Log();
        log.recordChosen(0, new Ballot(1, "P"), Command.set("x", "1"));
        log.recordChosen(1, new Ballot(1, "P"), Command.set("y", "2"));
        StateMachine stateMachine = new StateMachine();

        stateMachine.applyChosenEntries(log);

        assertEquals(Map.of("x", "1", "y", "2"), stateMachine.snapshot(), "the snapshot should reflect every applied command");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(StateMachineTest.class);
    }
}
