package paxossim.message;

import paxossim.core.Ballot;

/**
 * Optional rejection reply: an acceptor refused {@code ballot} because it
 * had already promised {@code promisedBallot}, letting the proposer retry
 * with a higher round immediately instead of waiting on a timeout.
 */
public record Nack(Ballot ballot, int slot, Ballot promisedBallot) implements Message {
}
