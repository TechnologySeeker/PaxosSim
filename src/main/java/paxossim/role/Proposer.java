package paxossim.role;

import paxossim.core.Ballot;
import paxossim.core.Command;
import paxossim.message.AcceptRequest;
import paxossim.message.Accepted;
import paxossim.message.Message;
import paxossim.message.Nack;
import paxossim.message.Prepare;
import paxossim.message.Promise;
import paxossim.network.SimulatedNetwork;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The proposer role, Phase 1 (Prepare): broadcasts a {@link Prepare} to
 * every acceptor in the cluster and collects the {@link Promise} replies,
 * so the proposer can tell once a majority quorum has promised and it's
 * safe to move on to Phase 2. A majority is more than half of the full
 * cluster ({@code acceptorIds}), not just of whoever has replied so far —
 * that's what lets the proposer keep making progress with one acceptor
 * down.
 */
public final class Proposer {

    private final String id;
    private final SimulatedNetwork network;
    private final List<String> acceptorIds;
    private final Map<String, Promise> promises = new HashMap<>();
    private final Set<String> nackedBy = new HashSet<>();

    public Proposer(String id, SimulatedNetwork network, List<String> acceptorIds) {
        this.id = Objects.requireNonNull(id, "id");
        this.network = Objects.requireNonNull(network, "network");
        this.acceptorIds = List.copyOf(acceptorIds);
        network.register(id, this::onMessage);
    }

    /** Broadcasts a Prepare(ballot, slot) to every acceptor, resetting any previously tracked replies. */
    public void sendPrepare(Ballot ballot, int slot) {
        promises.clear();
        nackedBy.clear();
        for (String acceptorId : acceptorIds) {
            network.send(id, acceptorId, new Prepare(ballot, slot));
        }
    }

    /** The Promise replies collected so far, keyed by the acceptor that sent them. */
    public Map<String, Promise> promises() {
        return Map.copyOf(promises);
    }

    /** How many distinct acceptors have promised. */
    public int promiseCount() {
        return promises.size();
    }

    /** How many distinct acceptors have nacked. */
    public int nackCount() {
        return nackedBy.size();
    }

    /** Whether a majority of the full cluster ({@code acceptorIds}) has promised. */
    public boolean hasQuorum() {
        return promises.size() > acceptorIds.size() / 2;
    }

    /**
     * The Paxos safety rule for Phase 2: if any collected promise carries a
     * previously accepted value, the proposer must re-propose the value
     * belonging to the <em>highest</em> such accepted ballot — never its own
     * original value — since some earlier round may have already gotten
     * that value accepted by a quorum. Only when no promise carries an
     * accepted value at all is the proposer free to propose
     * {@code originalValue}.
     */
    public Command chooseValue(Command originalValue) {
        Command chosen = originalValue;
        Ballot highestAccepted = Ballot.NONE;
        for (Promise promise : promises.values()) {
            if (promise.acceptedValue() != null && promise.acceptedBallot().compareTo(highestAccepted) > 0) {
                highestAccepted = promise.acceptedBallot();
                chosen = promise.acceptedValue();
            }
        }
        return chosen;
    }

    private void onMessage(String from, Message message) {
        switch (message) {
            case Promise promise -> promises.put(from, promise);
            case Nack nack -> nackedBy.add(from);
            case Prepare prepare -> throw unexpected(message);
            case AcceptRequest request -> throw unexpected(message);
            case Accepted accepted -> throw unexpected(message);
        }
    }

    private IllegalArgumentException unexpected(Message message) {
        return new IllegalArgumentException("proposer " + id + " has no Phase 2 logic yet to handle " + message);
    }
}
