package paxossim.message;

import paxossim.core.Ballot;
import paxossim.core.Command;

/**
 * Phase 1b: an acceptor's reply to a {@link Prepare}, carrying the highest
 * ballot/value it had already accepted for this slot, if any, so the
 * proposer can adopt it. {@code acceptedBallot} is {@link Ballot#NONE} and
 * {@code acceptedValue} is null when nothing has been accepted yet.
 */
public record Promise(Ballot ballot, int slot, Ballot acceptedBallot, Command acceptedValue) implements Message {
}
