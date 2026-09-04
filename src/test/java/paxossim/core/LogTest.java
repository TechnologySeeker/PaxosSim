package paxossim.core;

import java.util.List;
import java.util.Optional;

import static paxossim.testing.Assertions.assertEquals;
import static paxossim.testing.Assertions.assertTrue;

public class LogTest {

    public void testRecordChosenStoresAChosenEntry() {
        Log log = new Log();
        Command value = Command.set("x", "10");

        log.recordChosen(0, new Ballot(1, "A"), value);

        assertTrue(log.isChosen(0), "a recorded slot should be chosen");
        assertEquals(Optional.of(value), log.chosenValue(0), "chosenValue should return the recorded command");
    }

    public void testUnknownSlotIsNotChosen() {
        Log log = new Log();

        assertTrue(!log.isChosen(0), "an untouched slot should not be chosen");
        assertEquals(Optional.empty(), log.chosenValue(0), "an untouched slot should have no chosen value");
        assertEquals(Optional.empty(), log.get(0), "an untouched slot should have no entry");
    }

    public void testSlotsAreIndependent() {
        Log log = new Log();
        Command valueForSlot0 = Command.set("x", "10");
        Command valueForSlot1 = Command.set("y", "20");

        log.recordChosen(0, new Ballot(1, "A"), valueForSlot0);
        log.recordChosen(1, new Ballot(1, "A"), valueForSlot1);

        assertEquals(Optional.of(valueForSlot0), log.chosenValue(0), "slot 0 should keep its own value");
        assertEquals(Optional.of(valueForSlot1), log.chosenValue(1), "slot 1 should keep its own value");
    }

    public void testEntriesAreOrderedBySlotRegardlessOfInsertionOrder() {
        Log log = new Log();
        log.recordChosen(2, new Ballot(1, "A"), Command.set("z", "3"));
        log.recordChosen(0, new Ballot(1, "A"), Command.set("x", "1"));
        log.recordChosen(1, new Ballot(1, "A"), Command.set("y", "2"));

        List<Integer> slotsInOrder = log.entries().stream().map(LogEntry::slot).toList();

        assertEquals(List.of(0, 1, 2), slotsInOrder, "entries() should be ordered by slot regardless of insertion order");
    }

    public void testRecordChosenOverwritesAPreviousEntryForTheSameSlot() {
        Log log = new Log();
        log.recordChosen(0, new Ballot(1, "A"), Command.set("x", "old"));

        log.recordChosen(0, new Ballot(2, "B"), Command.set("x", "new"));

        assertEquals(1, log.size(), "re-recording the same slot should not grow the log");
        assertEquals(Optional.of(Command.set("x", "new")), log.chosenValue(0), "the latest recorded value should win");
    }

    public static void main(String[] args) {
        paxossim.testing.TestRunner.run(LogTest.class);
    }
}
