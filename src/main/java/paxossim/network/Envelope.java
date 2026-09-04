package paxossim.network;

import paxossim.message.Message;

import java.util.Objects;

/**
 * One message in flight on a {@link SimulatedNetwork}: {@code message} sent
 * by {@code from}, addressed to {@code to}. Node ids are opaque strings
 * ("A", "B", "C", ...) chosen by whoever is wiring the simulation together.
 */
public record Envelope(String from, String to, Message message) {

    public Envelope {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(message, "message");
    }
}
