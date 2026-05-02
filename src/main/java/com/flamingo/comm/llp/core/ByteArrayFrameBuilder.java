package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPLayerBuilder;
import com.flamingo.comm.llp.spi.LayerBuildPayload;
import com.flamingo.comm.llp.spi.LayerBuildResult;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Default {@link LLPFrameBuilder} implementation that produces a contiguous {@code byte[]} frame.
 *
 * <p>This builder applies a sequence of {@link LLPLayerBuilder} instances in order,
 * wrapping the provided payload into successive layers. Each layer contributes
 * metadata and may optionally transform the payload.</p>
 *
 * <h2>Build Strategy</h2>
 * <ul>
 *     <li>Layers are executed sequentially using the output payload of the previous layer.</li>
 *     <li>Headers (ID + metadata) are collected separately to avoid unnecessary copies.</li>
 *     <li>If a layer transforms the payload, previously accumulated headers are discarded,
 *     since they are assumed to be encapsulated within the transformed payload.</li>
 *     <li>The final byte array is assembled in a single pass.</li>
 * </ul>
 *
 * <h2>Frame Format</h2>
 * <pre>
 * [LAYER_ID][META_LENGTH][METADATA] ... [FINAL_ID=0x00][PAYLOAD]
 * </pre>
 *
 * <h2>Error Handling</h2>
 * <p>If any layer returns a {@link LayerBuildResult.Failure}, the build process is aborted
 * and a {@link FrameBuildException} is thrown.</p>
 *
 * <h2>Performance Notes</h2>
 * <ul>
 *     <li>Avoids intermediate payload concatenation.</li>
 *     <li>Performs a single allocation for the final byte array.</li>
 *     <li>Uses {@link ByteBuffer#duplicate()} to prevent mutation of input buffers.</li>
 * </ul>
 */
public class ByteArrayFrameBuilder implements LLPFrameBuilder<byte[]> {

    private final List<LLPLayerBuilder> layers;

    /**
     * Creates a new builder with the given ordered list of layers.
     *
     * @param layers the layers to apply during the build process
     */
    ByteArrayFrameBuilder(List<LLPLayerBuilder> layers) {
        this.layers = List.copyOf(layers);
    }

    /**
     * Builds the final LLP frame as a {@code byte[]}.
     *
     * @param payload the initial payload to be wrapped by the configured layers
     * @return the fully assembled frame as a contiguous byte array
     * @throws IllegalArgumentException if {@code payload} is {@code null}
     * @throws FrameBuildException      if any layer fails during the build process
     */
    @Override
    public byte[] build(ByteBuffer payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload cannot be null");
        }

        ByteBuffer currentPayload = payload;

        // Stack of headers (outermost first). Payload is handled separately.
        Deque<LayerHeader> headersStack = new ArrayDeque<>();

        for (LLPLayerBuilder layer : layers) {
            LayerBuildResult result = layer.build(new DefaultLayerBuildPayload(currentPayload.asReadOnlyBuffer()));

            switch (result) {
                case LayerBuildResult.Failure failure -> throw new FrameBuildException(layer.getLayerId(), failure.errorReason());

                case LayerBuildResult.Success success -> {
                    switch (success) {
                        case LayerBuildResult.Success.UnmodifiedPayload unmodified ->
                                headersStack.addFirst(new LayerHeader(layer.getLayerId(), unmodified.metadata()));

                        case LayerBuildResult.Success.TransformedPayload modified -> {
                            // The payload has been transformed (e.g., encryption/compression).
                            // Previous headers are assumed to be encapsulated in the new payload.
                            currentPayload = modified.modifiedPayload();
                            headersStack.clear();

                            headersStack.addFirst(new LayerHeader(layer.getLayerId(), modified.metadata()));
                        }
                    }
                }
            }
        }

        return assembleFinalArray(headersStack, currentPayload);
    }

    /**
     * Assembles the final frame into a single byte array.
     *
     * @param headers      the ordered headers (outermost first)
     * @param finalPayload the final payload to append
     * @return the serialized frame
     */
    private byte[] assembleFinalArray(Deque<LayerHeader> headers, ByteBuffer finalPayload) {
        int totalSize = 0;

        for (LayerHeader header : headers) {
            totalSize += header.size();
        }

        totalSize += 1; // Final layer ID (0x00)
        totalSize += finalPayload.remaining();

        byte[] result = new byte[totalSize];
        int offset = 0;

        for (LayerHeader header : headers) {
            offset = writeHeader(result, offset, header);
        }

        // Final layer marker
        result[offset++] = 0x00;

        // Payload (read-only copy)
        finalPayload.duplicate().get(result, offset, finalPayload.remaining());

        return result;
    }

    /**
     * Writes a single layer header into the destination array.
     *
     * @param dest   destination array
     * @param offset current write offset
     * @param header header to write
     * @return updated offset after writing
     */
    private int writeHeader(byte[] dest, int offset, LayerHeader header) {
        dest[offset++] = (byte) header.id();

        int metaLen = header.metadata().remaining();

        if (metaLen < 255) {
            dest[offset++] = (byte) metaLen;
        } else {
            dest[offset++] = (byte) 0xFF;
            dest[offset++] = (byte) ((metaLen >> 8) & 0xFF);
            dest[offset++] = (byte) (metaLen & 0xFF);
        }

        if (metaLen > 0) {
            header.metadata().duplicate().get(dest, offset, metaLen);
            offset += metaLen;
        }

        return offset;
    }

    /**
     * Lightweight structure representing a layer header (ID + metadata).
     */
    private record LayerHeader(int id, ByteBuffer metadata) {

        /**
         * Returns the total serialized size of this header.
         */
        int size() {
            int metaLen = metadata.remaining();
            return 1 + (metaLen < 255 ? 1 : 3) + metaLen;
        }
    }

    /**
     * Default implementation of {@link LayerBuildPayload}.
     * Wraps the current payload passed between layers.
     */
    private record DefaultLayerBuildPayload(ByteBuffer payload) implements LayerBuildPayload {
    }
}