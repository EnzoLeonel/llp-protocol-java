package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.BuildErrorReason;

import java.util.Optional;

/**
 * Exception thrown when an error occurs during the LLP frame building process.
 *
 * <p>This exception indicates that one or more layers failed to build correctly,
 * or that the overall frame construction process could not be completed.</p>
 *
 * <h2>Typical Causes</h2>
 * <ul>
 *     <li>A {@link com.flamingo.comm.llp.spi.LLPLayerBuilder} returned a failure result.</li>
 *     <li>A layer produced invalid metadata or payload.</li>
 *     <li>An unexpected exception occurred within a layer implementation.</li>
 *     <li>The configured layer chain is inconsistent or invalid.</li>
 * </ul>
 *
 * <h2>Additional Context</h2>
 * <ul>
 *     <li>{@code layerId} identifies the layer where the failure occurred.</li>
 *     <li>{@code errorReason} provides a structured reason when available.</li>
 * </ul>
 *
 * <h2>Usage Notes</h2>
 * <ul>
 *     <li>This is an unchecked exception ({@link RuntimeException}) as build
 *     failures are typically unrecoverable within the same flow.</li>
 *     <li>Callers may catch this exception to log or handle failures at a higher level.</li>
 * </ul>
 */
public class FrameBuildException extends RuntimeException {

    private final int layerId;
    private final BuildErrorReason errorReason;

    /**
     * Creates a new {@code FrameBuildException} with a message only.
     * Layer information will be unavailable.
     *
     * @param message a human-readable description of the error
     */
    public FrameBuildException(String message) {
        super(message);
        this.layerId = -1;
        this.errorReason = null;
    }

    /**
     * Creates a new {@code FrameBuildException} with a message and cause.
     *
     * @param message a human-readable description of the error
     * @param cause   the underlying cause of the failure
     */
    public FrameBuildException(String message, Throwable cause) {
        super(message, cause);
        this.layerId = -1;
        this.errorReason = null;
    }

    /**
     * Creates a new {@code FrameBuildException} with full layer context.
     *
     * @param layerId     the ID of the layer where the error occurred
     * @param errorReason the structured error reason
     */
    public FrameBuildException(int layerId, BuildErrorReason errorReason) {
        super(buildMessage(layerId, errorReason));
        this.layerId = layerId;
        this.errorReason = errorReason;
    }

    /**
     * Creates a new {@code FrameBuildException} with full context and cause.
     *
     * @param layerId     the ID of the layer where the error occurred
     * @param errorReason the structured error reason
     * @param cause       the underlying cause
     */
    public FrameBuildException(int layerId, BuildErrorReason errorReason, Throwable cause) {
        super(buildMessage(layerId, errorReason), cause);
        this.layerId = layerId;
        this.errorReason = errorReason;
    }

    private static String buildMessage(int layerId, BuildErrorReason reason) {
        return "Layer [" + layerId + "] failed to build. Reason: " + reason;
    }

    /**
     * Returns the layer ID where the failure occurred.
     *
     * @return the layer ID, or {@code -1} if not available
     */
    public int getLayerId() {
        return layerId;
    }

    /**
     * Returns the structured error reason, if available.
     *
     * @return an {@link Optional} containing the error reason
     */
    public Optional<BuildErrorReason> getErrorReason() {
        return Optional.ofNullable(errorReason);
    }
}