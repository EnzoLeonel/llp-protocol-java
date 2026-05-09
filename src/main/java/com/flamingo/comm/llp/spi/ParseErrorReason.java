package com.flamingo.comm.llp.spi;

/**
 * Marker interface for all layer parsing errors.
 * Plugins should implement this interface using their own enums.
 */
public interface ParseErrorReason {
    /**
     * Returns a human-readable default message for the error.
     */
    String reason();
}