package paxossim.events;

import paxossim.message.AcceptRequest;
import paxossim.message.Accepted;
import paxossim.message.Message;
import paxossim.message.Nack;
import paxossim.message.Prepare;
import paxossim.message.Promise;

/** Pulls the fields {@link EventLog} cares about out of any {@link Message}, for event recording. */
public final class MessageEvents {

    private MessageEvents() {}

    public static String typeName(Message message) {
        return switch (message) {
            case Prepare p -> "Prepare";
            case Promise p -> "Promise";
            case AcceptRequest r -> "AcceptRequest";
            case Accepted a -> "Accepted";
            case Nack n -> "Nack";
        };
    }

    public static String ballotOf(Message message) {
        return switch (message) {
            case Prepare p -> p.ballot().toString();
            case Promise p -> p.ballot().toString();
            case AcceptRequest r -> r.ballot().toString();
            case Accepted a -> a.ballot().toString();
            case Nack n -> n.ballot().toString();
        };
    }

    public static int slotOf(Message message) {
        return switch (message) {
            case Prepare p -> p.slot();
            case Promise p -> p.slot();
            case AcceptRequest r -> r.slot();
            case Accepted a -> a.slot();
            case Nack n -> n.slot();
        };
    }

    /** The value/accepted-value a message carries, or {@code null} if it doesn't carry one. */
    public static String valueOf(Message message) {
        return switch (message) {
            case AcceptRequest r -> String.valueOf(r.value());
            case Accepted a -> String.valueOf(a.value());
            case Promise p -> p.acceptedValue() == null ? null : p.acceptedValue().toString();
            case Prepare p -> null;
            case Nack n -> null;
        };
    }
}
