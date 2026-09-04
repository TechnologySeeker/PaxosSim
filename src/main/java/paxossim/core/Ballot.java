package paxossim.core;

import java.util.Objects;

/**
 * A Paxos ballot number: (round, proposerId). Ballots order primarily by
 * round, then by proposerId, so that ties between proposers competing in the
 * same round are broken deterministically. Every Phase 1/2 comparison in the
 * simulation is expressed in terms of this ordering.
 */
public final class Ballot implements Comparable<Ballot> {

    /** Sentinel below every real ballot; an acceptor's initial promise. */
    public static final Ballot NONE = new Ballot(0, "");

    private final int round;
    private final String proposerId;

    public Ballot(int round, String proposerId) {
        if (round < 0) {
            throw new IllegalArgumentException("round must be >= 0");
        }
        this.round = round;
        this.proposerId = Objects.requireNonNull(proposerId, "proposerId");
    }

    public int round() {
        return round;
    }

    public String proposerId() {
        return proposerId;
    }

    public Ballot nextRound() {
        return new Ballot(round + 1, proposerId);
    }

    @Override
    public int compareTo(Ballot other) {
        int cmp = Integer.compare(this.round, other.round);
        if (cmp != 0) {
            return cmp;
        }
        return this.proposerId.compareTo(other.proposerId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ballot other)) {
            return false;
        }
        return round == other.round && proposerId.equals(other.proposerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(round, proposerId);
    }

    @Override
    public String toString() {
        return "(" + round + "," + proposerId + ")";
    }
}
