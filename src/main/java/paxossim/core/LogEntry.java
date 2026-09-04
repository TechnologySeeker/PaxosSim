package paxossim.core;

import java.util.Objects;

/**
 * One slot of the replicated log: the command agreed on for that slot, the
 * ballot under which it was accepted, and whether a quorum has actually
 * chosen it yet (as opposed to merely accepted by one acceptor).
 */
public final class LogEntry {

    private final int slot;
    private final Ballot ballot;
    private final Command command;
    private boolean chosen;

    public LogEntry(int slot, Ballot ballot, Command command) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must be >= 0");
        }
        this.slot = slot;
        this.ballot = Objects.requireNonNull(ballot, "ballot");
        this.command = Objects.requireNonNull(command, "command");
        this.chosen = false;
    }

    public int slot() {
        return slot;
    }

    public Ballot ballot() {
        return ballot;
    }

    public Command command() {
        return command;
    }

    public boolean isChosen() {
        return chosen;
    }

    public void markChosen() {
        this.chosen = true;
    }

    @Override
    public String toString() {
        return "LogEntry{slot=" + slot + ", ballot=" + ballot + ", command=" + command
                + ", chosen=" + chosen + "}";
    }
}
