package io.shty.shortener;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class TestUtils {
    static <T> T runBlocking(CompletionStage<T> stringCompletionStage) {
        try {
            return stringCompletionStage.toCompletableFuture().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new RuntimeException(cause);
            }
        }
    }

    static <T extends Throwable> void assertThrows(Class<T> throwable, Callable callable) {
        try {
            callable.call();
            throw new RuntimeException("No throwable was not thrown");
        } catch (Throwable actualThrowable) {
            if (throwable.isAssignableFrom(actualThrowable.getClass())) {
                return;
            }
            throw new RuntimeException(actualThrowable.getClass() + " was thrown instead expected " + throwable);
        }
    }
}
