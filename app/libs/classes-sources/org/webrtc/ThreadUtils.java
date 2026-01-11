package org.webrtc;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/* loaded from: classes.jar:org/webrtc/ThreadUtils.class */
public class ThreadUtils {

    /* loaded from: classes.jar:org/webrtc/ThreadUtils$BlockingOperation.class */
    public interface BlockingOperation {
        void run() throws InterruptedException;
    }

    /* loaded from: classes.jar:org/webrtc/ThreadUtils$ThreadChecker.class */
    public static class ThreadChecker {

        @Nullable
        private Thread thread = Thread.currentThread();

        public void checkIsOnValidThread() {
            if (this.thread == null) {
                this.thread = Thread.currentThread();
            }
            if (Thread.currentThread() != this.thread) {
                throw new IllegalStateException("Wrong thread");
            }
        }

        public void detachThread() {
            this.thread = null;
        }
    }

    public static void checkIsOnMainThread() {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            throw new IllegalStateException("Not on main thread!");
        }
    }

    public static void executeUninterruptibly(BlockingOperation operation) {
        boolean wasInterrupted;
        boolean z = false;
        while (true) {
            try {
                wasInterrupted = z;
                operation.run();
                break;
            } catch (InterruptedException e) {
                z = true;
            }
        }
        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean joinUninterruptibly(Thread thread, long timeoutMs) throws InterruptedException {
        long startTimeMs = SystemClock.elapsedRealtime();
        long timeRemainingMs = timeoutMs;
        boolean wasInterrupted = false;
        while (timeRemainingMs > 0) {
            try {
                thread.join(timeRemainingMs);
                break;
            } catch (InterruptedException e) {
                wasInterrupted = true;
                long elapsedTimeMs = SystemClock.elapsedRealtime() - startTimeMs;
                timeRemainingMs = timeoutMs - elapsedTimeMs;
            }
        }
        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }
        return !thread.isAlive();
    }

    public static void joinUninterruptibly(final Thread thread) {
        executeUninterruptibly(new BlockingOperation() { // from class: org.webrtc.ThreadUtils.1
            @Override // org.webrtc.ThreadUtils.BlockingOperation
            public void run() throws InterruptedException {
                thread.join();
            }
        });
    }

    public static void awaitUninterruptibly(final CountDownLatch latch) {
        executeUninterruptibly(new BlockingOperation() { // from class: org.webrtc.ThreadUtils.2
            @Override // org.webrtc.ThreadUtils.BlockingOperation
            public void run() throws InterruptedException {
                latch.await();
            }
        });
    }

    public static boolean awaitUninterruptibly(CountDownLatch barrier, long timeoutMs) throws InterruptedException {
        long startTimeMs = SystemClock.elapsedRealtime();
        long timeRemainingMs = timeoutMs;
        boolean wasInterrupted = false;
        boolean result = false;
        do {
            try {
                result = barrier.await(timeRemainingMs, TimeUnit.MILLISECONDS);
                break;
            } catch (InterruptedException e) {
                wasInterrupted = true;
                long elapsedTimeMs = SystemClock.elapsedRealtime() - startTimeMs;
                timeRemainingMs = timeoutMs - elapsedTimeMs;
            }
        } while (timeRemainingMs > 0);
        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    public static <V> V invokeAtFrontUninterruptibly(Handler handler, final Callable<V> callable) {
        if (handler.getLooper().getThread() == Thread.currentThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        final C1Result result = new C1Result();
        final C1CaughtException caughtException = new C1CaughtException();
        final CountDownLatch barrier = new CountDownLatch(1);
        handler.post(new Runnable() { // from class: org.webrtc.ThreadUtils.3
            /* JADX WARN: Type inference failed for: r1v3, types: [V, java.lang.Object] */
            @Override // java.lang.Runnable
            public void run() {
                try {
                    result.value = callable.call();
                } catch (Exception e2) {
                    caughtException.f7e = e2;
                }
                barrier.countDown();
            }
        });
        awaitUninterruptibly(barrier);
        if (caughtException.f7e != null) {
            RuntimeException runtimeException = new RuntimeException(caughtException.f7e);
            runtimeException.setStackTrace(concatStackTraces(caughtException.f7e.getStackTrace(), runtimeException.getStackTrace()));
            throw runtimeException;
        }
        return result.value;
    }

    /* renamed from: org.webrtc.ThreadUtils$1CaughtException, reason: invalid class name */
    /* loaded from: classes.jar:org/webrtc/ThreadUtils$1CaughtException.class */
    class C1CaughtException {

        /* renamed from: e */
        Exception f7e;

        C1CaughtException() {
        }
    }

    /* renamed from: org.webrtc.ThreadUtils$1Result, reason: invalid class name */
    /* loaded from: classes.jar:org/webrtc/ThreadUtils$1Result.class */
    class C1Result {
        public V value;

        C1Result() {
        }
    }

    public static void invokeAtFrontUninterruptibly(Handler handler, final Runnable runner) {
        invokeAtFrontUninterruptibly(handler, new Callable<Void>() { // from class: org.webrtc.ThreadUtils.4
            /* JADX WARN: Can't rename method to resolve collision */
            @Override // java.util.concurrent.Callable
            public Void call() {
                runner.run();
                return null;
            }
        });
    }

    static StackTraceElement[] concatStackTraces(StackTraceElement[] inner, StackTraceElement[] outer) {
        StackTraceElement[] combined = new StackTraceElement[inner.length + outer.length];
        System.arraycopy(inner, 0, combined, 0, inner.length);
        System.arraycopy(outer, 0, combined, inner.length, outer.length);
        return combined;
    }
}
