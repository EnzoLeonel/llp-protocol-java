package com.flamingo.comm.llp.spi;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Represents the comprehensive result of a layer build process.
 */
public sealed interface LayerBuildResult permits LayerBuildResult.Success, LayerBuildResult.Failure {

    /**
     * Represents a successful layer build.
     * Can either leave the payload untouched or modify it.
     */
    sealed interface Success extends LayerBuildResult permits LayerBuildResult.Success.UnmodifiedPayload, LayerBuildResult.Success.TransformedPayload {

        /**
         * Used when the layer only appends metadata and leaves the payload untouched.
         */
        record UnmodifiedPayload(ByteBuffer metadata) implements Success {
            public UnmodifiedPayload {
                Objects.requireNonNull(metadata, "metadata cannot be null");
            }
        }

        /**
         * Used when the layer actively mutates the payload (e.g., encryption).
         */
        record TransformedPayload(ByteBuffer metadata, ByteBuffer modifiedPayload) implements Success {
            public TransformedPayload {
                Objects.requireNonNull(metadata, "metadata cannot be null");
                Objects.requireNonNull(modifiedPayload, "modifiedPayload cannot be null");
            }
        }
    }

    /**
     * Failed building result.
     *
     * @param errorReason reason for failure (never {@code null})
     */
    record Failure(BuildErrorReason errorReason) implements LayerBuildResult {
        public Failure {
            Objects.requireNonNull(errorReason, "errorReason cannot be null");
        }
    }
}