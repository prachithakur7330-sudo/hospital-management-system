package com.hms.service;

/** Thrown when a booking request breaks one of the scheduling rules. Message is shown to the user as-is. */
public class BookingException extends RuntimeException {
    public BookingException(String message) {
        super(message);
    }
}
