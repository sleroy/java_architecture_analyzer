package test_samples.threadlocal;

import java.text.SimpleDateFormat;
import java.util.Random;

/**
 * Example class demonstrating ThreadLocal usage patterns.
 * This class contains various ThreadLocal fields for testing the
 * ThreadLocalUsageInspector.
 */
public class ThreadLocalExample {

    // Simple ThreadLocal with explicit type
    private static final ThreadLocal<String> userContext = new ThreadLocal<>();

    // ThreadLocal with date formatter (common use case)
    private static final ThreadLocal<SimpleDateFormat> dateFormatter = ThreadLocal
            .withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

    // ThreadLocal with primitive wrapper
    private static final ThreadLocal<Integer> requestId = new ThreadLocal<>();

    // ThreadLocal with custom type
    private static final ThreadLocal<Random> randomGenerator = ThreadLocal.withInitial(Random::new);

    // InheritableThreadLocal (subclass of ThreadLocal)
    private static final InheritableThreadLocal<Long> transactionId = new InheritableThreadLocal<>();

    // Instance ThreadLocal (not just static)
    private final ThreadLocal<String> instanceContext = new ThreadLocal<>();

    public void setUserContext(String user) {
        userContext.set(user);
    }

    public String getUserContext() {
        return userContext.get();
    }

    public SimpleDateFormat getDateFormatter() {
        return dateFormatter.get();
    }

    public void setRequestId(int id) {
        requestId.set(id);
    }

    public Integer getRequestId() {
        return requestId.get();
    }

    public Random getRandomGenerator() {
        return randomGenerator.get();
    }

    public void setTransactionId(long id) {
        transactionId.set(id);
    }

    public Long getTransactionId() {
        return transactionId.get();
    }

    public void cleanup() {
        userContext.remove();
        requestId.remove();
        transactionId.remove();
        instanceContext.remove();
    }
}
