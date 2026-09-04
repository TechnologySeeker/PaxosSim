package paxossim.core;

/**
 * Per-slot bookkeeping an acceptor keeps for a single Paxos instance: the
 * highest ballot it has promised not to ignore, and the highest ballot/value
 * pair it has actually accepted (if any). This is the state that Phase 1
 * (promise) and Phase 2 (accept) read and update.
 */
public final class AcceptorState {

    private Ballot promisedBallot;
    private Ballot acceptedBallot;
    private Command acceptedValue;

    public AcceptorState() {
        this.promisedBallot = Ballot.NONE;
        this.acceptedBallot = Ballot.NONE;
        this.acceptedValue = null;
    }

    public Ballot promisedBallot() {
        return promisedBallot;
    }

    public Ballot acceptedBallot() {
        return acceptedBallot;
    }

    public Command acceptedValue() {
        return acceptedValue;
    }

    public boolean hasAccepted() {
        return acceptedValue != null;
    }

    /**
     * Records a promise for {@code ballot}, provided it is strictly higher
     * than any ballot already promised. Returns whether the promise was
     * recorded.
     */
    public boolean promise(Ballot ballot) {
        if (ballot.compareTo(promisedBallot) <= 0) {
            return false;
        }
        promisedBallot = ballot;
        return true;
    }

    /**
     * Records an accepted value for {@code ballot}, provided it is not lower
     * than any ballot already promised. Returns whether the value was
     * accepted.
     */
    public boolean accept(Ballot ballot, Command value) {
        if (ballot.compareTo(promisedBallot) < 0) {
            return false;
        }
        promisedBallot = ballot;
        acceptedBallot = ballot;
        acceptedValue = value;
        return true;
    }
}
