package paxossim.testing;

import java.util.Objects;

/**
 * Tiny assertion helpers so tests don't need a JUnit dependency — the
 * project has no build tool yet, so tests run with plain javac/java.
 */
public final class Assertions {

    private Assertions() {}

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }
}
