package paxossim.events;

/**
 * One structured moment in a Paxos run, for visualization/replay — never
 * read back by the Paxos logic itself. Fields not meaningful for a given
 * {@code type} are {@code null}: a {@code CHOSEN} event has no
 * {@code fromNode}, a {@code SEND} has no {@code value}, and so on.
 *
 * <p>{@code type} is one of: SEND, RECEIVE, DROPPED (message-level events,
 * both nodes populated); PROMISE, NACK, ACCEPTED (an acceptor's reply,
 * carried in {@code toNode}); CHOSEN (a learner marking a slot decided);
 * STATE_CHANGE (a state machine applying a command, carried in
 * {@code toNode}); NOTE (a free-text narration of a moment worth
 * highlighting, e.g. "quorum reached").
 */
public record Event(int seq, String type, String fromNode, String toNode, String message,
                     String ballot, Integer slot, String value, String note) {
}
