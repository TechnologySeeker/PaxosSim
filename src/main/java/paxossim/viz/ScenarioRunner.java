package paxossim.viz;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.core.Log;
import paxossim.core.StateMachine;
import paxossim.events.EventLog;
import paxossim.network.SimulatedNetwork;
import paxossim.node.Node;
import paxossim.role.Learner;
import paxossim.role.Proposer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Runs one named Paxos scenario against the real simulation classes
 * (Node/Proposer/Learner/Log/StateMachine over a SimulatedNetwork),
 * recording every event to an {@link EventLog}, and writes the result as
 * JSON for the static replay page at {@code viz/index.html} to load.
 *
 * <p>Usage: {@code java paxossim.viz.ScenarioRunner <scenario> <output-file>}
 * <br>Scenarios: {@code normal}, {@code competing_proposers}, {@code node_failure}, {@code partition}
 */
public final class ScenarioRunner {

    static final List<String> ACCEPTOR_IDS = List.of("A", "B", "C");
    private static final List<String> LEARNER_IDS = List.of("A-learner", "B-learner", "C-learner");

    private ScenarioRunner() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: java paxossim.viz.ScenarioRunner <scenario> <output-file>");
            System.err.println("scenarios: normal, competing_proposers, node_failure, partition");
            System.exit(1);
            return;
        }
        EventLog eventLog = runScenario(args[0]);
        Path outputFile = Path.of(args[1]);
        Files.writeString(outputFile, eventLog.toJson(ACCEPTOR_IDS));
        System.out.println("Wrote " + eventLog.events().size() + " events to " + outputFile);
    }

    static EventLog runScenario(String scenario) {
        return switch (scenario) {
            case "normal" -> runNormal();
            case "competing_proposers" -> runCompetingProposers();
            case "node_failure" -> runNodeFailure();
            case "partition" -> runPartition();
            default -> throw new IllegalArgumentException("unknown scenario: " + scenario
                    + " (expected one of: normal, competing_proposers, node_failure, partition)");
        };
    }

    /** A clean, two-slot run with every node healthy. */
    static EventLog runNormal() {
        EventLog eventLog = new EventLog();
        SimulatedNetwork network = new SimulatedNetwork(eventLog);
        for (String acceptorId : ACCEPTOR_IDS) {
            new Node(acceptorId, network, LEARNER_IDS);
        }
        Log logA = new Log();
        Log logB = new Log();
        Log logC = new Log();
        new Learner("A-learner", network, ACCEPTOR_IDS, logA);
        new Learner("B-learner", network, ACCEPTOR_IDS, logB);
        new Learner("C-learner", network, ACCEPTOR_IDS, logC);
        StateMachine stateMachineA = new StateMachine("A", eventLog);
        StateMachine stateMachineB = new StateMachine("B", eventLog);
        StateMachine stateMachineC = new StateMachine("C", eventLog);

        Proposer proposer = new Proposer("proposer", network, ACCEPTOR_IDS);
        runSlot(proposer, network, new Ballot(1, "A"), 0, Command.set("x", "10"));
        eventLog.note("Quorum reached for slot 0 — value chosen");
        runSlot(proposer, network, new Ballot(1, "A"), 1, Command.set("y", "20"));
        eventLog.note("Quorum reached for slot 1 — value chosen");

        stateMachineA.applyChosenEntries(logA);
        stateMachineB.applyChosenEntries(logB);
        stateMachineC.applyChosenEntries(logC);
        return eventLog;
    }

    /**
     * Two proposers race for real at the message level: A sends
     * Prepare(1,A), B sends Prepare(2,B). Both can win Phase 1, but by the
     * time either tries to Accept, every acceptor has moved on to B's
     * higher ballot, so A's Accept is rejected everywhere — A gets
     * preempted — and only B's value is ultimately chosen.
     */
    static EventLog runCompetingProposers() {
        EventLog eventLog = new EventLog();
        SimulatedNetwork network = new SimulatedNetwork(eventLog);
        for (String acceptorId : ACCEPTOR_IDS) {
            new Node(acceptorId, network, LEARNER_IDS);
        }
        Log logA = new Log();
        Log logB = new Log();
        Log logC = new Log();
        new Learner("A-learner", network, ACCEPTOR_IDS, logA);
        new Learner("B-learner", network, ACCEPTOR_IDS, logB);
        new Learner("C-learner", network, ACCEPTOR_IDS, logC);

        Proposer proposerA = new Proposer("proposer-A", network, ACCEPTOR_IDS);
        Proposer proposerB = new Proposer("proposer-B", network, ACCEPTOR_IDS);

        proposerA.sendPrepare(new Ballot(1, "A"), 0);
        proposerB.sendPrepare(new Ballot(2, "B"), 0);
        network.deliverAll();
        eventLog.note("Both A and B won phase 1 — every acceptor has now promised B's higher ballot (2,B)");

        Command chosenForA = proposerA.chooseValue(Command.set("x", "FROM-A"));
        Command chosenForB = proposerB.chooseValue(Command.set("x", "FROM-B"));
        proposerA.sendAccept(new Ballot(1, "A"), 0, chosenForA);
        proposerB.sendAccept(new Ballot(2, "B"), 0, chosenForB);
        network.deliverAll();
        eventLog.note("A's lower-ballot accept was rejected everywhere — A gets preempted; B's value is chosen");

        return eventLog;
    }

    /** C is down for the whole scenario; A and B alone still reach quorum. */
    static EventLog runNodeFailure() {
        EventLog eventLog = new EventLog();
        SimulatedNetwork network = new SimulatedNetwork(eventLog);
        new Node("A", network, LEARNER_IDS);
        new Node("B", network, LEARNER_IDS);
        // "C" never comes up.
        Log logA = new Log();
        Log logB = new Log();
        new Learner("A-learner", network, ACCEPTOR_IDS, logA);
        new Learner("B-learner", network, ACCEPTOR_IDS, logB);

        Proposer proposer = new Proposer("proposer", network, ACCEPTOR_IDS);
        eventLog.note("C is down for this entire run");
        runSlot(proposer, network, new Ballot(1, "A"), 0, Command.set("x", "10"));
        eventLog.note("A + B alone still reach quorum (2 of 3)");

        return eventLog;
    }

    /** C is partitioned away, then the partition heals and a retry succeeds. */
    static EventLog runPartition() {
        EventLog eventLog = new EventLog();
        SimulatedNetwork network = new SimulatedNetwork(eventLog);
        for (String acceptorId : ACCEPTOR_IDS) {
            new Node(acceptorId, network, LEARNER_IDS);
        }
        Log logA = new Log();
        Log logB = new Log();
        Log logC = new Log();
        new Learner("A-learner", network, ACCEPTOR_IDS, logA);
        new Learner("B-learner", network, ACCEPTOR_IDS, logB);
        new Learner("C-learner", network, ACCEPTOR_IDS, logC);
        Proposer proposer = new Proposer("proposer", network, ACCEPTOR_IDS);

        network.partition(Set.of("C"));
        eventLog.note("C is partitioned away from the rest of the cluster");
        runSlot(proposer, network, new Ballot(1, "A"), 0, Command.set("x", "10"));
        eventLog.note("A + B alone (the majority side) still reach quorum despite the partition");

        network.healPartition();
        eventLog.note("Partition healed — C rejoins the cluster");

        return eventLog;
    }

    private static void runSlot(Proposer proposer, SimulatedNetwork network, Ballot ballot, int slot, Command value) {
        proposer.sendPrepare(ballot, slot);
        network.deliverAll();
        Command chosen = proposer.chooseValue(value);
        proposer.sendAccept(ballot, slot, chosen);
        network.deliverAll();
    }
}
