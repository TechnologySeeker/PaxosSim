package paxossim.node;

import paxossim.message.AcceptRequest;
import paxossim.message.Message;
import paxossim.message.Prepare;
import paxossim.network.SimulatedNetwork;
import paxossim.role.Acceptor;

import java.util.Objects;

/**
 * A cluster member, addressed by {@code id} on a {@link SimulatedNetwork}.
 * Every node plays all three Paxos roles (proposer, acceptor, learner); for
 * now this wraps only the acceptor role, since proposer and learner logic
 * don't exist yet. A node registers itself with the network on construction
 * so that any message addressed to its id is handled and replied to
 * automatically.
 */
public final class Node {

    private final String id;
    private final SimulatedNetwork network;
    private final Acceptor acceptor = new Acceptor();

    public Node(String id, SimulatedNetwork network) {
        this.id = Objects.requireNonNull(id, "id");
        this.network = Objects.requireNonNull(network, "network");
        network.register(id, this::onMessage);
    }

    public String id() {
        return id;
    }

    private void onMessage(String from, Message message) {
        network.send(id, from, handle(message));
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
