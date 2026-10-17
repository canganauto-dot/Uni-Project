package com.professoros.repository;

/** Unexpected persistence failure; retain the cause for logging. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) { super(message, cause); }
}
