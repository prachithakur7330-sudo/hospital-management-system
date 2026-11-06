package com.hms.dao;

/** Unchecked wrapper so callers above the DAO layer don't have to deal with SQLException. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
