package paxossim.message;

import paxossim.core.Ballot;
import paxossim.core.Command;

/**
 * Phase 2b: an acceptor's confirmation that it accepted {@code value} under
 * {@code ballot} for {@code slot}.
 */
public record Accepted(Ballot ballot, int slot, Command value) implements Message {
}
