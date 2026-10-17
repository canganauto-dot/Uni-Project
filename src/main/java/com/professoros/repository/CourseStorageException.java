package com.professoros.repository;

/** Keeps JDBC exceptions below the UI boundary while retaining the diagnostic cause. */
public final class CourseStorageException extends StorageException {
    public CourseStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
