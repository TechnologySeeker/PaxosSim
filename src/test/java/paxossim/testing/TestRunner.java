package paxossim.testing;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal reflection-based test runner: invokes every public, no-arg, void
 * method named test* on the given classes and reports pass/fail. Exists so
 * the project can have tests before any build tool (Maven/Gradle) is set up.
 */
public final class TestRunner {

    private TestRunner() {}

    public static void run(Class<?>... testClasses) {
        int passed = 0;
        List<String> failures = new ArrayList<>();

        for (Class<?> testClass : testClasses) {
            Object instance = newInstance(testClass);
            for (Method method : testMethods(testClass)) {
                String label = testClass.getSimpleName() + "." + method.getName();
                try {
                    method.invoke(instance);
                    System.out.println("PASS  " + label);
                    passed++;
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    System.out.println("FAIL  " + label + " - " + cause.getMessage());
                    failures.add(label);
                }
            }
        }

        int total = passed + failures.size();
        System.out.println();
        System.out.println(passed + "/" + total + " tests passed");

        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }

    private static Object newInstance(Class<?> testClass) {
        try {
            return testClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("cannot instantiate " + testClass, e);
        }
    }

    private static List<Method> testMethods(Class<?> testClass) {
        List<Method> methods = new ArrayList<>();
        for (Method method : testClass.getMethods()) {
            if (method.getName().startsWith("test")
                    && method.getParameterCount() == 0
                    && method.getReturnType() == void.class) {
                methods.add(method);
            }
        }
        methods.sort((a, b) -> a.getName().compareTo(b.getName()));
        return methods;
    }
}
