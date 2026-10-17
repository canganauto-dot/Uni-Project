package com.professoros.controller;

import com.professoros.model.DomainValidationException;
import com.professoros.repository.StorageException;
import javafx.concurrent.Task;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.*;

/** JavaFX/background boundary shared by academic controllers. Call from the UI thread. */
final class UiTaskRunner implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("academic-worker").factory());
    <T> void run(Callable<T> work, Consumer<T> success, Consumer<String> failure) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable cause = task.getException();
            if (cause instanceof DomainValidationException) { failure.accept(cause.getMessage()); }
            else {
                Logger.getLogger(UiTaskRunner.class.getName()).log(Level.SEVERE, "Academic operation failed", cause);
                failure.accept(cause instanceof StorageException ? cause.getMessage() : "The operation failed. See the launch log for details.");
            }
        });
        executor.execute(task);
    }
    @Override public void close() { executor.shutdown(); }
}
