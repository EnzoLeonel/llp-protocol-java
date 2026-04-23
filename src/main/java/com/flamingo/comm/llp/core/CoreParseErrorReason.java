package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.ParseErrorReason;

/**
 * Core-level parsing error reasons detected by the LLP parser.
 *
 * <p>
 * These errors represent structural inconsistencies in the frame
 * that are independent of any specific layer implementation.
 * </p>
 *
 * <p>
 * This enum complements plugin-defined errors by covering cases
 * detected by the core parser itself.
 * </p>
 */
public enum CoreParseErrorReason implements ParseErrorReason {

    /**
     * Metadata length exceeds available buffer.
     */
    MALFORMED_METADATA_LENGTH("Metadata length exceeds available data"),

    /**
     * Frame ended before expected fields could be read.
     */
    PAYLOAD_TOO_SHORT("Unexpected end of payload"),

    /**
     * Layer ID is invalid or out of range.
     */
    INVALID_LAYER_ID("Invalid layer identifier"),

    /**
     * A non-skippable layer could not be parsed.
     */
    NON_SKIPPABLE_LAYER_FAILED("Non-skippable layer parsing failed"),

    /**
     * A required layer parser was not found.
     */
    UNKNOWN_CRITICAL_LAYER("No parser found for non-skippable layer"),

    /**
     * A plugin threw an unexpected exception.
     */
    PLUGIN_EXCEPTION("Layer parser threw an exception");

    private final String reason;

    CoreParseErrorReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String reason() {
        return reason;
    }
}