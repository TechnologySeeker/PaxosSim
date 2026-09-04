package paxossim.core;

import java.util.Objects;

/**
 * A client command against the replicated key-value store: SET a key to a
 * value, or DELETE a key. Instances are immutable and compared by value so
 * two proposals for the same effect are interchangeable.
 */
public final class Command {

    public enum Type { SET, DELETE }

    private final Type type;
    private final String key;
    private final String value; // null for DELETE

    private Command(Type type, String key, String value) {
        this.type = type;
        this.key = Objects.requireNonNull(key, "key");
        this.value = value;
    }

    public static Command set(String key, String value) {
        Objects.requireNonNull(value, "value");
        return new Command(Type.SET, key, value);
    }

    public static Command delete(String key) {
        return new Command(Type.DELETE, key, null);
    }

    public Type type() {
        return type;
    }

    public String key() {
        return key;
    }

    /** The value to set, or null when {@link #type()} is DELETE. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Command other)) {
            return false;
        }
        return type == other.type && key.equals(other.key) && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, key, value);
    }

    @Override
    public String toString() {
        return switch (type) {
            case SET -> "SET " + key + " " + value;
            case DELETE -> "DELETE " + key;
        };
    }
}
