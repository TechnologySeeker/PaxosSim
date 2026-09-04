package paxossim.message;

import paxossim.core.Ballot;

/**
 * Phase 1a: a proposer asks acceptors to promise not to accept any ballot
 * lower than {@code ballot} for {@code slot}.
 */
public record Prepare(Ballot ballot, int slot) implements Message {
}
