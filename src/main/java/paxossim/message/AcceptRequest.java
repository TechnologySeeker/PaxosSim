package paxossim.message;

import paxossim.core.Ballot;
import paxossim.core.Command;

/**
 * Phase 2a: a proposer asks acceptors to accept {@code value} under
 * {@code ballot} for {@code slot}.
 */
public record AcceptRequest(Ballot ballot, int slot, Command value) implements Message {
}
