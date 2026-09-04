package paxossim.role;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.core.Log;
import paxossim.events.EventLog;
import paxossim.message.Accepted;
import paxossim.message.Message;
import paxossim.network.SimulatedNetwork;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The learner role: a passive observer that watches {@link Accepted}
 * replies broadcast by acceptors and, once a majority of the full cluster
 * ({@code acceptorIds}) has accepted the <em>same ballot</em> for a slot,
 * marks that slot's value as chosen in {@code log}. Unlike a
 * {@link Proposer}, a learner never sends anything itself — it only ever
 * learns what already happened.
 */
public final class Learner {

    private final String id;
    private final int clusterSize;
    private final Log log;
    private final EventLog eventLog;

    // slot -> ballot -> ids of acceptors that accepted that ballot for that slot
    private final Map<Integer, Map<Ballot, Set<String>>> acceptedBy = new HashMap<>();

    public Learner(String id, SimulatedNetwork network, List<String> acceptorIds, Log log) {
        this.id = Objects.requireNonNull(id, "id");
        this.clusterSize = acceptorIds.size();
        this.log = Objects.requireNonNull(log, "log");
        this.eventLog = network.eventLog();
        network.register(id, this::onMessage);
    }

    private void onMessage(String from, Message message) {
        if (!(message instanceof Accepted accepted)) {
            throw new IllegalArgumentException("learner " + id + " only understands Accepted, got " + message);
        }
        record(from, accepted);
    }

    private void record(String from, Accepted accepted) {
        int slot = accepted.slot();
        Ballot ballot = accepted.ballot();

        Set<String> acceptorsForBallot = acceptedBy
                .computeIfAbsent(slot, s -> new HashMap<>())
                .computeIfAbsent(ballot, b -> new HashSet<>());
        acceptorsForBallot.add(from);

        if (!log.isChosen(slot) && acceptorsForBallot.size() > clusterSize / 2) {
            log.recordChosen(slot, ballot, accepted.value());
            eventLog.chosen(slot, String.valueOf(accepted.value()), "SLOT " + slot + " CHOSEN: " + accepted.value());
        }
    }
}
