package com.professoros.model;

/** Expected business failure whose message is safe to display. */
public class DomainValidationException extends RuntimeException {
    public DomainValidationException(String message) { super(message); }
}
