package lt.satsyuk.distributed.audit.auditwriter.testutil;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class TestWaitUtils {

    private TestWaitUtils() {
    }

    public static void pauseWithoutThreadSleep(long millis) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        long maxParkNanos = TimeUnit.MILLISECONDS.toNanos(1);
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Test wait was interrupted");
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                return;
            }

            LockSupport.parkNanos(Math.min(remainingNanos, maxParkNanos));
        }
    }
}

