package paxossim.network;

import paxossim.message.Message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An in-process, single-threaded stand-in for the network: a FIFO queue of
 * {@link Envelope}s that a test (or later, a driver loop) drains explicitly
 * via {@link #deliverNext()} / {@link #deliverAll()}. There are no threads,
 * sockets, or timers, so a whole run — including message loss, reordering,
 * and partitions — is fully deterministic and replayable: every failure
 * mode is something a test asks for explicitly, never something that just
 * happens.
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
    private Set<String> isolatedNodes = Set.of();

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
     * it to the recipient's registered handler, if any, unless the current
     * {@link #partition} blocks it — in which case it's silently dropped,
     * same as a message to an unregistered recipient. Returns the delivered
     * envelope (dropped or not), or {@code null} if the queue was empty.
     */
    public Envelope deliverNext() {
        Envelope envelope = queue.pollFirst();
        if (envelope == null) {
            return null;
        }
        if (!isBlockedByPartition(envelope.from(), envelope.to())) {
            MessageHandler handler = handlers.get(envelope.to());
            if (handler != null) {
                handler.onMessage(envelope.from(), envelope.message());
            }
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

    /**
     * Discards the oldest pending envelope in FIFO order without delivering
     * it — a message lost in transit. Returns the dropped envelope, or
     * {@code null} if the queue was empty.
     */
    public Envelope dropNext() {
        return queue.pollFirst();
    }

    /**
     * Discards every pending envelope matching {@code predicate}, wherever
     * it sits in the queue, without delivering it — for injecting loss that
     * isn't just "the next message" (e.g. every message to one node).
     * Returns how many envelopes were dropped.
     */
    public int dropWhere(Predicate<Envelope> predicate) {
        int before = queue.size();
        queue.removeIf(predicate);
        return before - queue.size();
    }

    /**
     * Replaces the pending queue with {@code reordering} applied to its
     * current FIFO order, so a test can simulate messages arriving out of
     * send order. The transform is given the full pending list (oldest
     * first) and must return the list in whatever new delivery order it
     * wants; nothing is dropped or added.
     */
    public void reorderPending(UnaryOperator<List<Envelope>> reordering) {
        List<Envelope> current = new ArrayList<>(queue);
        List<Envelope> reordered = reordering.apply(current);
        queue.clear();
        queue.addAll(reordered);
    }

    /**
     * Partitions the network: nodes in {@code isolatedNodes} can still reach
     * each other, and nodes outside it can still reach each other, but any
     * message crossing that boundary is silently dropped on delivery until
     * {@link #healPartition()} is called. Replaces any previous partition.
     */
    public void partition(Set<String> isolatedNodes) {
        this.isolatedNodes = Set.copyOf(isolatedNodes);
    }

    /** Heals the current partition, if any: every node can reach every other node again. */
    public void healPartition() {
        this.isolatedNodes = Set.of();
    }

    private boolean isBlockedByPartition(String from, String to) {
        return isolatedNodes.contains(from) != isolatedNodes.contains(to);
    }
}
