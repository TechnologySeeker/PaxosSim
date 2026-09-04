package paxossim.node;

import paxossim.message.AcceptRequest;
import paxossim.message.Accepted;
import paxossim.message.Message;
import paxossim.message.Nack;
import paxossim.message.Prepare;
import paxossim.message.Promise;
import paxossim.network.SimulatedNetwork;
import paxossim.role.Acceptor;

import java.util.List;
import java.util.Objects;

/**
 * A cluster member, addressed by {@code id} on a {@link SimulatedNetwork}.
 * Every node plays all three Paxos roles (proposer, acceptor, learner); for
 * now this wraps only the acceptor role, since proposer logic lives in its
 * own standalone {@link paxossim.role.Proposer} and learner logic in
 * {@link paxossim.role.Learner}. A node registers itself with the network on
 * construction so that any message addressed to its id is handled and
 * replied to automatically; whenever it accepts a value, it also broadcasts
 * that {@link Accepted} to every registered learner id, mirroring how real
 * acceptors notify learners directly rather than only the requesting
 * proposer.
 */
public final class Node {

    private final String id;
    private final SimulatedNetwork network;
    private final List<String> learnerIds;
    private final Acceptor acceptor = new Acceptor();

    public Node(String id, SimulatedNetwork network) {
        this(id, network, List.of());
    }

    public Node(String id, SimulatedNetwork network, List<String> learnerIds) {
        this.id = Objects.requireNonNull(id, "id");
        this.network = Objects.requireNonNull(network, "network");
        this.learnerIds = List.copyOf(learnerIds);
        network.register(id, this::onMessage);
    }

    public String id() {
        return id;
    }

    private void onMessage(String from, Message message) {
        Message reply = handle(message);
        recordReplyEvent(reply);
        network.send(id, from, reply);
        if (reply instanceof Accepted accepted) {
            for (String learnerId : learnerIds) {
                network.send(id, learnerId, accepted);
            }
        }
    }

    private void recordReplyEvent(Message reply) {
        switch (reply) {
            case Promise promise -> network.eventLog().promise(id, promise.ballot().toString(), promise.slot(),
                    "promised " + promise.ballot());
            case Accepted accepted -> network.eventLog().accepted(id, accepted.ballot().toString(), accepted.slot(),
                    String.valueOf(accepted.value()), "accepted " + accepted.value() + " under " + accepted.ballot());
            case Nack nack -> network.eventLog().nack(id, nack.ballot().toString(), nack.slot(),
                    "rejected " + nack.ballot() + " (already promised " + nack.promisedBallot() + ")");
            default -> throw new IllegalStateException("an acceptor's reply should only ever be Promise/Accepted/Nack, got " + reply);
        }
    }

    Message handle(Message message) {
        return switch (message) {
            case Prepare prepare -> acceptor.onPrepare(prepare);
            case AcceptRequest request -> acceptor.onAccept(request);
            default -> throw new IllegalArgumentException(
                    "node " + id + " has no proposer/learner logic yet to handle " + message);
        };
    }
}
