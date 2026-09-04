package paxossim.network;

import paxossim.message.Message;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An in-process, single-threaded stand-in for the network: a FIFO queue of
 * {@link Envelope}s that a test (or later, a driver loop) drains explicitly
 * via {@link #deliverNext()} / {@link #deliverAll()}. There are no threads,
 * sockets, or timers, so a whole run — including message loss and
 * reordering, once those are added — is fully deterministic and replayable.
 *
 * <p>Nodes may {@link #register} a handler for their id so that delivery
 * dispatches straight into their message-handling code (which can itself
 * call {@link #send} to reply); a recipient with no registered handler still
 * has its envelope dequeued and returned, just not acted on, so this class
 * doesn't need every role wired up to be independently testable.
 */
public final class SimulatedNetwork {

    /** Reacts to a message delivered to the node it was registered under. */
    @FunctionalInterface
    public interface MessageHandler {
        void onMessage(String from, Message message);
    }

    private final Deque<Envelope> queue = new ArrayDeque<>();
    private final Map<String, MessageHandler> handlers = new HashMap<>();

    /** Registers {@code handler} to receive messages addressed to {@code nodeId}. */
    public void register(String nodeId, MessageHandler handler) {
        handlers.put(Objects.requireNonNull(nodeId, "nodeId"), Objects.requireNonNull(handler, "handler"));
    }

    /** Enqueues {@code message} for delivery from {@code from} to {@code to}. */
    public void send(String from, String to, Message message) {
        queue.addLast(new Envelope(from, to, message));
    }

    /** Whether any envelope is still waiting to be delivered. */
    public boolean hasPending() {
        return !queue.isEmpty();
    }

    /** How many envelopes are still waiting to be delivered. */
    public int pendingCount() {
        return queue.size();
    }

    /**
     * Delivers the oldest pending envelope, in FIFO send order: dispatches
     * it to the recipient's registered handler, if any. Returns the
     * delivered envelope, or {@code null} if the queue was empty.
     */
    public Envelope deliverNext() {
        Envelope envelope = queue.pollFirst();
        if (envelope == null) {
            return null;
        }
        MessageHandler handler = handlers.get(envelope.to());
        if (handler != null) {
            handler.onMessage(envelope.from(), envelope.message());
        }
        return envelope;
    }

    /**
     * Delivers every pending envelope in FIFO order, including any new ones
     * that handlers enqueue while this call is draining the queue. Returns
     * the number of envelopes delivered.
     */
    public int deliverAll() {
        int delivered = 0;
        while (deliverNext() != null) {
            delivered++;
        }
        return delivered;
    }
}
