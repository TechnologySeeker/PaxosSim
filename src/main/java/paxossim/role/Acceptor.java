package paxossim.role;

import paxossim.core.AcceptorState;
import paxossim.core.Ballot;
import paxossim.message.AcceptRequest;
import paxossim.message.Accepted;
import paxossim.message.Message;
import paxossim.message.Nack;
import paxossim.message.Prepare;
import paxossim.message.Promise;

import java.util.HashMap;
import java.util.Map;

/**
 * The acceptor role: keeps one {@link AcceptorState} per log slot and
 * answers Prepare/AcceptRequest messages against it, per single-decree
 * Paxos. One instance handles every slot for a single node.
 */
public final class Acceptor {

    private final Map<Integer, AcceptorState> statesBySlot = new HashMap<>();

    /**
     * Phase 1b: promise not to accept ballots below {@code prepare.ballot()}
     * when it's higher than anything already promised for this slot,
     * carrying along the highest ballot/value already accepted (if any) so
     * the proposer can adopt it. Otherwise reject with a {@link Nack}
     * reporting the higher ballot already promised.
     */
    public Message onPrepare(Prepare prepare) {
        AcceptorState state = stateFor(prepare.slot());
        Ballot ballot = prepare.ballot();

        if (!state.promise(ballot)) {
            return new Nack(ballot, prepare.slot(), state.promisedBallot());
        }

        return new Promise(ballot, prepare.slot(), state.acceptedBallot(), state.acceptedValue());
    }

    /**
     * Phase 2b: accept {@code request.value()} under {@code request.ballot()}
     * when it's not lower than anything already promised for this slot.
     * Otherwise reject with a {@link Nack} reporting the higher ballot
     * already promised.
     */
    public Message onAccept(AcceptRequest request) {
        AcceptorState state = stateFor(request.slot());
        Ballot ballot = request.ballot();

        if (!state.accept(ballot, request.value())) {
            return new Nack(ballot, request.slot(), state.promisedBallot());
        }

        return new Accepted(ballot, request.slot(), request.value());
    }

    private AcceptorState stateFor(int slot) {
        return statesBySlot.computeIfAbsent(slot, s -> new AcceptorState());
    }
}
